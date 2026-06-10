package com.jtr.app.ui.person

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jtr.app.ui.components.rememberGalleryImagePicker
import com.jtr.app.utils.getSocialIcon
import kotlinx.coroutines.launch

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
    val likes           by viewModel.likes.collectAsStateWithLifecycle()
    val notes           by viewModel.notes.collectAsStateWithLifecycle()
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
    // Verrou proximité : activable uniquement si notifications + proximité globales actives.
    val proximityAllowed = remember {
        val p = context.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
        p.getBoolean("notifications_enabled", false) && p.getBoolean("proximity_enabled", false)
    }

    // Permission GPS demandée IMMÉDIATEMENT à l'activation du rappel de proximité ;
    // refus → message explicite orientant vers les paramètres du téléphone.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onCityNotifyChanged(true)
        else scope.launch { snackbarHostState.showSnackbar(locationDeniedMsg) }
    }
    val onProximityToggle: (Boolean) -> Unit = { wanted ->
        if (wanted && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            viewModel.onCityNotifyChanged(wanted)
        }
    }

    LaunchedEffect(cityFromMap) {
        val c = cityFromMap ?: return@LaunchedEffect
        viewModel.onCityFromMap(c, latFromMap, lngFromMap)
        onMapResultConsumed()
    }

    // Galerie native par ALBUMS (v5.3.4) — état du formulaire préservé au retour.
    val photoPicker = rememberGalleryImagePicker { uri -> viewModel.onPhotoSelected(uri) }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            OutlinedButton(
                onClick = { showAddLinkDialog = true },
                modifier = Modifier.align(Alignment.Start),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AddLink, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.person_add_social_link))
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
                notes = notes,
                onNotesChange = viewModel::onNotesChanged,
                likes = likes,
                onLikesChange = viewModel::onLikesChanged,
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    // Le ViewModel bloque déjà la sauvegarde (firstNameError) ;
                    // on double d'un message clair et actionnable.
                    if (firstName.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar(firstNameRequiredMsg) }
                    }
                    viewModel.savePerson(onSuccess = onNavigateBack)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.person_save),
                    style = MaterialTheme.typography.titleMedium)
            }
        }

        if (showAddLinkDialog) {
            AddSocialLinkDialog(
                onConfirm = { url -> viewModel.addPendingLink(url) },
                onDismiss = { showAddLinkDialog = false }
            )
        }

    }
}
