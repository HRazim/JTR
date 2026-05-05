package com.jtr.app.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.jtr.app.domain.model.Person
import com.jtr.app.worker.GeofenceBroadcastReceiver
import kotlinx.coroutines.tasks.await

class GeofenceManager(private val context: Context) {

    private val geofencingClient = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    suspend fun registerAll(persons: List<Person>, radiusMeters: Float) {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) return

        val eligible = persons
            .filter { it.cityNotify && it.hasGeoCoordinates }
            .sortedByDescending { it.lastContactedAt ?: 0L }
            .take(MAX_GEOFENCES)

        if (eligible.isEmpty()) return

        val geofences = eligible.map { person ->
            Geofence.Builder()
                .setRequestId(person.id)
                .setCircularRegion(person.cityLat!!, person.cityLng!!, radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // no immediate trigger on registration
            .addGeofences(geofences)
            .build()

        try {
            geofencingClient.addGeofences(request, pendingIntent).await()
        } catch (_: Exception) { }
    }

    suspend fun unregisterAll() {
        try {
            geofencingClient.removeGeofences(pendingIntent).await()
        } catch (_: Exception) { }
    }

    companion object {
        private const val MAX_GEOFENCES = 100
    }
}
