package com.jtr.app.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.data.contacts.DeviceContact
import com.jtr.app.ui.components.JtrSearchableTopAppBar

/**
 * Importation SÉLECTIVE de l'onboarding (v5.4.1) : liste alphabétique ultra-
 * fluide du répertoire natif (lignes keyées, vignettes Coil paresseuses —
 * 3 000+ contacts sans saturer la mémoire), recherche instantanée dans la
 * TopAppBar, multi-sélection par cases à cocher et compteur dynamique sur le
 * bouton de validation. Tout l'état (coches, recherche) vit dans le
 * [WelcomeViewModel] — il survit au scroll, au filtre et aux recompositions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectionScreen(
    contacts: List<DeviceContact>,
    selectedIds: Set<Long>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    var searchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            JtrSearchableTopAppBar(
                title = stringResource(R.string.contact_selection_title),
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchChange,
                searchActive = searchActive,
                onSearchActiveChange = { searchActive = it },
                searchPlaceholder = stringResource(R.string.home_search_placeholder),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Button(
                    onClick = onConfirm,
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(R.string.import_selected_count, selectedIds.size))
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(items = contacts, key = { it.id }) { contact ->
                DeviceContactRow(
                    contact = contact,
                    checked = contact.id in selectedIds,
                    onToggle = { onToggle(contact.id) }
                )
            }
        }
    }
}

/** Ligne native : case à cocher + vignette (photo ou initiale) + nom. */
@Composable
private fun DeviceContactRow(
    contact: DeviceContact,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (contact.photoUri != null) {
                AsyncImage(
                    model = contact.photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = contact.displayName.first().uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
