package com.jtr.app.ui.person

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.domain.model.NOTE_ICON_NOTES
import com.jtr.app.domain.model.NoteSection
import com.jtr.app.ui.components.rememberGalleryImagePicker
import com.jtr.app.utils.LocationUtils
import com.jtr.app.utils.getSocialIcon
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    cityFromMap: String? = null,
    latFromMap: Double? = null,
    lngFromMap: Double? = null,
    onMapResultConsumed: () -> Unit = {},
    viewModel: AddPersonViewModel = viewModel()
) {
    val firstName       by viewModel.firstName.collectAsStateWithLifecycle()
    val lastName        by viewModel.lastName.collectAsStateWithLifecycle()
    val city            by viewModel.city.collectAsStateWithLifecycle()
    val cityLat         by viewModel.cityLat.collectAsStateWithLifecycle()
    val cityNotify      by viewModel.cityNotify.collectAsStateWithLifecycle()
    val origin          by viewModel.origin.collectAsStateWithLifecycle()
    val jobTitle        by viewModel.jobTitle.collectAsStateWithLifecycle()
    val department      by viewModel.department.collectAsStateWithLifecycle()
    val company         by viewModel.company.collectAsStateWithLifecycle()
    val noteSections    by viewModel.noteSections.collectAsStateWithLifecycle()
    val nameDetails     by viewModel.nameDetails.collectAsStateWithLifecycle()
    val phoneLines      by viewModel.phoneLines.collectAsStateWithLifecycle()
    val emailLines      by viewModel.emailLines.collectAsStateWithLifecycle()
    val dateLines       by viewModel.dateLines.collectAsStateWithLifecycle()
    val relationLines   by viewModel.relationLines.collectAsStateWithLifecycle()
    val relationSuggestions by viewModel.relationSuggestions.collectAsStateWithLifecycle()
    val photoUri        by viewModel.photoUri.collectAsStateWithLifecycle()
    val firstNameError  by viewModel.firstNameError.collectAsStateWithLifecycle()
    val pendingLinks    by viewModel.pendingLinks.collectAsStateWithLifecycle()

    var showAddLinkDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val proximityBlockedMsg = stringResource(R.string.person_proximity_blocked_snackbar)
    val firstNameRequiredMsg = stringResource(R.string.save_requires_first_name)
    val locationDeniedMsg = stringResource(R.string.location_denied_settings)
    val locationOffMsg = stringResource(R.string.location_off_settings)
    val dateInvalidMsg = stringResource(R.string.person_date_year_invalid)
    val dateSpec = remember { resolveDateFormatSpec(Locale.getDefault()) }
    // Verrou proximité : activable uniquement si notifications + proximité globales actives.
    val proximityAllowed = remember {
        val p = context.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
        p.getBoolean("notifications_enabled", true) && p.getBoolean("proximity_enabled", false)
    }

    // Permission GPS demandée IMMÉDIATEMENT à l'activation du rappel de proximité ;
    // refus → message explicite orientant vers les paramètres du téléphone.
    // ÉTAPE 2 (Android 10+, Moteur de Proximité v5.4) : la détection en tâche de
    // fond exige ACCESS_BACKGROUND_LOCATION — dialogue explicatif AVANT la demande
    // (exigence Google Play), déclenché une fois la permission fine accordée.
    var showBackgroundRationale by remember { mutableStateOf(false) }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) scope.launch { snackbarHostState.showSnackbar(locationDeniedMsg) }
    }
    fun ensureBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showBackgroundRationale = true
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Permission accordée : encore faut-il que le SERVICE de localisation soit actif.
            if (LocationUtils.isLocationEnabled(context)) {
                viewModel.onCityNotifyChanged(true)
                ensureBackgroundLocation()
            } else {
                scope.launch { snackbarHostState.showSnackbar(locationOffMsg) }
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar(locationDeniedMsg) }
        }
    }
    // Active la proximité seulement si la localisation est réellement utilisable (permission
    // ET service système) ; sinon informe SANS rediriger (l'édition n'est pas interrompue).
    val onProximityToggle: (Boolean) -> Unit = { wanted ->
        when {
            !wanted -> viewModel.onCityNotifyChanged(false)
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            !LocationUtils.isLocationEnabled(context) ->
                scope.launch { snackbarHostState.showSnackbar(locationOffMsg) }
            else -> {
                viewModel.onCityNotifyChanged(true)
                ensureBackgroundLocation()
            }
        }
    }

    if (showBackgroundRationale) {
        AlertDialog(
            onDismissRequest = { showBackgroundRationale = false },
            icon = { Icon(Icons.Default.LocationOn, null,
                tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.settings_location_bg_title)) },
            text = { Text(stringResource(R.string.settings_location_bg_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundRationale = false
                    backgroundPermissionLauncher.launch(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text(stringResource(R.string.permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundRationale = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    LaunchedEffect(cityFromMap) {
        val c = cityFromMap ?: return@LaunchedEffect
        viewModel.onCityFromMap(c, latFromMap, lngFromMap)
        onMapResultConsumed()
    }

    // Nouveau contact : démarre avec UNE section « Notes » par défaut (titre localisé,
    // semé ici pour respecter la langue in-app). Idempotent (ne re-sème pas si non vide).
    val defaultNotesTitle = stringResource(R.string.note_section_default_notes)
    LaunchedEffect(Unit) {
        if (viewModel.noteSections.value.isEmpty()) {
            viewModel.onNoteSectionsChanged(
                listOf(NoteSection(title = defaultNotesTitle, iconKey = NOTE_ICON_NOTES, order = 0))
            )
        }
    }

    // Galerie IN-APP par ALBUMS (v5.5) — l'utilisateur ne quitte pas l'application.
    // L'URI choisie passe TOUJOURS par le recadrage (cercle = photo de profil)
    // AVANT d'être appliquée. pendingCropUri (présence) arme le dialogue à chaque
    // sélection, même URI identique ; la feuille s'est déjà fermée → pas de course.
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberGalleryImagePicker { uri -> pendingCropUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.person_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // Enregistrement SOBRE en haut à droite (v7.0.2) — remplace le gros
                    // bouton plein-largeur du bas. La logique de sauvegarde est inchangée.
                    IconButton(onClick = {
                        // Le ViewModel bloque déjà la sauvegarde (nom requis / date invalide) ;
                        // on double d'un message clair indiquant ce qui empêche d'enregistrer.
                        when {
                            firstName.isBlank() ->
                                scope.launch { snackbarHostState.showSnackbar(firstNameRequiredMsg) }
                            dateLines.any { !isDateLineValid(it.value, dateSpec) } ->
                                scope.launch { snackbarHostState.showSnackbar(dateInvalidMsg) }
                        }
                        viewModel.savePerson(onSuccess = onNavigateBack)
                    }) {
                        Icon(Icons.Default.Check,
                            contentDescription = stringResource(R.string.person_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // Pas de .imePadding() ici : l'activité est en adjustResize (edge-to-edge),
                // la fenêtre se redimensionne déjà à l'ouverture du clavier. Ajouter
                // imePadding() en plus doublait l'inset et créait un vide blanc géant.
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Photo ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .then(
                        if (photoUri == null)
                            Modifier.background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                        else Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    )
                    .clickable { photoPicker() },
                contentAlignment = Alignment.Center
            ) {
                if (photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoUri).crossfade(300).build(),
                        contentDescription = stringResource(R.string.person_photo_cd),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.common_photo),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White)
                    }
                }
            }

            HorizontalDivider()

            // ── Réseaux sociaux ──────────────────────────────────────────────
            Text(
                stringResource(R.string.person_social_links_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
            )
            if (pendingLinks.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pendingLinks.forEach { link ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(getSocialIcon(link.url)),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color.Unspecified
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    link.platform,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    link.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.removePendingLink(link.url) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.person_social_delete_cd),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            // Affordance d'ajout discrète « + » (v7.0.2) — plus de bouton bordé lourd.
            TextButton(
                onClick = { showAddLinkDialog = true },
                modifier = Modifier.align(Alignment.Start),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.AddLink, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.person_add_social_link),
                    style = MaterialTheme.typography.labelLarge)
            }

            HorizontalDivider()

            // ── Notes / Likes prioritaires + section repliable (PARTAGÉ avec l'édition) ──
            ProfileFormFields(
                firstName = firstName,
                onFirstNameChange = viewModel::onFirstNameChanged,
                lastName = lastName,
                onLastNameChange = viewModel::onLastNameChanged,
                firstNameError = firstNameError,
                nameDetails = nameDetails,
                onNameDetailsChange = viewModel::onNameDetailsChanged,
                noteSections = noteSections,
                onNoteSectionsChange = viewModel::onNoteSectionsChanged,
                phoneLines = phoneLines,
                onPhoneLinesChange = viewModel::onPhoneLinesChanged,
                emailLines = emailLines,
                onEmailLinesChange = viewModel::onEmailLinesChanged,
                dateLines = dateLines,
                onDateLinesChange = viewModel::onDateLinesChanged,
                relationLines = relationLines,
                onRelationLinesChange = viewModel::onRelationLinesChanged,
                relationSuggestions = relationSuggestions,
                city = city,
                cityHasCoords = cityLat != null,
                onCityChange = viewModel::onCityChanged,
                onNavigateToMap = onNavigateToMap,
                cityNotify = cityNotify,
                onCityNotifyChange = onProximityToggle,
                proximityAllowed = proximityAllowed,
                onProximityBlocked = {
                    scope.launch { snackbarHostState.showSnackbar(proximityBlockedMsg) }
                },
                origin = origin,
                onOriginChange = viewModel::onOriginChanged,
                jobTitle = jobTitle,
                onJobTitleChange = viewModel::onJobTitleChanged,
                department = department,
                onDepartmentChange = viewModel::onDepartmentChanged,
                company = company,
                onCompanyChange = viewModel::onCompanyChanged
            )

            // Enregistrement déplacé en haut à droite (v7.0.2) — plus de gros bouton bas.
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showAddLinkDialog) {
            AddSocialLinkDialog(
                onConfirm = { url -> viewModel.addPendingLink(url) },
                onDismiss = { showAddLinkDialog = false }
            )
        }

        pendingCropUri?.let { uri ->
            ImageCropDialog(
                sourceUri = uri,
                cropShape = CropShape.CIRCLE,
                onCropComplete = { croppedUri ->
                    viewModel.onPhotoSelected(croppedUri)
                    pendingCropUri = null
                },
                onDismiss = { pendingCropUri = null }
            )
        }
    }
}
