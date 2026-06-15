package com.jtr.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ReminderAlarmReceiver — réveillé par l'[android.app.AlarmManager] à l'instant exact
 * d'un rappel (v7.0). Il délègue à [ReminderScheduler.rescheduleAll] : la fenêtre de
 * l'occurrence qui vient de s'ouvrir est postée (rattrapage idempotent) et l'occurrence
 * suivante est immédiatement réarmée — un seul chemin, zéro doublon.
 *
 * [goAsync] prolonge la vie du receiver le temps de l'I/O Room + du post (le scope
 * survit au retour de onReceive ; `pending.finish()` est appelé dans tous les cas).
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
