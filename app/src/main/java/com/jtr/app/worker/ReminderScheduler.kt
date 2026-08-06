package com.jtr.app.worker

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jtr.app.JTRApplication
import com.jtr.app.MainActivity
import com.jtr.app.R
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.ui.person.storedDateToMillis
import com.jtr.app.utils.DateCanonical
import com.jtr.app.utils.JtrNotificationManager
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * ReminderScheduler — planification PRÉCISE (à la minute) des rappels de dates
 * importantes via [AlarmManager] (v7.0).
 *
 * Chaque date notifiable (anniversaire + dates importantes marquées « notifier ») est
 * ancrée à **minuit (00:00) du jour J** de sa prochaine occurrence ; le rappel se
 * déclenche à **(minuit du jour J − délai)**, où le délai vient de
 * [DynamicLine.reminderOffsetMinutes] (ou, pour le repli scalaire legacy,
 * `Person.birthdateReminderOffsetMinutes`). Délai `0` ⇒ déclenchement à minuit.
 *
 * Un Worker périodique ne garantit pas la minute (report Doze) : on programme donc une
 * **alarme exacte** ([AlarmManager.setExactAndAllowWhileIdle]) par occurrence à venir.
 * À chaque déclenchement (cf. [ReminderAlarmReceiver]), au démarrage de l'app, à la
 * sauvegarde d'un contact et au reboot ([BootReceiver]), on appelle [rescheduleAll] :
 * il annule les alarmes obsolètes, poste les rappels dont la fenêtre vient de s'ouvrir
 * (rattrapage idempotent), puis arme la prochaine occurrence de chaque date.
 *
 * Permission d'alarme exacte (Android 12+) : déclarée au manifeste (USE_EXACT_ALARM,
 * adaptée à une app de rappels ; SCHEDULE_EXACT_ALARM ≤ API 32). Si elle est malgré
 * tout indisponible ([AlarmManager.canScheduleExactAlarms] == false), on se replie sur
 * une alarme INEXACTE plutôt que de planter.
 */
object ReminderScheduler {

    private const val PREFS = "jtr_prefs"

    /** Clés des alarmes actuellement armées ("personId|lineId") — pour les annuler. */
    private const val KEY_ACTIVE = "reminder_active_keys"

    /** Dernière occurrence (minuit du jour J, en ms) effectivement notifiée par clé. */
    private const val LAST_PREFIX = "reminder_last_"

    const val ACTION_FIRE = "com.jtr.app.action.REMINDER_FIRE"

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Plafond d'alarmes EXACTES armées simultanément (v7.1.57).
     *
     * Android 13+ (API 33) limite une application à **500** alarmes exactes ; au-delà,
     * `setExactAndAllowWhileIdle` lève `IllegalStateException`. 400 laisse une marge
     * confortable sous ce seuil tout en couvrant très largement l'usage réel (une base de
     * 116 contacts en arme quelques centaines au plus). Les dates au-delà du plafond ne sont
     * pas perdues : elles sont réévaluées à chaque recalcul et armées dès que les échéances
     * plus proches se sont déclenchées.
     */
    internal const val MAX_EXACT_ALARMS = 400

    /**
     * Sélection des alarmes à armer : les [MAX_EXACT_ALARMS] échéances les PLUS PROCHES
     * (v7.1.57). Extraite en fonction PURE pour être testable sans `AlarmManager`.
     *
     * `sortedBy` est STABLE → à instants d'armement égaux, l'ordre de collecte est conservé,
     * donc deux recalculs successifs sur les mêmes données donnent la même sélection.
     */
    internal fun capByProximity(candidates: List<Pair<Long, String>>): List<Pair<Long, String>> =
        candidates.sortedBy { (armAt, _) -> armAt }.take(MAX_EXACT_ALARMS)

    /** Date notifiable résolue, prête à être planifiée / postée. */
    private data class Event(
        val personId: String,
        val firstName: String,
        val lineId: String,
        val label: String,
        val isBirthday: Boolean,     // v7.1.29 : sert au LIBELLÉ de la notification
        val dateMillis: Long,        // date stockée (avec son année) OU prochaine occurrence (year-less)
        val offsetMinutes: Int,
        // v7.1.37 (B3b) : date SANS année (`--MM-dd`) → ANNUELLE par nature ; force `recurring`
        // dans rescheduleAll SANS toucher la comparaison passé/futur des dates datées.
        val annualOnly: Boolean = false
    ) {
        val key get() = "$personId|$lineId"
    }

    /**
     * Résolution PURE d'un [Event] (v7.1.57) — sépare le CALCUL des dates de l'ACTION.
     *
     * Le calcul lui-même est strictement celui d'avant ; on l'a seulement extrait de la boucle
     * pour pouvoir TRIER les armements par proximité avant d'en plafonner le nombre
     * ([MAX_EXACT_ALARMS]). Aucune règle de récurrence n'est touchée.
     */
    private data class Resolved(
        val event: Event,
        /** Occurrence dont la fenêtre est DÉJÀ ouverte (à rattraper), sinon null. */
        val catchUp: Long?,
        /** Instant auquel armer l'alarme exacte, sinon null (plus rien à armer). */
        val armAt: Long?
    )

    /**
     * Recalcule TOUTES les alarmes de rappel. Idempotent : appelable à volonté
     * (démarrage, sauvegarde, déclenchement d'alarme, reboot) sans doublon de
     * notification ni d'alarme.
     */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1. Annule toutes les alarmes précédemment armées (gère la suppression d'une
        //    date ou la désactivation de sa cloche).
        val previous = prefs.getStringSet(KEY_ACTIVE, emptySet())?.toSet().orEmpty()
        previous.forEach { key -> am.cancel(firePendingIntent(app, key)) }

        // Défauts ALIGNÉS sur SettingsViewModel (true) → le toggle reflète la réalité.
        val enabled = prefs.getBoolean("notifications_enabled", true) &&
            prefs.getBoolean("birthday_enabled", true)

        // Notifications coupées ou permission absente (API 33+) : tout est annulé, rien
        // n'est planifié. La prochaine ouverture de l'app réarmera une fois rétabli.
        if (!enabled || !hasNotificationPermission(app)) {
            prefs.edit().putStringSet(KEY_ACTIVE, emptySet()).apply()
            return
        }

        val now = System.currentTimeMillis()
        val today0 = startOfDay(now)
        val events = collectEvents(app)
        val active = HashSet<String>()

        // 2. RÉSOLUTION (v7.1.57) — calcul PUR, sans effet de bord, identique à l'existant.
        val resolved = events.map { ev ->
            // v7.1.29 — critère PASSÉ/FUTUR (indépendant du type) : une date dont le jour est
            // déjà RÉVOLU refête chaque année (prochaine occurrence jour/mois) ; une date À VENIR
            // est un rappel UNIQUE sur sa vraie date (année incluse). `isBirthday` ne sert plus
            // qu'au libellé de la notification. Une date future, une fois passée, redevient
            // annuelle au prochain recalcul (comportement accepté). Aucune date n'est ignorée.
            // v7.1.37 (B3b) — une date SANS année est TOUJOURS annuelle (branche séparée) ; la
            // comparaison passé/futur des dates DATÉES (v7.1.29) reste strictement inchangée.
            val recurring = ev.annualOnly || startOfDay(ev.dateMillis) < today0
            val occurrence = if (recurring) nextEventMidnight(ev.dateMillis, now)
                             else startOfDay(ev.dateMillis)
            val trigger = occurrence - ev.offsetMinutes * 60_000L
            if (trigger > now) {
                // Fenêtre à venir → alarme exacte (recalculée depuis Room chaque jour, même à +ans).
                Resolved(ev, catchUp = null, armAt = trigger)
            } else {
                // Fenêtre déjà ouverte (ajout tardif, appareil éteint au moment prévu…) → rattrapage.
                // Date passée (annuelle) : on arme l'occurrence suivante. Date future unique : terminé.
                val next = if (recurring)
                    nextEventMidnight(ev.dateMillis, occurrence + DAY_MS) - ev.offsetMinutes * 60_000L
                else null
                Resolved(ev, catchUp = occurrence, armAt = next)
            }
        }

        // 3. RATTRAPAGE — inchangé, dans l'ordre de collecte, et JAMAIS plafonné : une
        //    notification déjà due est postée immédiatement, elle ne consomme aucun quota
        //    d'alarme. Une seule fois par occurrence.
        //    Chaque rattrapage est ISOLÉ par un runCatching (v7.1.57), en parité avec
        //    l'armement : une notification qui lève (ou une écriture de prefs qui échoue) ne
        //    doit pas plus faire tomber le recalcul global qu'une alarme refusée. Objectif
        //    complet : `rescheduleAll` ne plante JAMAIS — ni au démarrage, ni au boot.
        resolved.forEach { r ->
            val occurrence = r.catchUp ?: return@forEach
            val lastKey = LAST_PREFIX + r.event.key
            runCatching {
                if (prefs.getLong(lastKey, -1L) != occurrence && postReminder(app, r.event)) {
                    prefs.edit().putLong(lastKey, occurrence).apply()
                }
            }
        }

        // 4. ARMEMENT — PLAFONNÉ PAR PROXIMITÉ (v7.1.57). Android 13+ limite une app à 500
        //    alarmes exactes ; au-delà, setExactAndAllowWhileIdle lève IllegalStateException.
        //    Une alarme par date notifiable, sans borne, franchissait ce seuil dès ~250 contacts
        //    portant 2 dates — et l'exception remontait jusqu'à rescheduleAll, appelée au
        //    DÉMARRAGE, à chaque sauvegarde et au boot : panne dure.
        //    On trie par instant d'armement CROISSANT et on n'arme que les [MAX_EXACT_ALARMS]
        //    premières : les échéances les plus proches — les seules qui comptent aujourd'hui —
        //    sont toujours servies. Les suivantes seront armées d'elles-mêmes au prochain
        //    recalcul (quotidien via ImportantDateCheckWorker, et à chaque ouverture de l'app),
        //    au fur et à mesure que les alarmes armées se déclenchent et libèrent la place.
        //    La sélection elle-même vit dans [capByProximity] (fonction pure, testée).
        capByProximity(resolved.mapNotNull { r -> r.armAt?.let { it to r.event.key } })
            .forEach { (armAt, key) ->
                scheduleExact(am, app, key, armAt)
                active += key
            }
        prefs.edit().putStringSet(KEY_ACTIVE, active).apply()
    }

    // ── Construction des évènements ───────────────────────────────────────────

    private suspend fun collectEvents(context: Context): List<Event> {
        val persons = PersonRepository(context).getAllActive().first()
        val out = ArrayList<Event>()
        persons.forEach { p ->
            // dateLines JSON en priorité ; repli sur le scalaire birthdate (legacy), en ISO.
            val lines: List<DynamicLine> = p.dateLines?.takeIf { it.isNotEmpty() }
                ?: p.birthdate?.let {
                    listOf(
                        DynamicLine(
                            value = DateCanonical.millisToIso(it),
                            label = FieldTypes.DATE_BIRTHDAY,
                            notify = p.birthdateNotify,
                            reminderOffsetMinutes = p.birthdateReminderOffsetMinutes
                        )
                    )
                }.orEmpty()
            lines.forEach { line ->
                if (!line.notify) return@forEach
                // v7.1.37 (B3b) — date SANS année (`--MM-dd`) : NE PAS passer par storedDateToMillis
                // (qui rendrait null → date perdue) ; on calcule la prochaine occurrence (jour/mois)
                // et on marque l'event annuel. Sinon : interprétation LOCALE-LIBRE de l'ISO stocké.
                val yearLess = DateCanonical.isMonthDay(line.value)
                val millis = if (yearLess)
                    DateCanonical.nextOccurrenceMillis(line.value, System.currentTimeMillis())
                else
                    storedDateToMillis(line.value)
                if (millis == null) return@forEach
                out += Event(
                    personId = p.id,
                    firstName = p.firstName,
                    lineId = line.id,
                    label = line.label,
                    isBirthday = line.label == FieldTypes.DATE_BIRTHDAY,
                    dateMillis = millis,
                    offsetMinutes = line.reminderOffsetMinutes.coerceAtLeast(0),
                    annualOnly = yearLess
                )
            }
        }
        return out
    }

    // ── Calcul d'occurrence (récurrence annuelle) ─────────────────────────────

    /**
     * Minuit (00:00 local) de la prochaine occurrence de la date [dateMillis] dont le
     * jour n'est pas encore passé par rapport à [fromMillis] : l'occurrence de l'année
     * courante si son jour ≥ aujourd'hui, sinon celle de l'année suivante.
     */
    private fun nextEventMidnight(dateMillis: Long, fromMillis: Long): Long {
        val src = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val day = src.get(Calendar.DAY_OF_MONTH)
        val month = src.get(Calendar.MONTH)
        val today0 = startOfDay(fromMillis)
        val year = Calendar.getInstance().apply { timeInMillis = fromMillis }.get(Calendar.YEAR)
        var candidate = occurrenceMidnight(day, month, year)
        if (candidate < today0) candidate = occurrenceMidnight(day, month, year + 1)
        return candidate
    }

    /** Minuit local du [day]/[month]/[year], avec garde 29/02 → dernier jour du mois. */
    private fun occurrenceMidnight(day: Int, month: Int, year: Int): Long {
        val cal = Calendar.getInstance().apply {
            clear() // remet l'heure à 00:00:00.000
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(maxDay))
        return cal.timeInMillis
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ── Programmation AlarmManager ────────────────────────────────────────────

    private fun scheduleExact(am: AlarmManager, context: Context, key: String, triggerAt: Long) {
        val pi = firePendingIntent(context, key)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Permission d'alarme exacte révoquée : repli INEXACT (jamais de crash).
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: Exception) {
            // v7.1.57 — FILET ÉLARGI. On n'attrapait que SecurityException (permission d'alarme
            // exacte révoquée) ; le quota de 500 alarmes exactes d'Android 13+ lève, lui, une
            // IllegalStateException qui remontait jusqu'à rescheduleAll et faisait tomber le
            // démarrage. L'armement d'UNE date ne doit JAMAIS faire échouer le recalcul GLOBAL :
            // toute défaillance retombe sur une alarme INEXACTE (non soumise au quota), et si
            // même ce repli échoue, la date est simplement sautée — le prochain recalcul
            // (quotidien / à l'ouverture) retentera.
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
        }
    }

    /** PendingIntent unique par clé (data distincte) → annulable individuellement. */
    private fun firePendingIntent(context: Context, key: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("jtrreminder://$key")
        }
        return PendingIntent.getBroadcast(
            context, key.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ── Livraison de la notification (canal HIGH, v6.2.7) ─────────────────────

    /** Construit et poste la notification heads-up. Renvoie true si le post a réussi. */
    private fun postReminder(context: Context, ev: Event): Boolean {
        if (!hasNotificationPermission(context)) return false

        val title = if (ev.isBirthday)
            context.getString(R.string.notif_birthday_title, ev.firstName)
        else
            context.getString(R.string.notif_importantdate_title)
        val body = if (ev.isBirthday)
            context.getString(R.string.notif_birthday_body)
        else
            context.getString(
                R.string.notif_importantdate_text, labelFor(context, ev.label), ev.firstName
            )

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(JtrNotificationManager.EXTRA_PERSON_ID, ev.personId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, ev.key.hashCode(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, JTRApplication.CHANNEL_BIRTHDAY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // heads-up sous Android 8 (pré-canaux)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()

        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(ev.key.hashCode(), notification)
        }.isSuccess
    }

    /** Résout le libellé d'une date : type connu localisé, sinon texte personnalisé. */
    private fun labelFor(context: Context, label: String): String = when (label) {
        FieldTypes.DATE_BIRTHDAY -> context.getString(R.string.date_type_birthday)
        "anniversary"            -> context.getString(R.string.date_type_anniversary)
        "other", ""              -> context.getString(R.string.type_other)
        else                     -> label // libellé personnalisé saisi par l'utilisateur
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}
