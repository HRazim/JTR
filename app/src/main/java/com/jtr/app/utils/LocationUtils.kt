package com.jtr.app.utils

import android.content.Context
import android.location.LocationManager
import android.os.Build

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
}
