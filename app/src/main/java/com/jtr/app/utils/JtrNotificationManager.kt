package com.jtr.app.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jtr.app.MainActivity
import com.jtr.app.R
import com.jtr.app.domain.model.Person

/**
 * JtrNotificationManager — Moteur de Proximité v5.4.
 *
 * Centralise le canal Material 3 dédié [CHANNEL_PROXIMITY_ALERTS] (importance
 * HAUTE : l'alerte apparaît en heads-up quand on passe près d'un contact) et la
 * construction de la notification conviviale, avec DEEP LINK : le tap ouvre
 * directement [MainActivity] sur le PersonDetailScreen du contact concerné
 * (extra [EXTRA_PERSON_ID] consommé par la navigation).
 *
 * Utilisé par les DEUX déclencheurs de proximité : le Worker périodique
 * (ProximityCheckWorker) et le geofencing temps réel (GeofenceBroadcastReceiver).
 */
object JtrNotificationManager {

    /** Canal des alertes de proximité (v5.4) — importance haute. */
    const val CHANNEL_PROXIMITY_ALERTS = "jtr_proximity_alerts"

    /** Extra d'Intent : id du contact à ouvrir au tap sur la notification. */
    const val EXTRA_PERSON_ID = "jtr_extra_person_id"

    /** Ancien canal v4 (importance par défaut), remplacé — supprimé au démarrage. */
    private const val LEGACY_CHANNEL_PROXIMITY = "proximity_channel"

    /**
     * Crée le canal d'alertes de proximité (Android 8+) et retire l'ancien canal
     * v4 pour ne pas dupliquer les entrées dans les réglages système.
     */
    fun ensureProximityChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.deleteNotificationChannel(LEGACY_CHANNEL_PROXIMITY)
            val channel = NotificationChannel(
                CHANNEL_PROXIMITY_ALERTS,
                context.getString(R.string.notif_channel_proximity_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_proximity_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Affiche l'alerte de proximité pour [person] :
     *  - Titre  : « Proximité détectée ! 📍 »
     *  - Corps  : « Vous passez pas très loin de {ville}. {Prénom} est dans le
     *    coin, pourquoi ne pas lui passer un petit coucou ? 👋 »
     *  - Tap    : ouverture directe de la fiche du contact.
     *
     * Silencieusement ignorée si POST_NOTIFICATIONS n'est pas accordée (33+).
     */
    fun showProximityNotification(context: Context, person: Person) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val place = person.city ?: person.origin ?: ""
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_PERSON_ID, person.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, person.id.hashCode(), tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val message = context.getString(
            R.string.notif_proximity_v2_text, place, person.firstName
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_PROXIMITY_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_proximity_v2_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(person.id.hashCode(), notification)
    }
}
