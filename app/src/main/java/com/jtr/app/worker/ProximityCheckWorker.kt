package com.jtr.app.worker

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.jtr.app.JTRApplication
import com.jtr.app.MainActivity
import com.jtr.app.R
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Person
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlin.math.*

/**
 * ProximityCheckWorker — Worker périodique qui vérifie la proximité (PP3).
 *
 * [PP3 — Fonctionnalité créative] : "Rappel de proximité sociale"
 * Toutes les 6h, compare la position actuelle de l'utilisateur avec les villes
 * des contacts ayant activé cityNotify=true, et notifie si distance < 10 km.
 */
class ProximityCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = PersonRepository(context)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun doWork(): Result {
        // Vérifie la permission
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return Result.success() // Pas de permission, on ne fait rien
        }

        return try {
            val location = fusedLocationClient.lastLocation.await() ?: return Result.success()

            // Récupère les contacts avec notifications actives
            val contacts = repository.getAllActive().first()
                .filter { it.cityNotify && it.hasGeoCoordinates }

            contacts.forEach { person ->
                val distance = calculateDistance(
                    location.latitude, location.longitude,
                    person.cityLat!!, person.cityLng!!
                )

                // Distance < 10 km ET pas contacté depuis 90+ jours
                val shouldNotify = distance < PROXIMITY_RADIUS_KM &&
                        (person.daysSinceLastContact() ?: Long.MAX_VALUE) > 90

                if (shouldNotify) {
                    sendProximityNotification(person, distance.toInt())
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Formule de Haversine : distance entre deux points GPS en km.
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun sendProximityNotification(person: Person, distanceKm: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, person.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val days = person.daysSinceLastContact() ?: 0
        val message = "Tu es à ${distanceKm} km de ${person.city}. " +
                "Cela fait $days jours que tu n'as pas contacté ${person.firstName} !"

        val notification = NotificationCompat.Builder(context, JTRApplication.CHANNEL_PROXIMITY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${person.firstName} est dans les parages")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(person.id.hashCode(), notification)
    }

    companion object {
        private const val PROXIMITY_RADIUS_KM = 10.0
    }
}
