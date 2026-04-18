package com.jtr.app.data.repository

import com.jtr.app.data.remote.ApiClient
import com.jtr.app.data.remote.GeocodingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GeocodingRepository — Pont entre l'API externe et l'app.
 *
 * [PP3] Convertit un nom de ville en coordonnées GPS pour le géofencing.
 */
class GeocodingRepository {

    private val api = ApiClient.nominatimApi

    /**
     * Recherche les coordonnées d'une ville.
     * Retourne null en cas d'échec ou si la ville est introuvable.
     */
    suspend fun getCityCoordinates(cityName: String): GeocodingResult? {
        return withContext(Dispatchers.IO) {
            try {
                val results = api.searchCity(cityName)
                results.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }
}
