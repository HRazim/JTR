package com.jtr.app.ui.person

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.jtr.app.domain.model.Person
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    person: Person?,
    categoryName: String? = null,
    onNavigateBack: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person?.fullName ?: "Détail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (person == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center) {
                Text("Personne introuvable")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                Box(
                    modifier = Modifier.size(96.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (person.photoUri != null) {
                        AsyncImage(model = person.photoUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text(text = person.initials,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = person.fullName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)

                if (categoryName != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(categoryName) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null,
                            modifier = Modifier.size(16.dp)) }
                    )
                }

                if (person.isFavorite) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Favori", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                if (person.gender != null) {
                    DetailRow(icon = Icons.Default.Person, label = "Genre",
                        value = when (person.gender) {
                            "male" -> "Homme"; "female" -> "Femme"
                            "non-binary" -> "Non-binaire"; else -> person.gender
                        })
                }
                if (person.birthdate != null) {
                    DetailRow(icon = Icons.Default.Cake, label = "Anniversaire",
                        value = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
                            .format(Date(person.birthdate)))
                }

                // Ville + mini-carte
                if (person.city != null) {
                    CityDetailRow(
                        city = person.city,
                        cityLat = person.cityLat,
                        cityLng = person.cityLng
                    )
                }

                if (person.origin != null) {
                    DetailRow(icon = Icons.Default.Public, label = "Origine",
                        value = person.origin)
                }

                if (person.likes != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailTextBlock(icon = Icons.Default.Favorite,
                        label = "Ce qu'il/elle aime", value = person.likes)
                }
                if (person.notes != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailTextBlock(icon = Icons.Default.Notes,
                        label = "Notes", value = person.notes)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer ce contact ?") },
            text = { Text("Le contact sera placé dans la corbeille pendant 30 jours.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteClick() }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Ville + mini-carte Leaflet/OSM
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CityDetailRow(city: String, cityLat: Double?, cityLng: Double?) {
    val hasCoords = cityLat != null && cityLng != null
    var showMap by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Ville", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(city, style = MaterialTheme.typography.bodyLarge)
        }
        if (hasCoords) {
            IconButton(onClick = { showMap = !showMap }) {
                Icon(
                    imageVector = if (showMap) Icons.Default.Map else Icons.Outlined.Map,
                    contentDescription = if (showMap) "Masquer la carte" else "Voir sur la carte",
                    tint = if (showMap) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Mini-carte animée
    if (hasCoords) {
        AnimatedVisibility(
            visible = showMap,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LeafletMapView(
                    lat = cityLat!!,
                    lng = cityLng!!,
                    cityName = city,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Text(
                    text = "© OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LeafletMapView(
    lat: Double,
    lng: Double,
    cityName: String,
    modifier: Modifier = Modifier
) {
    val html = buildLeafletHtml(lat, lng, cityName)
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Base URL OSM pour résoudre les tuiles sans blocage CORS
                loadDataWithBaseURL(
                    "https://openstreetmap.org",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://openstreetmap.org", html, "text/html", "UTF-8", null
            )
        }
    )
}

private fun buildLeafletHtml(lat: Double, lng: Double, cityName: String): String = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
  <style>
    body { margin: 0; padding: 0; background: #f0f0f0; }
    #map { width: 100%; height: 100vh; }
  </style>
</head>
<body>
  <div id="map"></div>
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <script>
    var map = L.map('map', {
      center: [$lat, $lng],
      zoom: 12,
      zoomControl: false,
      dragging: false,
      scrollWheelZoom: false,
      doubleClickZoom: false,
      touchZoom: false
    });
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 18
    }).addTo(map);
    L.marker([$lat, $lng])
      .addTo(map)
      .bindPopup('${cityName.replace("'", "\\'")}')
      .openPopup();
  </script>
</body>
</html>
""".trimIndent()

// ─────────────────────────────────────────────────────────────────────────────
// Composables utilitaires
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun DetailTextBlock(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null,
            modifier = Modifier.size(24.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
