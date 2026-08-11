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
    // v7.1.46 — IDENTITÉ STABLE de l'objet OSM. À NE PAS confondre avec `place_id`, qui est un
    // numéro de ligne INTERNE au nœud Nominatim ayant répondu : mesuré sur nominatim.openstreetmap.org,
    // deux requêtes identiques renvoient le MÊME objet avec des `place_id` différents (cluster
    // à répartition de charge). `osm_type`+`osm_id` sont, eux, invariants — seule paire utilisable
    // comme départage stable entre deux candidats ex æquo.
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    // Champ retourné par Nominatim : "city", "town", "village", "suburb", etc.
    // Utilisé pour choisir un niveau de zoom adapté à l'échelle du lieu.
    @SerialName("addresstype") val addressType: String? = null,
    @SerialName("type") val type: String? = null,
    // v7.1.46 — catégorie OSM du résultat : "place" (nœud/zone habitée) vs "boundary"
    // (frontière administrative), etc. `class` étant un mot-clé Kotlin, le champ est renommé.
    // Sert de repli au classement quand `addresstype` est absent.
    @SerialName("class") val placeClass: String? = null,
    // v7.1.46 — score de pertinence Nominatim (0..1). N'ÉTAIT PAS parsé : le géocodage de ville
    // prenait donc le 1ᵉʳ résultat sans jamais pouvoir comparer. Attention, il est fréquemment
    // EX ÆQUO entre deux objets d'une même ville (cf. Safi : « city » et « administrative » à
    // 0.5247 tous les deux, à ~4 km l'un de l'autre) — il ne suffit pas à départager seul,
    // d'où le tri à trois niveaux de [com.jtr.app.data.repository.GeocodingRepository].
    @SerialName("importance") val importance: Double? = null,
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
