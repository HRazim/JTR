package com.jtr.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * ImportantDateCheckWorker — filet de sécurité quotidien (et balayage à l'ouverture)
 * qui RÉARME les alarmes de rappel des dates importantes.
 *
 * v7.0 — la livraison précise (à la minute, à « minuit − délai ») est désormais assurée
 * par des alarmes exactes ([ReminderScheduler] + [ReminderAlarmReceiver]). Ce Worker ne
 * poste plus lui-même : il appelle [ReminderScheduler.rescheduleAll], qui rattrape toute
 * fenêtre déjà ouverte (idempotent) et reprogramme les prochaines occurrences. Il couvre
 * les cas où une alarme aurait pu être perdue (force-stop, optimisations agressives),
 * en complément du réarmement au démarrage de l'app et au reboot ([BootReceiver]).
 */
class ImportantDateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ReminderScheduler.rescheduleAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NOW = "important_date_check_now"

        /** Réarmement immédiat via WorkManager (ouverture de l'app, balayage de secours). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ImportantDateCheckWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
