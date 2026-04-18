package com.jtr.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.GeofencingEvent

/**
 * GeofenceBroadcastReceiver — Récepteur pour les événements de géofencing (PP3).
 *
 * Déclenché automatiquement par le système quand l'utilisateur entre dans
 * un géofence défini autour d'une ville d'un contact.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return

        if (event.hasError()) return

        // Utilise le Worker pour gérer la notification (évite le code long dans le receiver)
        androidx.work.WorkManager.getInstance(context).enqueue(
            androidx.work.OneTimeWorkRequestBuilder<ProximityCheckWorker>().build()
        )
    }
}
