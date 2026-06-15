package com.jtr.app.ui.person

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import coil.request.ImageRequest
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.ui.components.FavoriteStar
import com.jtr.app.ui.components.rememberGalleryImagePicker
import com.jtr.app.utils.getSocialIcon
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.SocialPlatform
import com.jtr.app.utils.extractSocialLinks
import com.jtr.app.utils.icon
import com.jtr.app.utils.openSocialLink
import com.jtr.app.utils.SocialLink
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    person: Person?,
    /** Catégories du profil sous forme de paires (id, nom) — badges cliquables. */
    categories: List<Pair<String, String>> = emptyList(),
    onNavigateBack: () -> Unit,
    onNavigateToPerson: (String) -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {},
    onDeleteClick: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    cityFromMap: String? = null,
    latFromMap: Double? = null,
    lngFromMap: Double? = null,
    onMapResultConsumed: () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var showPhotoZoom by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val editVm: EditPersonViewModel = viewModel()
    val isEditing by editVm.isEditing.collectAsStateWithLifecycle()
    val isLoading by editVm.isLoading.collectAsStateWithLifecycle()

    // Retour système PENDANT l'édition : annule l'édition et revient au DÉTAIL du profil
    // (même comportement que la croix de la barre et que « Enregistrer »), au lieu de
    // dépiler tout l'écran jusqu'à l'accueil. Hors édition : garde inactive → la
    // navigation arrière normale (retour au précédent) reprend.
    BackHandler(enabled = isEditing) { editVm.cancelEdit() }
    val vmFirstName by editVm.firstName.collectAsStateWithLifecycle()
    val vmLastName by editVm.lastName.collectAsStateWithLifecycle()
    val vmCity by editVm.city.collectAsStateWithLifecycle()
    val vmCityLat by editVm.cityLat.collectAsStateWithLifecycle()
    val vmCityNotify by editVm.cityNotify.collectAsStateWithLifecycle()
    val vmOrigin by editVm.origin.collectAsStateWithLifecycle()
    val vmJobTitle by editVm.jobTitle.collectAsStateWithLifecycle()
    val vmDepartment by editVm.department.collectAsStateWithLifecycle()
    val vmCompany by editVm.company.collectAsStateWithLifecycle()
    val vmLikes by editVm.likes.collectAsStateWithLifecycle()
    val vmNotes by editVm.notes.collectAsStateWithLifecycle()
    val vmNameDetails by editVm.nameDetails.collectAsStateWithLifecycle()
    val vmPhoneLines by editVm.phoneLines.collectAsStateWithLifecycle()
    val vmEmailLines by editVm.emailLines.collectAsStateWithLifecycle()
    val vmDateLines by editVm.dateLines.collectAsStateWithLifecycle()
    val vmRelationLines by editVm.relationLines.collectAsStateWithLifecycle()
    val vmRelationSuggestions by editVm.relationSuggestions.collectAsStateWithLifecycle()
    val vmPhotoUri by editVm.photoUri.collectAsStateWithLifecycle()
    val firstNameError by editVm.firstNameError.collectAsStateWithLifecycle()
    val socialLinks by editVm.socialLinks.collectAsStateWithLifecycle()
    val vmPendingPhotoUri        by editVm.pendingPhotoUri.collectAsStateWithLifecycle()

    LaunchedEffect(person?.id) { person?.id?.let { editVm.loadPerson(it) } }

    LaunchedEffect(cityFromMap) {
        val c = cityFromMap ?: return@LaunchedEffect
        editVm.onCityFromMap(c, latFromMap, lngFromMap)
        onMapResultConsumed()
    }

    // Galerie IN-APP par ALBUMS (v5.5) — l'utilisateur ne quitte pas l'application.
    val photoPicker = rememberGalleryImagePicker { uri -> pendingCropUri = uri }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val proximityBlockedMsg = stringResource(R.string.person_proximity_blocked_snackbar)
    val firstNameRequiredMsg = stringResource(R.string.save_requires_first_name)
    val locationDeniedMsg = stringResource(R.string.location_denied_settings)
    // Verrou proximité : la notif de proximité n'est activable que si les
    // notifications globales ET la proximité sont actives dans les paramètres.
    val proximityAllowed = remember {
        val p = context.getSharedPreferences("jtr_prefs", android.content.Context.MODE_PRIVATE)
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
            editVm.onCityNotifyChanged(true)
            ensureBackgroundLocation()
        } else {
            scope.launch { snackbarHostState.showSnackbar(locationDeniedMsg) }
        }
    }
    val onProximityToggle: (Boolean) -> Unit = { wanted ->
        if (wanted && ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            editVm.onCityNotifyChanged(wanted)
            if (wanted) ensureBackgroundLocation()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) stringResource(R.string.person_edit_title)
                        else person?.fullName ?: stringResource(R.string.person_not_found)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) editVm.cancelEdit() else onNavigateBack()
                    }) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Close
                                          else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) stringResource(R.string.common_cancel)
                                                 else stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        // Enregistrement SOBRE en haut à droite (v7.0.2) — remplace le FAB.
                        // La logique de sauvegarde (commitAllEdits) est strictement inchangée.
                        IconButton(onClick = {
                            if (vmFirstName.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar(firstNameRequiredMsg) }
                            }
                            editVm.commitAllEdits()
                        }) {
                            Icon(Icons.Default.Check,
                                contentDescription = stringResource(R.string.person_save),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    if (!isEditing) {
                        // Étoile favori : jaune vif si actif, toggle instantané en base.
                        IconButton(onClick = { editVm.toggleFavorite() }) {
                            val fav = person?.isFavorite == true
                            val cd = stringResource(R.string.person_favorite_toggle_cd)
                            if (fav) {
                                FavoriteStar(size = 24.dp, contentDescription = cd)
                            } else {
                                Icon(
                                    Icons.Default.StarBorder,
                                    contentDescription = cd,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        // Menu « 3 points » : Modifier / Supprimer / Informations du profil.
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.common_more_actions),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_edit)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, null,
                                        tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { menuExpanded = false; editVm.enterEditMode() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_delete)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null,
                                        tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuExpanded = false; showDeleteDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.person_info_menu)) },
                                    leadingIcon = { Icon(Icons.Default.Info, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { menuExpanded = false; showInfoDialog = true }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isEditing) MaterialTheme.colorScheme.secondaryContainer
                                     else MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = if (isEditing) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        if (isLoading || person == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center) {
                if (isLoading) CircularProgressIndicator()
                else Text(stringResource(R.string.person_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // Pas de .imePadding() ici : l'activité est en adjustResize (edge-to-edge),
                // la fenêtre se redimensionne déjà à l'ouverture du clavier. Ajouter
                // imePadding() en plus doublait l'inset et créait un vide blanc géant.
                .verticalScroll(rememberScrollState())
                .pointerInput(isEditing) {
                    if (!isEditing) detectTapGestures(onDoubleTap = { editVm.enterEditMode() })
                }
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (!isEditing) {
                Text(
                    stringResource(R.string.person_double_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.size(96.dp)) {
                // Source résolue dans un ordre STABLE, indépendant de isEditing :
                // 1) photo en attente (sélectionnée, pas encore persistée),
                // 2) photo chargée dans le ViewModel (snapshot fixe du profil),
                // 3) photo Room « live ». L'étape 2 garantit que la sauvegarde ne
                // laisse jamais l'avatar vide le temps que le Flow Room ré-émette.
                val photoSrc = vmPendingPhotoUri?.path ?: vmPhotoUri ?: person.photoUri

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .then(
                            if (photoSrc == null)
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
                        .then(if (isEditing) Modifier.blur(8.dp) else Modifier)
                        .then(
                            if (!isEditing && photoSrc != null)
                                Modifier.clickable { showPhotoZoom = true }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Avatar par défaut TOUJOURS rendu en couche de fond (initiales) :
                    // aucune recomposition / sauvegarde ne peut laisser un disque vide
                    // le temps qu'une photo (re)charge — la photo, si présente, recouvre.
                    Text(
                        text = person.initials,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (photoSrc != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photoSrc).crossfade(300).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                EditPhotoOverlay(isEditing = isEditing) {
                    photoPicker()
                }
            }

            Spacer(Modifier.height(16.dp))

            // En mode édition, le nom est édité dans ProfileFormFields (section Nom
            // épurée). En lecture, on affiche le nom complet centré.
            AnimatedContent(
                targetState = isEditing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "name_section"
            ) { editing ->
                if (editing) {
                    Spacer(Modifier.height(0.dp))
                } else {
                    // Le statut favori est porté UNIQUEMENT par l'étoile de la TopBar.
                    Text(text = person.fullName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                }
            }

            if (categories.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Badges INTERACTIFS (v5.3.4) : un tap ouvre le détail de la catégorie.
                    categories.forEach { (categoryId, name) ->
                        SuggestionChip(
                            onClick = { onNavigateToCategory(categoryId) },
                            label = { Text(name) },
                            icon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SocialLinksSection(
                links = socialLinks,
                isEditing = isEditing,
                onAddClick = { showAddLinkDialog = true },
                onRemoveClick = { editVm.removeSocialLink(it) }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (isEditing) {
                ProfileFormFields(
                    firstName = vmFirstName,
                    onFirstNameChange = { editVm.onFirstNameChanged(it) },
                    lastName = vmLastName,
                    onLastNameChange = { editVm.onLastNameChanged(it) },
                    firstNameError = firstNameError,
                    nameDetails = vmNameDetails,
                    onNameDetailsChange = { editVm.onNameDetailsChanged(it) },
                    notes = vmNotes,
                    onNotesChange = { editVm.onNotesChanged(it) },
                    likes = vmLikes,
                    onLikesChange = { editVm.onLikesChanged(it) },
                    phoneLines = vmPhoneLines,
                    onPhoneLinesChange = { editVm.onPhoneLinesChanged(it) },
                    emailLines = vmEmailLines,
                    onEmailLinesChange = { editVm.onEmailLinesChanged(it) },
                    dateLines = vmDateLines,
                    onDateLinesChange = { editVm.onDateLinesChanged(it) },
                    relationLines = vmRelationLines,
                    onRelationLinesChange = { editVm.onRelationLinesChanged(it) },
                    relationSuggestions = vmRelationSuggestions,
                    city = vmCity,
                    cityHasCoords = vmCityLat != null,
                    onCityChange = { editVm.onCityChanged(it) },
                    onNavigateToMap = onNavigateToMap,
                    cityNotify = vmCityNotify,
                    onCityNotifyChange = onProximityToggle,
                    proximityAllowed = proximityAllowed,
                    onProximityBlocked = {
                        scope.launch { snackbarHostState.showSnackbar(proximityBlockedMsg) }
                    },
                    origin = vmOrigin,
                    onOriginChange = { editVm.onOriginChanged(it) },
                    jobTitle = vmJobTitle,
                    onJobTitleChange = { editVm.onJobTitleChanged(it) },
                    department = vmDepartment,
                    onDepartmentChange = { editVm.onDepartmentChanged(it) },
                    company = vmCompany,
                    onCompanyChange = { editVm.onCompanyChanged(it) }
                )
            } else {
                // ── Mode lecture : ordre IDENTIQUE au formulaire ─────────────────
                // 2. Dates importantes — TOUTES, avec cloche si rappel actif (règle 2 max)
                val dates = person.dateLines?.takeIf { it.isNotEmpty() }
                    ?: person.birthdate?.let {
                        listOf(DynamicLine(
                            value = millisToRawDigits(it, resolveDateFormatSpec(Locale.getDefault()).order),
                            label = FieldTypes.DATE_BIRTHDAY,
                            notify = person.birthdateNotify
                        ))
                    }
                if (dates != null) DatesBlock(lines = dates)

                // 3. Relations (règle 2 max) — noms cliquables → contact lié
                val relations = person.relationLines?.filter { it.value.isNotBlank() }?.takeIf { it.isNotEmpty() }
                if (relations != null) {
                    val notFoundMsg = stringResource(R.string.relation_not_found)
                    ContactLinesBlock(
                        icon = Icons.Default.Group,
                        sectionLabel = stringResource(R.string.section_relations),
                        lines = relations,
                        types = FieldTypes.RELATION,
                        onValueClick = { name ->
                            editVm.findPersonIdByName(name) { id ->
                                if (id != null && id != person.id) onNavigateToPerson(id)
                                else Toast.makeText(context, notFoundMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // 4. Téléphones puis 5. Emails (repli scalaire pour profils legacy, règle 2 max)
                val phones = person.phoneLines?.filter { it.value.isNotBlank() }?.takeIf { it.isNotEmpty() }
                    ?: person.phoneNumber?.takeIf { it.isNotBlank() }
                        ?.let { listOf(DynamicLine(value = it, label = FieldTypes.PHONE_MOBILE)) }
                if (phones != null) {
                    ContactLinesBlock(
                        icon = Icons.Default.Phone,
                        sectionLabel = stringResource(R.string.section_phones),
                        lines = phones,
                        types = FieldTypes.PHONE,
                        // Numéro cliquable → composition d'appel native.
                        onValueClick = { number ->
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.trim()}")))
                            editVm.markAsContacted()
                        }
                    )
                }
                val emails = person.emailLines?.filter { it.value.isNotBlank() }?.takeIf { it.isNotEmpty() }
                    ?: person.email?.takeIf { it.isNotBlank() }
                        ?.let { listOf(DynamicLine(value = it, label = FieldTypes.EMAIL_HOME)) }
                if (emails != null) {
                    ContactLinesBlock(
                        icon = Icons.Default.Email,
                        sectionLabel = stringResource(R.string.section_emails),
                        lines = emails,
                        types = FieldTypes.EMAIL,
                        // Email cliquable → messagerie native.
                        onValueClick = { address ->
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${address.trim()}")))
                            editVm.markAsContacted()
                        }
                    )
                }

                // 6. Informations professionnelles — bloc compact, masqué si vide
                val at = stringResource(R.string.person_job_at)
                val jobHead = when {
                    !person.jobTitle.isNullOrBlank() && !person.company.isNullOrBlank() ->
                        "${person.jobTitle} $at ${person.company}"
                    !person.jobTitle.isNullOrBlank() -> person.jobTitle
                    !person.company.isNullOrBlank() -> person.company
                    else -> null
                }
                val jobSummary = listOfNotNull(
                    jobHead, person.department?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (jobSummary.isNotBlank()) {
                    DetailRow(icon = Icons.Default.Work,
                        label = stringResource(R.string.section_work),
                        value = jobSummary)
                }

                // 7. Origine — juste AU-DESSUS de la ville
                if (person.origin != null) {
                    DetailRow(icon = Icons.Default.Public,
                        label = stringResource(R.string.person_origin_label),
                        value = person.origin)
                }

                // 8. Ville & mini-carte
                if (person.city != null) {
                    CityDetailRow(city = person.city,
                        cityLat = person.cityLat, cityLng = person.cityLng)
                }

                // 9. Notes & 10. Ce qu'il aime — grands blocs de texte tout en bas
                if (person.notes != null || person.likes != null) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                }
                if (person.notes != null) {
                    DetailTextBlock(icon = Icons.AutoMirrored.Filled.Notes,
                        label = stringResource(R.string.person_notes_label),
                        value = person.notes)
                }
                if (person.likes != null) {
                    DetailTextBlock(icon = Icons.Default.Favorite,
                        label = stringResource(R.string.person_likes_label),
                        value = person.likes)
                }

                // Legacy : genre (retiré du formulaire), affiché discrètement en bas.
                if (person.gender != null) {
                    DetailRow(
                        icon = Icons.Default.Person,
                        label = stringResource(R.string.person_gender_label),
                        value = when (person.gender) {
                            "male"       -> stringResource(R.string.person_gender_male)
                            "female"     -> stringResource(R.string.person_gender_female)
                            "non-binary" -> stringResource(R.string.person_gender_nonbinary)
                            else         -> person.gender
                        }
                    )
                }
            }
        }
    }

    if (showPhotoZoom && !isEditing) {
        val zoomUri = vmPendingPhotoUri?.path ?: person?.photoUri
        if (zoomUri != null) {
            PhotoZoomDialog(photoUri = zoomUri, onDismiss = { showPhotoZoom = false })
        }
    }

    pendingCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            cropShape = CropShape.CIRCLE,
            onCropComplete = { croppedUri ->
                editVm.onPhotoSelected(croppedUri)
                pendingCropUri = null
            },
            onDismiss = { pendingCropUri = null }
        )
    }


    if (showAddLinkDialog) {
        AddSocialLinkDialog(
            onConfirm = { url -> editVm.addSocialLink(url) },
            onDismiss = { showAddLinkDialog = false }
        )
    }

    if (showInfoDialog && person != null) {
        val df = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.person_info_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(stringResource(R.string.person_info_created),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(df.format(Date(person.createdAt)),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                    Column {
                        Text(stringResource(R.string.person_info_updated),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(df.format(Date(person.updatedAt.takeIf { it > 0 } ?: person.createdAt)),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.person_delete_dialog_title)) },
            text = { Text(stringResource(R.string.person_delete_dialog_text)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteClick() }) {
                    Text(stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}


@Composable
private fun PhotoZoomDialog(photoUri: Any, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Échelle/translation ANIMABLES → double-tap zoom doux + recentrage fluide.
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // Taille intrinsèque de l'image chargée → dimensions réellement affichées (Fit).
    var intrinsicSize by remember { mutableStateOf<Size?>(null) }
    val density = LocalDensity.current

    // Rectangle réellement occupé par la photo (mode Fit, à l'échelle 1) — base du
    // dimensionnement de la zone tactile « photo » (le reste = noir/letterbox).
    fun dispSize(): Size {
        val c = containerSize
        if (c == IntSize.Zero) return Size.Zero
        val i = intrinsicSize
        return if (i != null && i.width > 0f && i.height > 0f) {
            val fit = minOf(c.width / i.width, c.height / i.height)
            Size(i.width * fit, i.height * fit)
        } else {
            Size(c.width.toFloat(), c.height.toFloat())
        }
    }

    // Barrières géométriques du pan à une échelle donnée : l'image ne peut JAMAIS
    // être traînée hors écran (révéler du fond noir).
    fun maxPanAt(s: Float): Offset {
        val c = containerSize
        if (c == IntSize.Zero) return Offset.Zero
        val d = dispSize()
        return Offset(
            ((d.width * s - c.width) / 2f).coerceAtLeast(0f),
            ((d.height * s - c.height) / 2f).coerceAtLeast(0f)
        )
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scope.launch {
            scale.snapTo((scale.value * zoomChange).coerceIn(1f, 5f))
            val max = maxPanAt(scale.value)
            offsetX.snapTo((offsetX.value + panChange.x).coerceIn(-max.x, max.x))
            offsetY.snapTo((offsetY.value + panChange.y).coerceIn(-max.y, max.y))
        }
    }

    // Double-tap ANIMÉ (~280 ms, easing doux) : zoom 1× → 2.5× ANCRÉ sur le point
    // touché (le point sous le doigt y reste), ou dézoom centré si déjà zoomée.
    // Ancrage identique au pinch : offset' = d·(1−k) + offset·k, d = point − centre.
    fun animateZoomToPoint(point: Offset) {
        scope.launch {
            val spec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
            if (scale.value > 1f) {
                launch { scale.animateTo(1f, spec) }
                launch { offsetX.animateTo(0f, spec) }
                launch { offsetY.animateTo(0f, spec) }
            } else {
                val target = 2.5f
                val k = target / scale.value
                val d = dispSize()
                val dx = point.x - d.width / 2f
                val dy = point.y - d.height / 2f
                val max = maxPanAt(target)
                val newOffX = (dx * (1f - k) + offsetX.value * k).coerceIn(-max.x, max.x)
                val newOffY = (dy * (1f - k) + offsetY.value * k).coerceIn(-max.y, max.y)
                launch { scale.animateTo(target, spec) }
                launch { offsetX.animateTo(newOffX, spec) }
                launch { offsetY.animateTo(newOffY, spec) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      // RTL : la visionneuse opère en pixels (zoom/pan via graphicsLayer, ancrage
      // double-tap en coordonnées locales) → on force LTR pour rester direction-
      // agnostique (gestes identiques en arabe).
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center
        ) {
            // ZONE NOIRE / letterbox : tap simple = fermeture IMMÉDIATE. Aucun
            // onDoubleTap ici → zéro délai de détection (fermeture snappy).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )

            // ZONE PHOTO : dimensionnée au rectangle Fit ; le graphicsLayer applique
            // zoom/pan ET fait suivre la zone tactile à l'image agrandie.
            val disp = dispSize()
            val photoModifier = if (disp != Size.Zero) {
                Modifier.size(
                    with(density) { disp.width.toDp() },
                    with(density) { disp.height.toDp() }
                )
            } else {
                Modifier.fillMaxSize()
            }
            Box(
                modifier = photoModifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = offsetX.value
                        translationY = offsetY.value
                    }
                    .transformable(state = transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            // Tap sur la photo : dézoom (animé) si zoomée, sinon ferme.
                            onTap = { if (scale.value > 1f) animateZoomToPoint(it) else onDismiss() },
                            // Double-tap : zoom DOUX animé, ancré sur le point touché.
                            onDoubleTap = { animateZoomToPoint(it) }
                        )
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photoUri).crossfade(200).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            intrinsicSize = state.painter.intrinsicSize
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Croix sur un scrim sombre circulaire → TOUJOURS visible, même sur une
            // photo claire/blanche.
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White
                )
            }
        }
      }
    }
}

@Composable
private fun EditPhotoOverlay(isEditing: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(visible = isEditing, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.person_edit_photo_label),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun AddSocialLinkDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    val detected by remember { derivedStateOf { extractSocialLinks(url).firstOrNull() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person_add_link_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.person_add_link_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp)
                )
                AnimatedVisibility(visible = detected != null) {
                    val platform = detected?.platform ?: return@AnimatedVisibility
                    val bgColor = Color(platform.argbColor)
                    val contentColor = if (platform == SocialPlatform.Snapchat) Color.Black else Color.White
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledIconButton(
                            onClick = {},
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = bgColor, contentColor = contentColor),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(platform.icon(), null, modifier = Modifier.size(18.dp))
                        }
                        Text(platform.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url); onDismiss() },
                enabled = url.isNotBlank()
            ) { Text(stringResource(R.string.person_add_link_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun SocialLinksSection(
    links: List<SocialLinkEntity>,
    isEditing: Boolean,
    onAddClick: () -> Unit,
    onRemoveClick: (String) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (links.isEmpty() && !isEditing) return@Column

        AnimatedContent(
            targetState = isEditing,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "social_links_content"
        ) { editing ->
            if (editing) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    links.forEach { link ->
                        val platform = SocialPlatform.all.firstOrNull {
                            it.displayName == link.platform
                        }
                        val bgColor = Color(platform?.argbColor ?: 0xFF607D8BL)
                        val contentColor = if (platform == SocialPlatform.Snapchat) Color.Black else Color.White
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = {},
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = bgColor, contentColor = contentColor),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(platform?.icon() ?: Icons.Default.Link, null,
                                    modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(link.platform,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(link.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                            IconButton(onClick = { onRemoveClick(link.id) }) {
                                Icon(Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.person_social_delete_cd),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    // Affordance d'ajout discrète « + » (v7.0.2) — plus de bouton bordé lourd.
                    TextButton(
                        onClick = onAddClick,
                        modifier = Modifier.align(Alignment.Start),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AddLink, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.person_add_social_link),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    links.forEach { link ->
                        val platform = SocialPlatform.all.firstOrNull { it.displayName == link.platform }
                        val socialLink = SocialLink(
                            platform = platform ?: SocialPlatform.Instagram,
                            url = link.url
                        )
                        Icon(
                            painter = painterResource(getSocialIcon(link.url)),
                            contentDescription = link.platform,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { openSocialLink(context, socialLink) },
                            tint = Color.Unspecified
                        )
                    }
                    FilledTonalIconButton(onClick = onAddClick, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Add,
                            contentDescription = stringResource(R.string.common_add),
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CityDetailRow(city: String, cityLat: Double?, cityLng: Double?) {
    val hasCoords = cityLat != null && cityLng != null
    var showMap by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.LocationOn, null, Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.person_city_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(city, style = MaterialTheme.typography.bodyLarge)
        }
        if (hasCoords) {
            IconButton(onClick = { showMap = !showMap }) {
                Icon(
                    if (showMap) Icons.Default.Map else Icons.Outlined.Map,
                    contentDescription = if (showMap) stringResource(R.string.person_map_hide_cd)
                                         else stringResource(R.string.person_map_cd),
                    tint = if (showMap) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (hasCoords) {
        AnimatedVisibility(visible = showMap, enter = expandVertically(), exit = shrinkVertically()) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                MapLibreMiniMap(lat = cityLat!!, lng = cityLng!!, cityName = city,
                    modifier = Modifier.fillMaxWidth().height(180.dp))
                Text("© OpenStreetMap contributors | MapLibre",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun MapLibreMiniMap(lat: Double, lng: Double, cityName: String, modifier: Modifier) {
    val context = LocalContext.current
    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context) {
        val cb = object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(l: Int) = mapView.onLowMemory()
            override fun onConfigurationChanged(c: android.content.res.Configuration) = Unit
            override fun onLowMemory() = mapView.onLowMemory()
        }
        context.registerComponentCallbacks(cb)
        onDispose { context.unregisterComponentCallbacks(cb) }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            if (lifecycle.currentState != Lifecycle.State.DESTROYED) { mapView.onStop(); mapView.onDestroy() }
        }
    }
    AndroidView(factory = { _ ->
        mapView.apply {
            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            getMapAsync { map ->
                map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 12.0))
                    @Suppress("DEPRECATION")
                    map.addMarker(org.maplibre.android.annotations.MarkerOptions()
                        .position(LatLng(lat, lng)).title(cityName))
                }
            }
        }
    }, modifier = modifier.clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)))
}

/** Libellé d'affichage d'une ligne : type connu localisé, sinon texte personnalisé. */
@Composable
private fun lineTypeLabel(types: List<TypeOption>, key: String): String {
    val res = typeLabelResOrNull(types, key)
    return if (res != null) stringResource(res) else key
}

/** Bascule discrète « Voir les X autres… / Voir moins » pour le mode lecture. */
@Composable
private fun SeeMoreToggle(hiddenCount: Int, showAll: Boolean, onToggle: () -> Unit) {
    Text(
        text = if (showAll) stringResource(R.string.person_see_less)
        else stringResource(R.string.person_see_more, hiddenCount),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { onToggle() }
            .padding(top = 2.dp)
    )
}

/**
 * Bloc lecture d'un groupe répétable : icône + libellé de section + lignes
 * « type : valeur ». Règle des 2 max : au-delà de 2 éléments, seuls les 2 premiers
 * sont visibles, le reste se déroule sur place via [SeeMoreToggle].
 */
@Composable
private fun ContactLinesBlock(
    icon: ImageVector,
    sectionLabel: String,
    lines: List<DynamicLine>,
    types: List<TypeOption>,
    onValueClick: ((String) -> Unit)? = null
) {
    var showAll by remember { mutableStateOf(false) }
    val visible = if (lines.size > 2 && !showAll) lines.take(2) else lines

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(24.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(sectionLabel, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            visible.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(lineTypeLabel(types, line.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 56.dp))
                    if (onValueClick != null) {
                        // Valeur cliquable (relation/téléphone/email) — couleur primaire,
                        // sans soulignement : l'interaction se découvre au clic.
                        Text(line.value, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).clickable { onValueClick(line.value) })
                    } else {
                        Text(line.value, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f))
                    }
                }
            }
            if (lines.size > 2) {
                SeeMoreToggle(lines.size - 2, showAll) { showAll = !showAll }
            }
        }
    }
}

/**
 * Bloc lecture des dates importantes : chaque date formatée + cloche si rappel actif.
 * Règle des 2 max comme [ContactLinesBlock]. Cas particulier : si l'unique date est
 * l'anniversaire de base, on n'affiche pas le titre de section (ligne « Anniversaire : … »).
 */
@Composable
private fun DatesBlock(lines: List<DynamicLine>) {
    val spec = remember { resolveDateFormatSpec(Locale.getDefault()) }
    val formatter = remember { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
    val rendered = remember(lines) {
        lines.mapNotNull { line ->
            val millis = rawDigitsToMillis(line.value, spec) ?: return@mapNotNull null
            Triple(line.label, formatter.format(Date(millis)), line.notify)
        }
    }
    if (rendered.isEmpty()) return

    val singleBirthday = rendered.size == 1 && rendered.first().first == FieldTypes.DATE_BIRTHDAY
    var showAll by remember { mutableStateOf(false) }
    val visible = if (rendered.size > 2 && !showAll) rendered.take(2) else rendered

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Cake, null, Modifier.size(24.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!singleBirthday) {
                Text(stringResource(R.string.section_dates),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            visible.forEach { (label, dateStr, notify) ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(lineTypeLabel(FieldTypes.DATE, label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 56.dp))
                    Text(dateStr, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    if (notify) {
                        Icon(Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.person_date_notify_cd),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (rendered.size > 2) {
                SeeMoreToggle(rendered.size - 2, showAll) { showAll = !showAll }
            }
        }
    }
}

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun DetailTextBlock(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(24.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
