package com.jtr.app.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.jtr.app.JTRApplication
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.utils.JtrNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * ProximityCheckWorker — Moteur de Proximité Actif (v5.4).
 *
 * Worker périodique (3 h) qui réveille brièvement l'appareil, lit la DERNIÈRE
 * position connue (cache système — zéro tracking GPS continu, zéro drainage de
 * batterie) et alerte si un contact « rappel de proximité » se trouve à moins
 * de [JTRApplication.PROXIMITY_RADIUS_KM] (10 km).
 *
 * Cinématique (Dispatchers.IO) :
 *  1. permissions (fine + arrière-plan sur Android 10+) et toggles globaux ;
 *  2. `fusedLocationClient.lastLocation` (requête unique, jamais de polling) ;
 *  3. requête Room CIBLÉE : contacts actifs avec toggle + coordonnées valides ;
 *  4. [Location.distanceBetween] pour chaque candidat ;
 *  5. seuil ≤ 10 000 m + ANTI-SPAM : au plus une alerte par contact par 48 h
 *     (horodatage `proximityNotifiedAt` persisté en base).
 */
class ProximityCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = PersonRepository(context)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 1) Permissions : fine TOUJOURS, arrière-plan requis dès Android 10
        //    (le Worker s'exécute app fermée).
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val backgroundGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted || !backgroundGranted) return@withContext Result.success()

        // Toggles globaux (Paramètres).
        val prefs = context.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true) ||
            !prefs.getBoolean("proximity_enabled", false)
        ) {
            return@withContext Result.success()
        }

        try {
            // 2) Dernière position connue (cache système, requête unique).
            val location = fusedLocationClient.lastLocation.await()
                ?: return@withContext Result.success()

            // 3) Requête Room ciblée : toggle actif + coordonnées valides.
            val candidates = repository.getProximityCandidates()
            val radiusMeters = JTRApplication.PROXIMITY_RADIUS_KM * 1000f
            val now = System.currentTimeMillis()

            candidates.forEach { person ->
                val lat = person.cityLat ?: return@forEach
                val lng = person.cityLng ?: return@forEach

                // 4) Distance géodésique native (WGS84).
                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude, location.longitude, lat, lng, results
                )
                val distanceMeters = results[0]

                // 5) Seuil ≤ 10 km + idempotence 48 h.
                val lastNotified = person.proximityNotifiedAt ?: 0L
                val cooldownOver = now - lastNotified >= NOTIFY_COOLDOWN_MS
                if (distanceMeters <= radiusMeters && cooldownOver) {
                    JtrNotificationManager.showProximityNotification(context, person)
                    repository.markProximityNotified(person.id)
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        /** Anti-spam : au plus une alerte par contact par fenêtre de 48 heures. */
        val NOTIFY_COOLDOWN_MS: Long = TimeUnit.HOURS.toMillis(48)
    }
}
