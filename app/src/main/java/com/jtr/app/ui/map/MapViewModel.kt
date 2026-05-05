package com.jtr.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.remote.ApiClient
import com.jtr.app.data.remote.GeocodingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel : ViewModel() {

    private val _searchResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /** Sélection confirmée (ville, lat, lng) — null tant qu'aucune sélection. */
    private val _selectedLocation = MutableStateFlow<Triple<String, Double, Double>?>(null)
    val selectedLocation: StateFlow<Triple<String, Double, Double>?> = _selectedLocation.asStateFlow()

    /**
     * Événement one-shot : (lat, lng, zoom) — demande à la carte d'animer la caméra.
     * SharedFlow pour ne pas rejouer l'animation à chaque recomposition.
     * extraBufferCapacity = 1 garantit que tryEmit() n'est jamais bloquant.
     */
    private val _cameraEvent = MutableSharedFlow<Triple<Double, Double, Double>>(extraBufferCapacity = 1)
    val cameraEvent: SharedFlow<Triple<Double, Double, Double>> = _cameraEvent.asSharedFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        if (query.length < 3) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(400)
            try {
                val results = withContext(Dispatchers.IO) {
                    ApiClient.nominatimApi.searchCity(
                        cityName = query,
                        format = "json",
                        limit = 6
                    )
                }
                _searchResults.value = results.filter {
                    it.latitude != null && it.longitude != null
                }
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            }
            _isSearching.value = false
        }
    }

    fun clearResults() {
        _searchResults.value = emptyList()
    }

    /**
     * Sélection depuis la barre de recherche.
     * Calcule un niveau de zoom adapté au type de lieu (ville entière vs rue précise),
     * puis émet un événement caméra que la View consommera pour lancer l'animation.
     */
    fun selectFromSearch(result: GeocodingResult) {
        val lat = result.latitude ?: return
        val lng = result.longitude ?: return
        val city = result.displayName.split(",").first().trim()
        val zoom = zoomForResult(result)
        _selectedLocation.value = Triple(city, lat, lng)
        _cameraEvent.tryEmit(Triple(lat, lng, zoom))
        clearResults()
    }

    /**
     * Tap direct sur la carte : géocodage inverse via Nominatim.
     * Le marqueur visuel est déjà posé par la View pour un feedback immédiat.
     */
    fun onMapClick(lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ApiClient.nominatimApi.reverseGeocode(lat = lat, lon = lng)
                }
                val city = result.displayName.split(",").first().trim()
                _selectedLocation.value = Triple(city, lat, lng)
            } catch (_: Exception) {
                _selectedLocation.value = Triple("%.4f, %.4f".format(lat, lng), lat, lng)
            }
        }
    }

    /**
     * Zoom adapté à l'échelle du lieu retourné par Nominatim.
     * addressType est le champ le plus fiable ("city", "town", "village"…).
     * On replie sur type si addressType est absent.
     */
    private fun zoomForResult(result: GeocodingResult): Double {
        return when ((result.addressType ?: result.type)?.lowercase()) {
            "country"                         -> 4.0
            "state", "region"                 -> 6.0
            "county", "province"              -> 9.0
            "city", "municipality"            -> 11.5
            "town"                            -> 12.5
            "village", "hamlet", "borough"    -> 13.5
            "suburb", "quarter"               -> 14.0
            "neighbourhood", "residential"    -> 14.5
            else                              -> 14.5   // adresse précise ou type inconnu
        }
    }
}
