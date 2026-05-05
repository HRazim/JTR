package com.jtr.app.ui.map

import android.view.MotionEvent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** Durée de l'animation de survol en millisecondes. */
private const val CAMERA_ANIMATION_DURATION_MS = 1800

/** Padding overlay en dp : laisse de l'air sous la search bar et au-dessus du bandeau bas. */
private const val OVERLAY_TOP_DP = 76f
private const val OVERLAY_BOTTOM_DP = 90f
private const val OVERLAY_SIDE_DP = 32f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onLocationSelected: (cityName: String, lat: Double, lng: Double) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val selectedLocation by viewModel.selectedLocation.collectAsStateWithLifecycle()
    var showResults by remember { mutableStateOf(false) }

    // Références stables à la carte et au marqueur courant
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    val markerRef = remember { arrayOfNulls<Marker>(1) }
    val mapView = rememberMapViewWithLifecycle()

    // ── Animation caméra (événement one-shot depuis le ViewModel) ───────────
    // collectLatest : si l'utilisateur sélectionne un 2e résultat pendant que la 1ère
    // animation tourne, le bloc précédent est annulé avant que le nouveau démarre.
    LaunchedEffect(Unit) {
        viewModel.cameraEvent.collectLatest { (lat, lng, zoom) ->
            val map = mapRef[0] ?: return@collectLatest
            // Marqueur posé avant l'animation pour un feedback visuel immédiat
            markerRef[0]?.remove()
            markerRef[0] = map.addMarker(MarkerOptions().position(LatLng(lat, lng)))
            // easeCamera : ease-in-out natif MapLibre (easingInterpolator = true)
            // Le CancelableCallback gère l'annulation propre si l'utilisateur
            // interrompt l'animation par un geste (scroll, pinch, etc.).
            map.easeCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom),
                CAMERA_ANIMATION_DURATION_MS,
                true, // ease-in-out : départ et arrivée progressifs
                object : MapLibreMap.CancelableCallback {
                    override fun onCancel() {
                        // Interruption propre par geste utilisateur — état cohérent,
                        // le marqueur et le bandeau de sélection restent valides.
                    }
                    override fun onFinish() {}
                }
            )
        }
    }

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
            // ── Carte MapLibre (couche de fond) ────────────────────────────────
            AndroidView(
                factory = { _ ->
                    mapView.apply {
                        // Empêche le parent Compose d'intercepter les gestes dès le premier toucher.
                        // Sans ça, pan et pinch-to-zoom sont avalés par le système de gestes Compose.
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_POINTER_DOWN ->
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL ->
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false // laisser MapView traiter l'événement
                        }
                        getMapAsync { map ->
                            mapRef[0] = map
                            map.uiSettings.apply {
                                isScrollGesturesEnabled = true
                                isZoomGesturesEnabled = true
                                isRotateGesturesEnabled = true
                                isTiltGesturesEnabled = true
                            }
                            // Padding permanent : décale le point focal de la carte
                            // pour que les markers soient toujours visibles entre la
                            // search bar (haut) et le bandeau de sélection (bas).
                            val d = resources.displayMetrics.density
                            map.setPadding(
                                (OVERLAY_SIDE_DP   * d).toInt(),
                                (OVERLAY_TOP_DP    * d).toInt(),
                                (OVERLAY_SIDE_DP   * d).toInt(),
                                (OVERLAY_BOTTOM_DP * d).toInt()
                            )
                            map.setStyle(STYLE_URL)
                            map.addOnMapClickListener { point ->
                                // Feedback visuel immédiat — le nom arrive après le géocodage inverse
                                markerRef[0]?.remove()
                                markerRef[0] = map.addMarker(
                                    MarkerOptions().position(LatLng(point.latitude, point.longitude))
                                )
                                // Géocodage inverse délégué au ViewModel (UDF)
                                viewModel.onMapClick(point.latitude, point.longitude)
                                true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── Barre de recherche (overlay haut) ─────────────────────────────
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
                                        // Zoom + marqueur délégués au ViewModel via cameraEvent
                                        viewModel.selectFromSearch(result)
                                        query = ""
                                        showResults = false
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // ── Bandeau bas : ville sélectionnée + bouton Enregistrer ──────────
            selectedLocation?.let { (city, lat, lng) ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .zIndex(1f),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                city,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "%.5f, %.5f".format(lat, lng),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { onLocationSelected(city, lat, lng) }) {
                            Text("Enregistrer")
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
