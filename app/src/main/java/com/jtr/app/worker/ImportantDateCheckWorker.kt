package com.jtr.app.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jtr.app.JTRApplication
import com.jtr.app.R
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.ui.person.rawDigitsToMillis
import com.jtr.app.ui.person.resolveDateFormatSpec
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
 * rencontre », etc.) et le nom du contact.
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

        val spec = resolveDateFormatSpec(Locale.getDefault())
        val today = Calendar.getInstance()
        val todayDay = today.get(Calendar.DAY_OF_MONTH)
        val todayMonth = today.get(Calendar.MONTH)

        val persons = repository.getAllActive().first()
        persons.forEach { person ->
            val lines = person.dateLines ?: return@forEach
            lines.forEach { line ->
                if (!line.notify) return@forEach
                val millis = rawDigitsToMillis(line.value, spec) ?: return@forEach
                val cal = Calendar.getInstance().apply { timeInMillis = millis }
                if (cal.get(Calendar.DAY_OF_MONTH) == todayDay &&
                    cal.get(Calendar.MONTH) == todayMonth) {
                    notifyImportantDate(person.firstName, line)
                }
            }
        }
        return Result.success()
    }

    /** Résout le libellé d'une ligne : type connu localisé, sinon texte personnalisé. */
    private fun labelFor(line: DynamicLine): String = when (line.label) {
        FieldTypes.DATE_BIRTHDAY -> context.getString(R.string.date_type_birthday)
        "anniversary"            -> context.getString(R.string.date_type_anniversary)
        "other", ""              -> context.getString(R.string.type_other)
        else                     -> line.label // libellé personnalisé saisi par l'utilisateur
    }

    private fun notifyImportantDate(firstName: String, line: DynamicLine) {
        val label = labelFor(line)
        val notification = NotificationCompat.Builder(context, JTRApplication.CHANNEL_BIRTHDAY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_importantdate_title))
            .setContentText(context.getString(R.string.notif_importantdate_text, label, firstName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((firstName + line.id).hashCode(), notification)
    }
}
