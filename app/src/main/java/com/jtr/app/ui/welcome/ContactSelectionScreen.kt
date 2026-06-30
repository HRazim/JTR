package com.jtr.app.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.data.contacts.CAP_ADDRESS
import com.jtr.app.data.contacts.CAP_BIRTHDAY
import com.jtr.app.data.contacts.CAP_COMPANY
import com.jtr.app.data.contacts.CAP_DATE
import com.jtr.app.data.contacts.CAP_EMAIL
import com.jtr.app.data.contacts.CAP_NOTE
import com.jtr.app.data.contacts.CAP_PHONE
import com.jtr.app.data.contacts.CAP_PHOTO
import com.jtr.app.data.contacts.CAP_RELATION
import com.jtr.app.data.contacts.CAP_WEBSITE
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
                    Text(pluralStringResource(R.plurals.import_selected_count, selectedIds.size, selectedIds.size))
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
        // v7.1.41 (B6) — nom + rangée d'icônes « capacités » (ce qui sera importé). Le nom reste
        // sur une ligne (ellipsis) ; les chips s'enroulent dessous via FlowRow si nombreuses.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CapabilityChips(capabilities = contact.capabilities)
        }
    }
}

/** v7.1.41 (B6) — descripteur d'une chip : bit, icône Material, libellé a11y (string réutilisée). */
private data class CapabilityChip(
    val bit: Int,
    val icon: ImageVector,
    @StringRes val labelRes: Int
)

/**
 * v7.1.41 (B6) — ordre d'affichage des chips de capacité. Libellés `contentDescription`
 * RÉUTILISÉS de chaînes existantes (0 nouvelle string) → TalkBack énonce la capacité.
 */
private val CAPABILITY_CHIPS: List<CapabilityChip> = listOf(
    CapabilityChip(CAP_PHONE, Icons.Default.Phone, R.string.section_phones),
    CapabilityChip(CAP_EMAIL, Icons.Default.Email, R.string.section_emails),
    CapabilityChip(CAP_PHOTO, Icons.Default.PhotoLibrary, R.string.common_photo),
    CapabilityChip(CAP_BIRTHDAY, Icons.Default.Cake, R.string.date_type_birthday),
    CapabilityChip(CAP_DATE, Icons.Default.Event, R.string.section_dates),
    CapabilityChip(CAP_ADDRESS, Icons.Default.Place, R.string.note_section_address),
    CapabilityChip(CAP_WEBSITE, Icons.Default.Language, R.string.person_social_links_title),
    CapabilityChip(CAP_COMPANY, Icons.Default.Business, R.string.section_work),
    CapabilityChip(CAP_RELATION, Icons.Default.Group, R.string.section_relations),
    CapabilityChip(CAP_NOTE, Icons.AutoMirrored.Filled.Notes, R.string.note_section_default_notes)
)

/**
 * v7.1.41 (B6) — petites icônes indicatives de ce que contient le contact (Tél · Email · Photo ·
 * 🎂 · 📅 · Adresse · Site · Société · Relation · Note). Purement informatif. FlowRow → miroir RTL
 * automatique et enroulement si nombreuses. Rien rendu si [capabilities] est vide.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapabilityChips(capabilities: Int, modifier: Modifier = Modifier) {
    if (capabilities == 0) return
    FlowRow(
        modifier = modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CAPABILITY_CHIPS.forEach { chip ->
            if (capabilities and chip.bit != 0) {
                Icon(
                    imageVector = chip.icon,
                    contentDescription = stringResource(chip.labelRes),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
