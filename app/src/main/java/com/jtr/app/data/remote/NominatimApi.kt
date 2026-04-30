package com.jtr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * NominatimApi — API externe pour le géocodage (PP3).
 *
 * [PP3 — Prof] : "Intégration avec une API externe."
 * Utilise OpenStreetMap Nominatim (gratuit, pas de clé API requise).
 * Convertit un nom de ville en coordonnées GPS (latitude/longitude).
 *
 * Documentation : https://nominatim.org/release-docs/latest/api/Search/
 */
interface NominatimApi {

    @GET("search")
    suspend fun searchCity(
        @Query("q") cityName: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1,
        @Query("addressdetails") addressDetails: Int = 1
    ): List<GeocodingResult>

    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json"
    ): GeocodingResult
}
