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

        events.forEach { ev ->
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
                scheduleExact(am, app, ev.key, trigger)
                active += ev.key
            } else {
                // Fenêtre déjà ouverte (ajout tardif, appareil éteint au moment prévu…) :
                // rattrapage immédiat, une seule fois par occurrence.
                val lastKey = LAST_PREFIX + ev.key
                if (prefs.getLong(lastKey, -1L) != occurrence && postReminder(app, ev)) {
                    prefs.edit().putLong(lastKey, occurrence).apply()
                }
                // Date passée (annuelle) : on arme l'occurrence suivante. Date future unique : terminé.
                if (recurring) {
                    val nextOccurrence = nextEventMidnight(ev.dateMillis, occurrence + DAY_MS)
                    scheduleExact(am, app, ev.key, nextOccurrence - ev.offsetMinutes * 60_000L)
                    active += ev.key
                }
            }
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
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
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
