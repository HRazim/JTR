package com.jtr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GeocodingResult — Résultat de l'API Nominatim.
 *
 * [PP3] Modèle de données pour la réponse JSON du géocodage.
 * Les coordonnées sont reçues en String et converties en Double.
 */
@Serializable
data class GeocodingResult(
    @SerialName("lat") val lat: String,
    @SerialName("lon") val lon: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("place_id") val placeId: Long? = null
) {
    val latitude: Double? get() = lat.toDoubleOrNull()
    val longitude: Double? get() = lon.toDoubleOrNull()
}
