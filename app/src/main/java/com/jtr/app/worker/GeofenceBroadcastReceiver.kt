package com.jtr.app.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.jtr.app.JTRApplication
import com.jtr.app.MainActivity
import com.jtr.app.R
import com.jtr.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val triggeringFences = event.triggeringGeofences ?: return

        val prefs = context.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true) ||
            !prefs.getBoolean("proximity_enabled", true)) return

        val pendingResult = goAsync()
        val personDao = AppDatabase.getInstance(context).personDao()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                triggeringFences.forEach { fence ->
                    val person = personDao.getById(fence.requestId) ?: return@forEach
                    if (!person.cityNotify) return@forEach
                    val daysSince = person.daysSinceLastContact() ?: Long.MAX_VALUE
                    if (daysSince <= 90) return@forEach

                    sendNotification(
                        context = context,
                        notifId = person.id.hashCode(),
                        firstName = person.firstName,
                        city = person.city ?: "cette ville",
                        daysSince = daysSince
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendNotification(
        context: Context,
        notifId: Int,
        firstName: String,
        city: String,
        daysSince: Long
    ) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val message = "Tu es actuellement à $city — ça fait $daysSince jours que vous ne vous êtes pas vus !"

        val notification = NotificationCompat.Builder(context, JTRApplication.CHANNEL_PROXIMITY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$firstName est dans les parages !")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notifId, notification)
    }
}
