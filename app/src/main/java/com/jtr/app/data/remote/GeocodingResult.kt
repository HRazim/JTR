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
    @SerialName("place_id") val placeId: Long? = null,
    // Champ retourné par Nominatim : "city", "town", "village", "suburb", etc.
    // Utilisé pour choisir un niveau de zoom adapté à l'échelle du lieu.
    @SerialName("addresstype") val addressType: String? = null,
    @SerialName("type") val type: String? = null,
    // v7.1.32 — nom propre du lieu (POI/commerce, ex. « Tour Eiffel »). Vide pour une
    // simple adresse. Présent en `format=json` (search ET reverse).
    @SerialName("name") val name: String? = null,
    // v7.1.32 — sous-objet d'adresse structurée (retourné avec addressdetails=1, déjà actif
    // par défaut sur le reverse, explicite sur le search). Sert à composer un libellé COMPLET
    // (numéro + rue + localité + pays) au lieu du seul 1ᵉʳ composant de `display_name` (= le
    // numéro pour une adresse). Champs absents → null (jamais de crash).
    @SerialName("address") val address: Address? = null
) {
    val latitude: Double? get() = lat.toDoubleOrNull()
    val longitude: Double? get() = lon.toDoubleOrNull()
}

/**
 * Adresse structurée Nominatim (v7.1.32) — tous les champs sont optionnels et reviennent
 * dans la langue de JTR (via l'interceptor `Accept-Language` v7.1.31). On garde plusieurs
 * variantes de « voie » et de « localité » pour couvrir piéton/résidentiel/village/etc.
 */
@Serializable
data class Address(
    @SerialName("house_number") val houseNumber: String? = null,
    @SerialName("road") val road: String? = null,
    @SerialName("pedestrian") val pedestrian: String? = null,
    @SerialName("residential") val residential: String? = null,
    @SerialName("footway") val footway: String? = null,
    @SerialName("street") val street: String? = null,
    @SerialName("neighbourhood") val neighbourhood: String? = null,
    @SerialName("suburb") val suburb: String? = null,
    @SerialName("quarter") val quarter: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("town") val town: String? = null,
    @SerialName("village") val village: String? = null,
    @SerialName("municipality") val municipality: String? = null,
    @SerialName("county") val county: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null
)
