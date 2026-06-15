package com.jtr.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.utils.JtrNotificationManager
import com.jtr.app.worker.ProximityCheckWorker.Companion.NOTIFY_COOLDOWN_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Déclencheur TEMPS RÉEL du Moteur de Proximité : réagit aux transitions ENTER
 * des géofences (rayon [com.jtr.app.JTRApplication.PROXIMITY_RADIUS_KM]).
 * Partage avec le Worker périodique la même notification conviviale
 * ([JtrNotificationManager]) et le même ANTI-SPAM 48 h (`proximityNotifiedAt`).
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val triggeringFences = event.triggeringGeofences ?: return

        val prefs = context.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true) ||
            !prefs.getBoolean("proximity_enabled", false)) return

        val pendingResult = goAsync()
        val personDao = AppDatabase.getInstance(context).personDao()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                triggeringFences.forEach { fence ->
                    val person = personDao.getById(fence.requestId) ?: return@forEach
                    if (!person.cityNotify) return@forEach
                    // Anti-spam partagé : une alerte max par contact par 48 h.
                    val lastNotified = person.proximityNotifiedAt ?: 0L
                    if (now - lastNotified < NOTIFY_COOLDOWN_MS) return@forEach

                    JtrNotificationManager.showProximityNotification(context, person)
                    personDao.markProximityNotified(person.id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
