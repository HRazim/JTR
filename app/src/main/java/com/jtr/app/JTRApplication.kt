package com.jtr.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jtr.app.utils.GeofenceManager
import com.jtr.app.worker.ProximityCheckWorker
import org.maplibre.android.MapLibre
import java.util.concurrent.TimeUnit

/**
 * JTRApplication — Classe Application (v4.0).
 *
 * Initialise les éléments globaux : MapLibre, GeofenceManager, canaux de notification
 * et WorkManager pour les vérifications de proximité périodiques.
 */
class JTRApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        geofenceManager = GeofenceManager(this)
        createNotificationChannels()
        scheduleProximityChecks()
    }

    /**
     * Crée les canaux de notification (requis Android 8+).
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val proximityChannel = NotificationChannel(
                CHANNEL_PROXIMITY,
                "Rappels de proximité",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications quand vous êtes dans la même ville qu'un contact"
            }

            val birthdayChannel = NotificationChannel(
                CHANNEL_BIRTHDAY,
                "Anniversaires",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rappels d'anniversaires des contacts"
            }

            nm.createNotificationChannel(proximityChannel)
            nm.createNotificationChannel(birthdayChannel)
        }
    }

    /**
     * Planifie un Worker périodique qui vérifie la proximité toutes les 6 heures.
     */
    private fun scheduleProximityChecks() {
        val request = PeriodicWorkRequestBuilder<ProximityCheckWorker>(
            6, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "proximity_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val CHANNEL_PROXIMITY = "proximity_channel"
        const val CHANNEL_BIRTHDAY = "birthday_channel"

        /** Singleton initialisé dans onCreate() — null uniquement en tests unitaires JVM. */
        var geofenceManager: GeofenceManager? = null
            private set
    }
}
