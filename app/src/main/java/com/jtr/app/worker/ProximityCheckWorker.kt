package com.jtr.app.worker

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
 * ProximityCheckWorker — Vérifie la proximité sociale toutes les 6h.
 *
 * [PP3 — Fonctionnalité créative] : "Rappel de proximité sociale".
 * Notifie quand l'utilisateur est à moins de N km d'un contact (N configurable
 * dans les Paramètres, persisté dans SharedPreferences sous "proximity_radius_km").
 */
class ProximityCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = PersonRepository(context)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }

        // Vérifie si les notifications de proximité sont activées dans les paramètres
        val prefs = context.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        val proximityEnabled = prefs.getBoolean("proximity_enabled", true)
        if (!notificationsEnabled || !proximityEnabled) return Result.success()

        // Rayon configurable par l'utilisateur (défaut : 5 km)
        val radiusKm = prefs.getFloat("proximity_radius_km", PROXIMITY_RADIUS_DEFAULT_KM).toDouble()

        return try {
            val location = fusedLocationClient.lastLocation.await() ?: return Result.success()

            val contacts = repository.getAllActive().first()
                .filter { it.cityNotify && it.hasGeoCoordinates }

            contacts.forEach { person ->
                val distance = calculateDistance(
                    location.latitude, location.longitude,
                    person.cityLat!!, person.cityLng!!
                )

                // Distance < rayon configuré ET pas contacté depuis 90+ jours
                val shouldNotify = distance < radiusKm &&
                        (person.daysSinceLastContact() ?: Long.MAX_VALUE) > 90

                if (shouldNotify) {
                    sendProximityNotification(person, distance.toInt(), radiusKm.toInt())
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /** Formule de Haversine : distance entre deux points GPS en km. */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun sendProximityNotification(person: Person, distanceKm: Int, radiusKm: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, person.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val days = person.daysSinceLastContact() ?: 0
        val message = "Tu es à ${distanceKm} km de ${person.city} " +
                "(rayon configuré : ${radiusKm} km). " +
                "Cela fait $days jours que tu n'as pas contacté ${person.firstName} !"

        val notification = NotificationCompat.Builder(context, JTRApplication.CHANNEL_PROXIMITY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${person.firstName} est dans les parages !")
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
        // Rayon par défaut si jamais l'utilisateur n'a pas modifié les paramètres
        const val PROXIMITY_RADIUS_DEFAULT_KM = 5f
    }
}
