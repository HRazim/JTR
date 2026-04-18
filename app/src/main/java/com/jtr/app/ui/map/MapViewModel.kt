package com.jtr.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.remote.ApiClient
import com.jtr.app.data.remote.GeocodingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel : ViewModel() {

    private val _searchResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        if (query.length < 3) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(400) // debounce
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
}
