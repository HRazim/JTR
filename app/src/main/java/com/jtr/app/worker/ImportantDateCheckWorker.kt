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
import java.util.Calendar
import java.util.Locale

/**
 * ImportantDateCheckWorker — Balaie chaque jour TOUTES les dates importantes (v4.5).
 *
 * Anciennement `BirthdayCheckWorker`, limité à l'anniversaire scalaire. Désormais il
 * parcourt les `dateLines` JSON de chaque contact : pour chaque ligne marquée
 * `notify = true` dont le jour/mois tombe aujourd'hui, il déclenche une notification
 * personnalisée affichant le libellé de la date (« anniversaire », « anniversaire de
 * rencontre », etc.) et le nom du contact. Repli legacy : un profil pré-v12 ne
 * possédant que le scalaire `birthdate` est traité comme une ligne anniversaire
 * (cloche `birthdateNotify`), exactement comme le bandeau d'accueil.
 */
class ImportantDateCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = PersonRepository(context)

    override suspend fun doWork(): Result {
        val prefs = context.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true) ||
            !prefs.getBoolean("birthday_enabled", true)) return Result.success()

        // Sans POST_NOTIFICATIONS accordée (Android 13+), nm.notify() est ignoré
        // silencieusement par le système : inutile de balayer la base.
        if (!hasNotificationPermission()) return Result.success()

        val spec = resolveDateFormatSpec(Locale.getDefault())
        val today = Calendar.getInstance()
        val todayDay = today.get(Calendar.DAY_OF_MONTH)
        val todayMonth = today.get(Calendar.MONTH)

        val persons = repository.getAllActive().first()
        persons.forEach { person ->
            // dateLines JSON en priorité ; repli sur le scalaire birthdate (legacy),
            // identique au calcul du bandeau d'accueil (computeUpcomingEvents).
            val dates: List<DueDate> = person.dateLines?.takeIf { it.isNotEmpty() }
                ?.map { DueDate(value = it.value, label = it.label, notify = it.notify, id = it.id) }
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
                if (cal.get(Calendar.DAY_OF_MONTH) == todayDay &&
                    cal.get(Calendar.MONTH) == todayMonth) {
                    notifyImportantDate(person, due)
                }
            }
        }
        return Result.success()
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /** Résout le libellé d'une date : type connu localisé, sinon texte personnalisé. */
    private fun labelFor(label: String): String = when (label) {
        FieldTypes.DATE_BIRTHDAY -> context.getString(R.string.date_type_birthday)
        "anniversary"            -> context.getString(R.string.date_type_anniversary)
        "other", ""              -> context.getString(R.string.type_other)
        else                     -> label // libellé personnalisé saisi par l'utilisateur
    }

    private fun notifyImportantDate(person: Person, due: DueDate) {
        val label = labelFor(due.label)

        // Tap → ouverture directe de la fiche du contact (deep link), cohérent
        // avec les notifications de proximité (JtrNotificationManager).
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
            .setContentTitle(context.getString(R.string.notif_importantdate_title))
            .setContentText(context.getString(R.string.notif_importantdate_text, label, person.firstName))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((person.firstName + due.id).hashCode(), notification)
    }

    /** Vue unifiée d'une date échéante (issue d'une dateLine ou du scalaire legacy). */
    private data class DueDate(
        val value: String,
        val label: String,
        val notify: Boolean,
        val id: String
    )
}
