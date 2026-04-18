package com.jtr.app.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jtr.app.JTRApplication
import com.jtr.app.R
import com.jtr.app.data.repository.PersonRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * BirthdayCheckWorker — Vérifie quotidiennement les anniversaires (PP3).
 */
class BirthdayCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = PersonRepository(context)

    override suspend fun doWork(): Result {
        val today = Calendar.getInstance()
        val todayDay = today.get(Calendar.DAY_OF_MONTH)
        val todayMonth = today.get(Calendar.MONTH)

        val persons = repository.getAllActive().first()
            .filter { it.birthdateNotify && it.birthdate != null }

        persons.forEach { person ->
            val birthday = Calendar.getInstance().apply {
                timeInMillis = person.birthdate!!
            }
            if (birthday.get(Calendar.DAY_OF_MONTH) == todayDay &&
                birthday.get(Calendar.MONTH) == todayMonth) {
                sendBirthdayNotification(person.firstName)
            }
        }
        return Result.success()
    }

    private fun sendBirthdayNotification(firstName: String) {
        val notification = NotificationCompat.Builder(context, JTRApplication.CHANNEL_BIRTHDAY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🎂 Anniversaire aujourd'hui !")
            .setContentText("C'est l'anniversaire de $firstName. Pense à lui souhaiter !")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(firstName.hashCode(), notification)
    }
}
