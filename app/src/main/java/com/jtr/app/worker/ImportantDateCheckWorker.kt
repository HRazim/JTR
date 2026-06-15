package com.jtr.app.worker

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jtr.app.JTRApplication
import com.jtr.app.MainActivity
import com.jtr.app.R
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.ui.person.millisToRawDigits
import com.jtr.app.ui.person.rawDigitsToMillis
import com.jtr.app.ui.person.resolveDateFormatSpec
import com.jtr.app.utils.JtrNotificationManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/**
 * ImportantDateCheckWorker — Balaie TOUTES les dates importantes (anniversaires +
 * dates personnalisées marquées « notifier ») et poste une notification VISIBLE
 * (heads-up) pour celles qui tombent AUJOURD'HUI.
 *
 * v6.2.7 — fiabilisation de bout en bout : la logique de balayage/post est exposée en
 * [checkAndNotify] (suspend), appelable DIRECTEMENT après la sauvegarde d'un contact
 * (déclenchement immédiat, non différé par Doze/WorkManager, sans course avec la base),
 * et via [runNow] pour l'ouverture de l'app / le balayage de secours, en plus du
 * balayage périodique quotidien.
 */
class ImportantDateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        checkAndNotify(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NOW = "important_date_check_now"

        /** Balayage immédiat via WorkManager (ouverture de l'app, balayage de secours). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ImportantDateCheckWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request
            )
        }

        /**
         * Balaie les dates dues aujourd'hui et poste les notifications. `suspend` →
         * appelable DIRECTEMENT (sans WorkManager) juste après l'enregistrement d'un
         * contact : la base est déjà à jour (aucune course) et le post est immédiat
         * (aucun report Doze). Idempotent : anti-doublon par (contact + date) et par jour,
         * marqué UNIQUEMENT après un post réussi.
         */
        suspend fun checkAndNotify(context: Context) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
            // Défauts ALIGNÉS sur SettingsViewModel (true) → le toggle reflète la réalité.
            if (!prefs.getBoolean("notifications_enabled", true) ||
                !prefs.getBoolean("birthday_enabled", true)) return
            // Permission absente (API 33+) : on s'arrête AVANT toute marque anti-doublon,
            // pour ne jamais bloquer une future tentative une fois la permission accordée.
            if (!hasNotificationPermission(appContext)) return

            val spec = resolveDateFormatSpec(Locale.getDefault())
            val today = Calendar.getInstance()
            val todayDay = today.get(Calendar.DAY_OF_MONTH)
            val todayMonth = today.get(Calendar.MONTH)
            val todayEpochDay = LocalDate.now().toEpochDay()

            val persons = PersonRepository(appContext).getAllActive().first()
            persons.forEach { person ->
                // dateLines JSON en priorité ; repli sur le scalaire birthdate (legacy).
                val dates: List<DueDate> = person.dateLines?.takeIf { it.isNotEmpty() }
                    ?.map { DueDate(it.value, it.label, it.notify, it.id) }
                    ?: person.birthdate?.let {
                        listOf(
                            DueDate(
                                value = millisToRawDigits(it, spec.order),
                                label = FieldTypes.DATE_BIRTHDAY,
                                notify = person.birthdateNotify,
                                id = "birthdate"
                            )
                        )
                    }.orEmpty()

                dates.forEach { due ->
                    if (!due.notify) return@forEach
                    val millis = rawDigitsToMillis(due.value, spec) ?: return@forEach
                    val cal = Calendar.getInstance().apply { timeInMillis = millis }
                    // RÉCURRENT : correspondance par JOUR + MOIS, quelle que soit l'année stockée.
                    if (cal.get(Calendar.DAY_OF_MONTH) == todayDay &&
                        cal.get(Calendar.MONTH) == todayMonth) {
                        val key = "datenotif_${person.id}_${due.id}"
                        if (prefs.getLong(key, -1L) != todayEpochDay) {
                            // Drapeau posé SEULEMENT si le post a réussi (sinon une
                            // tentative ultérieure reste possible).
                            if (notifyImportantDate(appContext, person, due)) {
                                prefs.edit().putLong(key, todayEpochDay).apply()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Vue unifiée d'une date échéante (issue d'une dateLine ou du scalaire legacy). */
private data class DueDate(
    val value: String,
    val label: String,
    val notify: Boolean,
    val id: String
)

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

/** Résout le libellé d'une date : type connu localisé, sinon texte personnalisé. */
private fun labelFor(context: Context, label: String): String = when (label) {
    FieldTypes.DATE_BIRTHDAY -> context.getString(R.string.date_type_birthday)
    "anniversary"            -> context.getString(R.string.date_type_anniversary)
    "other", ""              -> context.getString(R.string.type_other)
    else                     -> label // libellé personnalisé saisi par l'utilisateur
}

/**
 * Construit et poste la notification (canal HIGH → heads-up). Renvoie true si le post
 * a réussi (permission présente, aucune exception) — condition du marquage anti-doublon.
 */
private fun notifyImportantDate(context: Context, person: Person, due: DueDate): Boolean {
    if (!hasNotificationPermission(context)) return false

    val isBirthday = due.label == FieldTypes.DATE_BIRTHDAY
    val title = if (isBirthday)
        context.getString(R.string.notif_birthday_title, person.firstName)
    else
        context.getString(R.string.notif_importantdate_title)
    val body = if (isBirthday)
        context.getString(R.string.notif_birthday_body)
    else
        context.getString(R.string.notif_importantdate_text, labelFor(context, due.label), person.firstName)

    // Tap → ouverture directe de la fiche du contact (deep link).
    val tapIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra(JtrNotificationManager.EXTRA_PERSON_ID, person.id)
    }
    val pendingIntent = PendingIntent.getActivity(
        context, (person.id + due.id).hashCode(), tapIntent,
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
        nm.notify((person.id + due.id).hashCode(), notification)
    }.isSuccess
}
