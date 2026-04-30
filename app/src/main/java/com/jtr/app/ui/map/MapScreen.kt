package com.jtr.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.location.Geocoder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.util.Locale

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onLocationSelected: (cityName: String, lat: Double, lng: Double) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    var showResults by remember { mutableStateOf(false) }

    val markerRef = remember { arrayOfNulls<Marker>(1) }
    val mapView = rememberMapViewWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choisir une ville") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Carte MapLibre (couche de fond) ────────────────────────────
            AndroidView(
                factory = { _ ->
                    mapView.apply {
                        getMapAsync { map ->
                            map.uiSettings.apply {
                                isScrollGesturesEnabled = true
                                isZoomGesturesEnabled = true
                                isRotateGesturesEnabled = true
                                isTiltGesturesEnabled = true
                            }
                            map.setStyle(STYLE_URL)
                            map.addOnMapClickListener { point ->
                                // Feedback visuel immédiat
                                markerRef[0]?.remove()
                                markerRef[0] = map.addMarker(
                                    MarkerOptions().position(LatLng(point.latitude, point.longitude))
                                )
                                scope.launch(Dispatchers.IO) {
                                    val name = try {
                                        val geocoder = Geocoder(context, Locale.getDefault())
                                        @Suppress("DEPRECATION")
                                        val addresses = geocoder.getFromLocation(
                                            point.latitude, point.longitude, 1
                                        )
                                        addresses?.firstOrNull()?.let {
                                            it.locality ?: it.subAdminArea ?: it.adminArea
                                        }
                                    } catch (_: Exception) { null }
                                        ?: "%.5f, %.5f".format(point.latitude, point.longitude)

                                    android.util.Log.d("MapPicker", "onLocationSelected → $name")
                                    withContext(Dispatchers.Main) {
                                        onLocationSelected(name, point.latitude, point.longitude)
                                    }
                                }
                                true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── Barre de recherche (overlay haut) ───────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .zIndex(1f)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { q ->
                        query = q
                        showResults = q.length >= 3
                        if (q.length >= 3) viewModel.search(q)
                        else if (q.isEmpty()) { viewModel.clearResults(); showResults = false }
                    },
                    label = { Text("Rechercher une ville") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        when {
                            isSearching -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            query.isNotEmpty() -> IconButton(onClick = {
                                query = ""
                                viewModel.clearResults()
                                showResults = false
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (showResults && results.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(results) { result ->
                                val cityName = result.displayName.split(",").first().trim()
                                ListItem(
                                    headlineContent = { Text(cityName) },
                                    supportingContent = {
                                        Text(
                                            result.displayName,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        val lat = result.latitude ?: return@clickable
                                        val lng = result.longitude ?: return@clickable
                                        onLocationSelected(cityName, lat, lng)
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Forward onLowMemory so MapView can release GL resources under pressure
    DisposableEffect(context) {
        val callbacks = object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = mapView.onLowMemory()
            override fun onConfigurationChanged(c: android.content.res.Configuration) = Unit
            override fun onLowMemory() = mapView.onLowMemory()
        }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // ON_CREATE already handled in remember block above
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Only stop/destroy if the Activity is still alive (e.g. screen nav).
            // When the Activity is destroyed the observer already called them —
            // calling twice corrupts MapView's GL state.
            if (lifecycle.currentState != Lifecycle.State.DESTROYED) {
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }
    return mapView
}
