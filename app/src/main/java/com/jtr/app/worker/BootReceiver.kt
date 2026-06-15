package com.jtr.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BootReceiver — réarme les alarmes de rappel après un redémarrage (les alarmes
 * AlarmManager sont perdues au reboot) ou une mise à jour de l'application (v7.0).
 *
 * Réagit à BOOT_COMPLETED et à MY_PACKAGE_REPLACED, puis délègue à
 * [ReminderScheduler.rescheduleAll] (calcul des prochaines occurrences + rattrapage
 * des fenêtres déjà ouvertes).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val pending = goAsync()
                val app = context.applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        ReminderScheduler.rescheduleAll(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
