package com.jtr.app.ui.welcome

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R

/**
 * Écran de Bienvenue (première ouverture uniquement, flag `is_first_launch`) :
 * présentation de JTR + importation asynchrone des contacts natifs du téléphone.
 * L'importation tourne sur Dispatchers.IO (ViewModel) ; l'UI n'affiche que des
 * états réactifs (progression, succès, erreur) — jamais bloquée.
 */
@Composable
fun WelcomeScreen(
    onFinished: () -> Unit,
    viewModel: WelcomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filteredDeviceContacts by viewModel.filteredDeviceContacts.collectAsStateWithLifecycle()
    val selectedContactIds by viewModel.selectedContactIds.collectAsStateWithLifecycle()
    val contactSearch by viewModel.contactSearch.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    // Importation sélective (v5.4.1) : bascule plein écran vers la liste native.
    var selecting by remember { mutableStateOf(false) }
    // Intention portée par la demande de permission : tout importer OU choisir.
    var pendingSelectMode by remember { mutableStateOf(false) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            if (pendingSelectMode) {
                viewModel.loadDeviceContacts()
                selecting = true
            } else {
                viewModel.startImport()
            }
        } else {
            permissionDenied = true
        }
        pendingSelectMode = false
    }
    fun hasContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    // Fin d'importation → cap sur l'Accueil (le flag est déjà consommé).
    LaunchedEffect(state) {
        if (state is WelcomeUiState.Done) onFinished()
    }

    // Écran de sélection : les coches et la recherche vivent dans le ViewModel —
    // elles survivent au scroll, au filtre et aux allers-retours.
    if (selecting) {
        ContactSelectionScreen(
            contacts = filteredDeviceContacts,
            selectedIds = selectedContactIds,
            searchQuery = contactSearch,
            onSearchChange = { viewModel.setContactSearch(it) },
            onToggle = { viewModel.toggleContact(it) },
            onBack = { selecting = false },
            onConfirm = {
                selecting = false
                viewModel.startImport(selectedContactIds)
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emblème « cahier de mémoire ».
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            when (val s = state) {
                is WelcomeUiState.Importing -> {
                    // Progression déterminée dès que le total est connu.
                    if (s.total > 0) {
                        LinearProgressIndicator(
                            progress = { s.done / s.total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.welcome_importing, s.total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                else -> {
                    if (s is WelcomeUiState.Error) {
                        Text(
                            text = stringResource(R.string.welcome_import_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (permissionDenied) {
                        Text(
                            text = stringResource(R.string.contacts_permission_denied),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Button(
                        onClick = {
                            pendingSelectMode = false
                            if (hasContactsPermission()) viewModel.startImport()
                            else contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = null,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.welcome_import_button),
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Importation SÉLECTIVE : ouvre la liste native cochable.
                    OutlinedButton(
                        onClick = {
                            if (hasContactsPermission()) {
                                viewModel.loadDeviceContacts()
                                selecting = true
                            } else {
                                pendingSelectMode = true
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.ChecklistRtl, contentDescription = null,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.welcome_select_button))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        viewModel.markOnboardingComplete()
                        onFinished()
                    }) {
                        Text(stringResource(R.string.welcome_skip))
                    }
                }
            }
        }
    }
}
