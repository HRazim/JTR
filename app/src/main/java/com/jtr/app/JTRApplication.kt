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
import com.jtr.app.utils.JtrNotificationManager
import com.jtr.app.worker.ImportantDateCheckWorker
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
        scheduleImportantDateChecks()
    }

    /**
     * Crée les canaux de notification (requis Android 8+). Le canal des alertes
     * de proximité (importance HAUTE, v5.4) est géré par [JtrNotificationManager],
     * qui retire aussi l'ancien canal v4.
     */
    private fun createNotificationChannels() {
        JtrNotificationManager.ensureProximityChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val birthdayChannel = NotificationChannel(
                CHANNEL_BIRTHDAY,
                getString(R.string.notif_channel_birthday_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_birthday_desc)
            }
            nm.createNotificationChannel(birthdayChannel)
        }
    }

    /**
     * Planifie le Moteur de Proximité : vérification toutes les 3 heures (fenêtre
     * 2-4 h du cahier des charges v5.4 — le cache de localisation système est lu
     * en une requête unique, zéro drainage de batterie). Politique UPDATE : le
     * nouvel intervalle remplace l'ancienne planification 6 h des installations
     * existantes sans dupliquer le travail.
     */
    private fun scheduleProximityChecks() {
        val request = PeriodicWorkRequestBuilder<ProximityCheckWorker>(
            3, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "proximity_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Planifie le balayage quotidien des dates importantes (anniversaires et dates
     * personnalisées marquées « notifier »). Voir [ImportantDateCheckWorker].
     *
     * Délai initial calé sur le prochain créneau du matin ([CHECK_HOUR_OF_DAY]) :
     * la notification arrive ainsi le jour même à une heure pertinente, et non à
     * un instant arbitraire dépendant de l'heure d'installation. Politique UPDATE :
     * les installations existantes (ancien planning sans délai) sont recalées sans
     * dupliquer le travail.
     */
    private fun scheduleImportantDateChecks() {
        val request = PeriodicWorkRequestBuilder<ImportantDateCheckWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(millisUntilNextMorning(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "important_date_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Millisecondes jusqu'au prochain [CHECK_HOUR_OF_DAY]:00 local. */
    private fun millisUntilNextMorning(): Long {
        val now = java.util.Calendar.getInstance()
        val next = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, CHECK_HOUR_OF_DAY)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (!after(now)) add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    companion object {
        const val CHANNEL_BIRTHDAY = "birthday_channel"

        /** Heure locale du balayage quotidien des dates importantes (9 h du matin). */
        private const val CHECK_HOUR_OF_DAY = 9

        /**
         * Rayon de détection du Moteur de Proximité (v5.4) : seuil de 10 km
         * (10 000 m), borne haute de la fenêtre 5-10 km du cahier des charges —
         * assez large pour un passage en voiture, assez serré pour rester
         * pertinent. Partagé par le Worker périodique ET le geofencing.
         */
        const val PROXIMITY_RADIUS_KM = 10f

        /** Singleton initialisé dans onCreate() — null uniquement en tests unitaires JVM. */
        var geofenceManager: GeofenceManager? = null
            private set
    }
}
