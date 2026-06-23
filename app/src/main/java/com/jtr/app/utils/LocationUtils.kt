package com.jtr.app.utils

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Helpers d'état de la localisation système, partagés (Accueil + Paramètres). */
object LocationUtils {

    /**
     * État RÉEL de la localisation système (interrupteur du téléphone). Indépendant
     * des permissions de l'app : la fonctionnalité de proximité ne peut PAS marcher
     * si cet interrupteur est éteint. `LocationManager.isLocationEnabled` n'existe
     * qu'à partir d'API 28 (P) → repli sur l'état des fournisseurs sous API 26-27.
     */
    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Octroi de la localisation EN ARRIÈRE-PLAN, APRÈS la divulgation explicite (le dialogue
     * de divulgation reste géré par l'appelant). SOURCE UNIQUE partagée par le flux Paramètres
     * ET le flux « rappel de proximité par contact » → ils ne peuvent plus diverger.
     *  - **Android 10 (Q)** : requête runtime ([onRuntimeRequest]) — le système propose
     *    « Autoriser tout le temps » dans sa boîte de dialogue.
     *  - **Android 11+ (R+)** : l'octroi « Toujours autoriser » passe OBLIGATOIREMENT par les
     *    Réglages système (la requête runtime y est IGNORÉE = no-op) → on ouvre la fiche de
     *    l'app pour que l'utilisateur l'accorde manuellement.
     */
    fun requestBackgroundLocation(context: Context, onRuntimeRequest: () -> Unit) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            onRuntimeRequest()
        } else {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            }
        }
    }
}
