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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import coil.request.ImageRequest
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.ui.components.FavoriteStar
import com.jtr.app.ui.components.JtrAlertDialog
import com.jtr.app.ui.components.PhotoZoomDialog
import com.jtr.app.ui.components.rememberSystemPhotoPicker
import com.jtr.app.utils.DateCanonical
import com.jtr.app.utils.LocationUtils
import com.jtr.app.utils.getSocialIcon
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtr.app.data.repository.GalleryRepository
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.launch
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.domain.model.combinedText
import com.jtr.app.domain.model.deriveNoteSections
import com.jtr.app.domain.model.effectiveNoteSections
import com.jtr.app.domain.model.splitCombinedText
import com.jtr.app.utils.SocialPlatform
import com.jtr.app.utils.extractSocialLinks
import com.jtr.app.utils.openSocialLink
import com.jtr.app.utils.SocialLink
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // FlowRow (badges de catégories)
@Composable
fun PersonDetailScreen(
    person: Person?,
    onNavigateBack: () -> Unit,
    onNavigateToPerson: (String) -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {},
    onDeleteClick: () -> Unit,
    onNavigateToMap: () -> Unit = {},
    // v7.1.89 — carré « galerie » à côté de l'avatar → page galerie du contact.
    onNavigateToGallery: (String) -> Unit = {},
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
    var showCategoryDialog by remember { mutableStateOf(false) }

    val editVm: EditPersonViewModel = viewModel()
    val isEditing by editVm.isEditing.collectAsStateWithLifecycle()
    val isLoading by editVm.isLoading.collectAsStateWithLifecycle()

    // Catégories de la personne (v7.1.5) : source RÉACTIVE unique (table de jointure) pour
    // les badges de la fiche ET le sélecteur « Gérer les catégories ». Remplace le
    // chargement one-shot de la navigation → les chips se mettent à jour sans recharger.
    val categoriesVm: PersonCategoriesViewModel = viewModel()
    val categoryChips by categoriesVm.personCategoryChips.collectAsStateWithLifecycle()
    // v7.1.71 — ids des catégories ACTIVES du contact (exclut la corbeille) : pilote le libellé
    // « Ajouter/Gérer » ET le compteur du dialogue, pour ne jamais compter une catégorie supprimée.
    val memberCategoryIds by categoriesVm.activeMemberCategoryIds.collectAsStateWithLifecycle()
    val allCategories by categoriesVm.allCategories.collectAsStateWithLifecycle()
    val categoryGroups by categoriesVm.groups.collectAsStateWithLifecycle()

    // Retour système PENDANT l'édition (v7.1.4) : SAUVEGARDE les modifications (flush) et revient
    // au DÉTAIL du profil — avec l'auto-save, quitter ne perd plus rien (plus d'annulation). Hors
    // édition : garde inactive → la navigation arrière normale (retour au précédent) reprend.
    BackHandler(enabled = isEditing) { editVm.exitEdit() }
    val vmFirstName by editVm.firstName.collectAsStateWithLifecycle()
    val vmLastName by editVm.lastName.collectAsStateWithLifecycle()
    val vmCity by editVm.city.collectAsStateWithLifecycle()
    val vmCityLat by editVm.cityLat.collectAsStateWithLifecycle()
    val vmCityNotify by editVm.cityNotify.collectAsStateWithLifecycle()
    val vmOrigin by editVm.origin.collectAsStateWithLifecycle()
    val vmJobTitle by editVm.jobTitle.collectAsStateWithLifecycle()
    val vmDepartment by editVm.department.collectAsStateWithLifecycle()
    val vmCompany by editVm.company.collectAsStateWithLifecycle()
    val vmNoteSections by editVm.noteSections.collectAsStateWithLifecycle()
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

    LaunchedEffect(person?.id) {
        person?.id?.let {
            editVm.loadPerson(it)
            categoriesVm.bind(it)
        }
    }

    LaunchedEffect(cityFromMap) {
        val c = cityFromMap ?: return@LaunchedEffect
        editVm.onCityFromMap(c, latFromMap, lngFromMap)
        onMapResultConsumed()
    }

    // Titres par défaut LOCALISÉS (langue in-app) des sections issues du backfill legacy.
    val notesTitle = stringResource(R.string.note_section_default_notes)
    val likesTitle = stringResource(R.string.person_likes_label)
    // À l'entrée en édition d'un profil LEGACY (sections vides mais notes/likes hérités),
    // sème la conversion sans perte — une seule fois (garde « liste vide »).
    LaunchedEffect(person?.id, isEditing) {
        if (isEditing && editVm.noteSections.value.isEmpty()) {
            val derived = deriveNoteSections(person?.notes, person?.likes, notesTitle, likesTitle)
            if (derived.isNotEmpty()) editVm.onNoteSectionsChanged(derived)
        }
    }

    // Photo Picker système (v7.1.66) — aucune permission d'accès à la photothèque.
    val photoPicker = rememberSystemPhotoPicker { uri -> pendingCropUri = uri }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val proximityBlockedMsg = stringResource(R.string.person_proximity_blocked_snackbar)
    val firstNameRequiredMsg = stringResource(R.string.save_requires_first_name)
    val locationDeniedMsg = stringResource(R.string.location_denied_settings)
    val locationOffMsg = stringResource(R.string.location_off_settings)
    val dateInvalidMsg = stringResource(R.string.person_date_year_invalid)
    val dateSpec = remember { resolveDateFormatSpec(java.util.Locale.getDefault()) }
    // État du mode réordonnancement des notes, hissé pour rendre le footer au niveau écran.
    val noteReorderState = rememberNoteReorderState()
    // v7.1.58 — le ScrollState était créé EN LIGNE : rien ne pouvait le piloter. Nommé ici, il
    // alimente le défilement auto de « plus / moins d'informations » (cf. MoreInfoScrollState).
    val scrollState = rememberScrollState()
    val moreInfoScroll = rememberMoreInfoScroll(scrollState)
    // Sortie du mode édition → réinitialise le footer de réordonnancement ET le pilote de
    // défilement : ProfileFormFields quitte la composition, son `showMore` local repart à false.
    LaunchedEffect(isEditing) {
        if (!isEditing) {
            noteReorderState.reset()
            moreInfoScroll.reset()
        }
    }
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
            // Permission accordée : encore faut-il que le SERVICE de localisation soit actif.
            if (LocationUtils.isLocationEnabled(context)) {
                editVm.onCityNotifyChanged(true)
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
            !wanted -> editVm.onCityNotifyChanged(false)
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ->
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            !LocationUtils.isLocationEnabled(context) ->
                scope.launch { snackbarHostState.showSnackbar(locationOffMsg) }
            else -> {
                editVm.onCityNotifyChanged(true)
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
                    // R+ : la requête runtime est un no-op → redirection Réglages (logique
                    // partagée avec le flux Paramètres via LocationUtils, plus de divergence).
                    LocationUtils.requestBackgroundLocation(context) {
                        backgroundPermissionLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
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
                    // v7.1.4 — flèche retour (auto-mirrored RTL) en édition AUSSI : avec l'auto-save,
                    // ce bouton SAUVEGARDE (flush) et sort, comme le retour Android. L'icône ✗
                    // suggérait à tort « abandonner » → flèche cohérente. Comportement inchangé.
                    IconButton(onClick = {
                        if (isEditing) editVm.exitEdit() else onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        // Enregistrement SOBRE en haut à droite (v7.0.2) — remplace le FAB.
                        // La logique de sauvegarde (commitAllEdits) est strictement inchangée.
                        IconButton(onClick = {
                            // commitAllEdits bloque déjà (nom requis / date invalide) ;
                            // on double d'un message clair sur ce qui empêche d'enregistrer.
                            when {
                                vmFirstName.isBlank() ->
                                    scope.launch { snackbarHostState.showSnackbar(firstNameRequiredMsg) }
                                vmDateLines.any { !isDateLineValid(it.value, dateSpec, it.label) } ->
                                    scope.launch { snackbarHostState.showSnackbar(dateInvalidMsg) }
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
                                // Libellé RÉACTIF : « Ajouter à une catégorie » si la personne
                                // n'en a aucune, sinon « Gérer les catégories » (v7.1.5).
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(
                                            if (memberCategoryIds.isEmpty())
                                                R.string.person_add_to_category
                                            else R.string.person_manage_categories
                                        ))
                                    },
                                    leadingIcon = { Icon(Icons.Default.Folder, null,
                                        tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { menuExpanded = false; showCategoryDialog = true }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Inset IME appliqué EXACTEMENT UNE FOIS (v7.1.1) : app edge-to-edge (targetSdk 35) → la
        // fenêtre ne se redimensionne pas, le clavier est un inset. On l'ajoute aux insets du
        // Scaffold (systemBars ∪ ime) → paddingValues intègre le clavier ; le conteneur défilant
        // ne fait QUE .padding(pv) (aucun imePadding en plus) → viewport réduit d'exactement la
        // hauteur du clavier (ni texte sous le clavier, ni grand vide).
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)
    ) { paddingValues ->

        if (isLoading || person == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center) {
                if (isLoading) CircularProgressIndicator()
                else Text(stringResource(R.string.person_not_found))
            }
            return@Scaffold
        }

        // Box racine de l'écran : ancre le footer de réordonnancement des notes en bas
        // (align BottomCenter), au-dessus du contenu défilant, calé au ras des touches.
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // paddingValues intègre DÉJÀ l'inset clavier (Scaffold.contentWindowInsets =
                // systemBars ∪ ime ci-dessus) → le viewport se réduit d'exactement la hauteur du
                // clavier. PAS de imePadding/consumeWindowInsets ici (sinon double inset = vide).
                // L'auto-scroll repose sur BringIntoView (focus + curseur du TextField).
                .padding(paddingValues)
                // v7.1.58 — AVANT verticalScroll : le nœud reste HORS du défilement, il mesure
                // donc le VIEWPORT (référence fixe) et non le contenu qui glisse.
                .then(moreInfoScroll.viewportModifier)
                .verticalScroll(scrollState)
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

            // v7.1.89 — la galerie du contact alimente À LA FOIS la pastille (nombre) et la
            // vignette du carré (image la plus récente). Une seule observation : la liste est
            // triée position/createdAt croissants, donc la DERNIÈRE entrée est la plus récente.
            val galleryContext = LocalContext.current
            val galleryImages by remember(person.id) {
                GalleryRepository(galleryContext).observeByPerson(person.id)
            }.collectAsStateWithLifecycle(initialValue = emptyList())
            val galleryCount = galleryImages.size
            val galleryLatestPath = galleryImages.lastOrNull()?.path

            // v7.1.89 — l'avatar est encadré par un espaceur et le carré « galerie » de MÊME
            // largeur : le carré apparaît à droite SANS décentrer l'avatar. Le carré est un
            // FRÈRE du Box de 96 dp, donc l'EditPhotoOverlay (qui couvre tout le disque en
            // édition) ne le masque pas — il reste accessible dans les deux modes.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(GALLERY_SQUARE_SIZE + 12.dp))

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
                        // v7.1.119 (G3b-3) — fin du traitement lourd du mode édition : plus de
                        // flou, plus de voile ; le disque reste NET et devient la cible du
                        // sélecteur (même geste qu'avant : taper l'avatar change la photo),
                        // l'affordance visuelle est le badge en coin façon v7.1.79.
                        .then(
                            when {
                                isEditing -> Modifier.clickable { photoPicker() }
                                photoSrc != null -> Modifier.clickable { showPhotoZoom = true }
                                else -> Modifier
                            }
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

                EditPhotoBadge(
                    isEditing = isEditing,
                    hasPhoto = photoSrc != null,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) { photoPicker() }
            }

                Spacer(Modifier.width(12.dp))
                GallerySquare(
                    count = galleryCount,
                    latestImagePath = galleryLatestPath,
                    personName = person.fullName,
                    onClick = { onNavigateToGallery(person.id) }
                )
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

            // v7.1.74 — retour à la disposition d'AVANT v7.1.67 (demande HR) : badges et
            // réseaux rendus ICI dans les DEUX modes. En ÉDITION (le nom s'édite dans
            // ProfileFormFields, rendu plus bas), ils apparaissent donc AU-DESSUS du
            // prénom : photo → catégorie → liens → nom → notes. En LECTURE, rien ne
            // change (le nom, affiché juste au-dessus, reste premier).
            // v7.1.76 (Lot 1, harmonisé) — point d'entrée DIRECT « + catégorie », MÊME
            // matrice de visibilité que les liens (SocialLinksSection) : lecture sans
            // catégorie → rien, chips → petit « + » au bout, édition → affordance texte.
            // La rangée gère sa marge haute elle-même (aucun Spacer fantôme à vide).
            CategoryChipsRow(
                categoryChips = categoryChips,
                isEditing = isEditing,
                onNavigateToCategory = onNavigateToCategory,
                onAddCategoryClick = { showCategoryDialog = true }
            )

            Spacer(Modifier.height(12.dp))
            SocialLinksSection(
                links = socialLinks,
                isEditing = isEditing,
                onAddClick = { showAddLinkDialog = true },
                onRemoveClick = { editVm.removeSocialLink(it) }
            )

            // v7.1.118 — en ÉDITION, le filet entre réseaux sociaux et formulaire reste
            // parti (même traitement que la création) : la respiration seule sépare.
            // v7.1.124 — en LECTURE il revient, mais SEULEMENT s'il sépare deux blocs
            // réellement remplis ; l'espace de tête est donc décidé par chaque mode.
            if (isEditing) {
                Spacer(Modifier.height(28.dp))
                ProfileFormFields(
                    firstName = vmFirstName,
                    onFirstNameChange = { editVm.onFirstNameChanged(it) },
                    lastName = vmLastName,
                    onLastNameChange = { editVm.onLastNameChanged(it) },
                    firstNameError = firstNameError,
                    nameDetails = vmNameDetails,
                    onNameDetailsChange = { editVm.onNameDetailsChanged(it) },
                    noteSections = vmNoteSections,
                    onNoteSectionsChange = { editVm.onNoteSectionsChanged(it) },
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
                    onCompanyChange = { editVm.onCompanyChanged(it) },
                    noteReorderState = noteReorderState,
                    onExpandedChange = moreInfoScroll.onExpandedChange,
                    onToggleTopInRoot = moreInfoScroll.onToggleTopInRoot
                )
            } else {
                // ── Mode lecture : ordre IDENTIQUE au formulaire ─────────────────
                // v7.1.124 — le contenu de CHAQUE bloc est résolu AVANT de dessiner quoi
                // que ce soit : un filet ne se pose qu'entre deux blocs voisins REMPLIS,
                // ce qui suppose de savoir si le suivant a du contenu. Les trois blocs de
                // la fiche sont, dans l'ordre : l'en-tête (photo, nom, catégories, liens —
                // TOUJOURS présent), les informations, puis les sections de notes.

                // 2. Dates importantes — TOUTES, avec cloche si rappel actif (règle 2 max)
                val dates = person.dateLines?.takeIf { it.isNotEmpty() }
                    ?: person.birthdate?.let {
                        listOf(DynamicLine(
                            value = DateCanonical.millisToIso(it),
                            label = FieldTypes.DATE_BIRTHDAY,
                            notify = person.birthdateNotify
                        ))
                    }
                // 3. Relations (règle 2 max) — cliquables → contact lié PAR IDENTIFIANT (v7.1.6).
                // La navigation utilise linkedPersonId (repli SANS AMBIGUÏTÉ par nom pour l'hérité) :
                // jamais d'ouverture devinée. Nom ambigu → « à vérifier » ; introuvable → message.
                val relations = person.relationLines?.filter { it.value.isNotBlank() }?.takeIf { it.isNotEmpty() }
                // 4. Téléphones puis 5. Emails (repli scalaire pour profils legacy, règle 2 max)
                val phones = person.phoneLines?.filter { it.value.isNotBlank() }?.takeIf { it.isNotEmpty() }
                    ?: person.phoneNumber?.takeIf { it.isNotBlank() }
                        ?.let { listOf(DynamicLine(value = it, label = FieldTypes.PHONE_MOBILE)) }
                val emails = person.emailLines?.filter { it.value.isNotBlank() }?.takeIf { it.isNotEmpty() }
                    ?: person.email?.takeIf { it.isNotBlank() }
                        ?.let { listOf(DynamicLine(value = it, label = FieldTypes.EMAIL_HOME)) }
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

                // Le bloc « informations » est rempli dès qu'une seule de ses lignes l'est.
                // Le genre hérité (rendu tout en bas, après les notes) en est EXCLU : le
                // compter ici poserait un filet en tête alors que son contenu arrive après
                // les notes — soit, pour une fiche sans informations, deux filets collés.
                val hasInfoBlock = dates != null || relations != null || phones != null ||
                    emails != null || jobSummary.isNotBlank() ||
                    person.origin != null || person.city != null

                // Filet en-tête ↔ informations. L'en-tête ne peut pas être vide, donc ce
                // filet sépare toujours deux blocs remplis ; sans informations, aucun filet
                // ici et c'est celui des notes qui jouera la séparation (jamais les deux).
                if (hasInfoBlock) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                } else {
                    Spacer(Modifier.height(28.dp))
                }

                if (dates != null) DatesBlock(lines = dates)

                if (relations != null) {
                    val notFoundMsg = stringResource(R.string.relation_not_found)
                    val ambiguousMsg = stringResource(R.string.relation_ambiguous_toast)
                    ContactLinesBlock(
                        icon = Icons.Default.Group,
                        sectionLabel = stringResource(R.string.section_relations),
                        lines = relations,
                        types = FieldTypes.RELATION,
                        onLineClick = { line ->
                            editVm.resolveRelationTarget(line) { target ->
                                when (target) {
                                    is RelationTarget.Resolved ->
                                        if (target.personId != person.id) onNavigateToPerson(target.personId)
                                    RelationTarget.Ambiguous ->
                                        Toast.makeText(context, ambiguousMsg, Toast.LENGTH_SHORT).show()
                                    RelationTarget.NotFound ->
                                        Toast.makeText(context, notFoundMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                if (phones != null) {
                    ContactLinesBlock(
                        icon = Icons.Default.Phone,
                        sectionLabel = stringResource(R.string.section_phones),
                        lines = phones,
                        types = FieldTypes.PHONE,
                        // Numéro cliquable → composition d'appel native.
                        onLineClick = { line ->
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${line.value.trim()}")))
                            editVm.markAsContacted()
                        }
                    )
                }
                if (emails != null) {
                    ContactLinesBlock(
                        icon = Icons.Default.Email,
                        sectionLabel = stringResource(R.string.section_emails),
                        lines = emails,
                        types = FieldTypes.EMAIL,
                        // Email cliquable → messagerie native.
                        onLineClick = { line ->
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${line.value.trim()}")))
                            editVm.markAsContacted()
                        }
                    )
                }

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

                // 9. Sections de notes — grands blocs de texte tout en bas (v7.0.3).
                // Sections persistées, ou conversion sans perte des notes héritées (legacy).
                // On masque les sections vides en lecture.
                // v7.1.75 — « 1re ligne = titre » : le couple (titre affiché, corps) est
                // CALCULÉ au rendu via combined/split (dérivation pure, AUCUNE écriture).
                // Filtre sur le texte COMBINÉ : les sections « titre seul » (contenu vide),
                // invisibles avant, deviennent visibles — une ligne en style titre (tranché HR).
                val readSections = person.effectiveNoteSections(notesTitle, likesTitle)
                    .filter { it.combinedText().isNotBlank() }
                if (readSections.isNotEmpty()) {
                    // Second intervalle de la même règle : ce filet sépare les notes du
                    // bloc rempli qui précède — les informations, ou l'en-tête à défaut.
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    readSections.forEach { s ->
                        val (displayTitle, displayBody) = splitCombinedText(s.combinedText())
                        DetailTextBlock(
                            icon = NoteIcons.icon(s.iconKey),
                            label = displayTitle,
                            value = displayBody
                        )
                    }
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

        // Footer de réordonnancement des notes — ancré en bas de l'ÉCRAN (v7.0.7), visible
        // uniquement en édition ; calé au ras des touches via NoteReorderFooter (plus de Popup).
        if (isEditing) {
            NoteReorderFooter(
                state = noteReorderState,
                sections = vmNoteSections,
                onSectionsChange = { editVm.onNoteSectionsChanged(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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

    if (showCategoryDialog && person != null) {
        ManageCategoriesDialog(
            categories = allCategories,
            groups = categoryGroups,
            memberIds = memberCategoryIds,
            onToggle = { id, inCat -> categoriesVm.setInCategory(id, inCat) },
            onCreateCategory = { name, color, imagePath ->
                categoriesVm.createCategoryAndAssign(name, color, imagePath)
            },
            onDismiss = { showCategoryDialog = false }
        )
    }

    if (showInfoDialog && person != null) {
        // v7.1.30 — keyé sur la locale courante (cf. DatesBlock) : jamais de format périmé.
        // v7.1.48 — formatage délégué à [formatDateTimeLong] (squelettes OS « yMMMMd » + « jm ») :
        // l'ancien pattern en dur « d MMMM yyyy, HH:mm » imposait l'ordre jour-mois-année et le
        // 24 h à TOUTES les langues (« 14 June 2026 » en anglais, ordre faux en japonais).
        val infoLocale = Locale.getDefault()
        JtrAlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = Icons.Default.Info,
            title = stringResource(R.string.person_info_title),
            confirmLabel = stringResource(R.string.common_ok),
            onConfirm = { showInfoDialog = false },
            // Corps RICHE (paires libellé/valeur) : `content`, pas `text`.
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(stringResource(R.string.person_info_created),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDateTimeLong(person.createdAt, infoLocale),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                    Column {
                        Text(stringResource(R.string.person_info_updated),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatDateTimeLong(
                                person.updatedAt.takeIf { it > 0 } ?: person.createdAt, infoLocale),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {
        JtrAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = Icons.Default.Delete,
            title = stringResource(R.string.person_delete_dialog_title),
            text = stringResource(R.string.person_delete_dialog_text),
            destructive = true,
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { showDeleteDialog = false; onDeleteClick() },
            dismissLabel = stringResource(R.string.common_cancel)
        )
    }
}



@Composable
private fun EditPhotoBadge(
    isEditing: Boolean,
    hasPhoto: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // v7.1.119 (G3b-3) — remplace l'EditPhotoOverlay historique (voile Color.Black 45 % +
    // flou + libellé blanc sur tout le disque) par le badge caméra v7.1.79 de
    // [FormPhotoHeader] : 32 dp en coin, couleurs DU THÈME (primaryContainer — contraste
    // garanti clair/sombre sur les 6 palettes, aucun noir/blanc codé en dur), Edit si une
    // photo existe, AddAPhoto sinon. INSCRIT dans le coin du disque (pas d'offset négatif,
    // v7.1.89) et miroité naturellement en RTL. Le geste historique est conservé : tout le
    // disque reste cliquable vers le sélecteur, le badge n'est que l'affordance visible.
    AnimatedVisibility(
        visible = isEditing, enter = fadeIn(), exit = fadeOut(), modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (hasPhoto) Icons.Default.Edit else Icons.Default.AddAPhoto,
                contentDescription = stringResource(R.string.person_edit_photo_label),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Icône de marque (couleurs d'origine, non teintée) — même rendu
                        // que l'Accueil/le profil (source unique getSocialIcon/iconRes).
                        Icon(
                            painter = painterResource(platform.iconRes),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp)
                        )
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

/**
 * Badges de catégories de la fiche. FlowRow (v7.1.50) : au-delà de ~3 catégories, une
 * `Row` simple poussait les badges HORS de l'écran (ni retour à la ligne, ni scroll) — le
 * contact paraissait n'appartenir qu'aux premières. Ils se replient désormais sur autant
 * de lignes que nécessaire. Alignement au DÉBUT (et non centré) : centrer chaque rangée
 * indépendamment donnait une pyramide, la dernière rangée, plus courte, se retrouvant
 * décalée par rapport aux précédentes.
 *
 * Badges INTERACTIFS (v5.3.4) : un tap ouvre le détail de la catégorie. Source RÉACTIVE
 * (v7.1.5) → ajout/retrait depuis le sélecteur reflété ici.
 *
 * v7.1.76 (Lot 1 refonte catégories, HARMONISÉ) : la rangée porte son propre bouton
 * « + catégorie » — icône dossier+ ([Icons.Default.CreateNewFolder]), jamais confondable
 * avec le « + lien » voisin — qui ouvre le sélecteur EXISTANT (aucun nouveau flux).
 * MÊME MATRICE DE VISIBILITÉ que [SocialLinksSection], la référence tranchée par HR :
 * lecture sans élément → RIEN (la 1re catégorie s'ajoute via l'édition, comme un lien) ;
 * lecture avec chips → chips + petit « + » rond 28 dp en fin de rangée, sans texte ;
 * édition → affordance texte sous les chips (0 ou N), même grammaire que « + lien ».
 * La rangée porte sa propre marge haute : aucun espace fantôme quand elle ne rend rien.
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun CategoryChipsRow(
    categoryChips: List<Pair<String, String>>,
    isEditing: Boolean,
    onNavigateToCategory: (String) -> Unit,
    onAddCategoryClick: () -> Unit
) {
    // Matrice [SocialLinksSection] : en lecture sans élément, la section n'existe pas.
    if (categoryChips.isEmpty() && !isEditing) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (categoryChips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categoryChips.forEach { (categoryId, name) ->
                    SuggestionChip(
                        onClick = { onNavigateToCategory(categoryId) },
                        label = { Text(name) },
                        icon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) }
                    )
                }
                if (!isEditing) {
                    // Petit « + » rond 28 dp en FIN de rangée (suit le miroir RTL),
                    // strict équivalent du « + lien » de lecture. Un SuggestionChip
                    // occupe un slot de 48 dp (cible tactile a11y) autour de ses 32 dp
                    // visibles : sans `align`, un conteneur de 32 dp calé en haut de
                    // ligne mettait le bouton ~8 dp trop haut (retour HR) ; le centrage
                    // vaut PAR LIGNE, donc reste correct quand les chips se replient.
                    Box(
                        modifier = Modifier.height(32.dp).align(Alignment.CenterVertically),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledTonalIconButton(onClick = onAddCategoryClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.CreateNewFolder,
                                contentDescription = stringResource(R.string.person_add_to_category),
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        if (isEditing) {
            // Affordance texte SOUS les chips — grammaire identique à « Add a social
            // link » du mode édition (TextButton + icône 18 dp + libellé), 0 ou N chips.
            TextButton(
                onClick = onAddCategoryClick,
                modifier = Modifier.align(Alignment.Start),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.person_add_to_category),
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Icône de marque (source unique, non teintée) — cohérent avec
                            // l'aperçu du dialogue et l'affichage du profil.
                            Icon(
                                painter = painterResource(getSocialIcon(link.url)),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(28.dp)
                            )
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
                        // Détection par URL (source unique) → ouvre le bon paquet même pour
                        // un lien hérité dont le libellé stocké serait erroné (ex. ancien « X »).
                        val socialLink = SocialLink(
                            platform = SocialPlatform.detect(link.url) ?: SocialPlatform.Instagram,
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

/**
 * v7.1.46 — Zoom de la mini-carte, à l'ÉCHELLE DE LA VILLE. Il était figé à 12,0, soit ~32 m/px :
 * à ce niveau, le décalage kilométrique NORMAL entre le point renvoyé par Nominatim et le centre
 * perçu d'une ville suffit à poser le repère sur un quartier, dont l'étiquette se lit alors à la
 * place du nom de la ville (« Lakhssassi » au lieu de « Safi », à 1,2 km). À 11,0 (~65 m/px) le
 * même écart devient négligeable et c'est le toponyme de la ville qui s'affiche sous le repère.
 *
 * `MapViewModel.zoomForResult()` n'est pas réutilisable ici : il se déduit de `addresstype`, or
 * seules les coordonnées sont persistées (aucun type de lieu en base). On retient donc la valeur
 * de sa tranche « ville », arrondie vers le bas pour la marge.
 */
private const val CITY_MAP_ZOOM = 11.0

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
                    MotionEvent.ACTION_UP -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        // a11y : signale un « clic » au système (TalkBack) sans consommer
                        // le geste — la carte reçoit toujours l'événement (on renvoie false).
                        v.performClick()
                    }
                    MotionEvent.ACTION_CANCEL ->
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            getMapAsync { map ->
                map.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), CITY_MAP_ZOOM))
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
        else pluralStringResource(R.plurals.person_see_more, hiddenCount, hiddenCount),
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
    onLineClick: ((DynamicLine) -> Unit)? = null
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
                    if (onLineClick != null) {
                        // Valeur cliquable (relation/téléphone/email) — couleur primaire,
                        // sans soulignement : l'interaction se découvre au clic. La relation
                        // navigue par identifiant (v7.1.6) : le clic transmet la LIGNE entière.
                        Text(line.value, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).clickable { onLineClick(line) })
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
    // Affichage localisé (d MMMM yyyy) à partir d'une valeur STOCKÉE locale-libre (ISO).
    // v7.1.30 — formateur ET rendu keyés sur la locale courante : un changement de langue
    // sans recreate() ne laisse jamais un formateur/texte périmé en cache.
    val locale = Locale.getDefault()
    val rendered = remember(lines, locale) {
        lines.mapNotNull { line ->
            // v7.1.37 (B3b) — formatStoredDateLong gère l'ISO complet ET le year-less
            // `--MM-dd` (« 15 mars » localisé) ; null = valeur non affichable → ignorée.
            formatStoredDateLong(line.value, locale)?.let { Triple(line.label, it, line.notify) }
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
            // Titre VIDE possible depuis v7.1.33 (section laissée sans titre) : on MASQUE la ligne
            // de libellé plutôt que d'afficher un vide ou de réinjecter « Notes » → bloc cohérent
            // (icône + contenu). v7.1.75 — la 1re ligne faisant office de titre, elle passe en
            // VRAI style de titre (titleSmall SemiBold, onSurface), gras en lecture seulement.
            if (label.isNotBlank()) {
                Text(label, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
            }
            // v7.1.75 — corps masqué s'il est vide (note d'une seule ligne = titre seul) :
            // sinon Text("") réserverait une ligne fantôme sous le titre.
            if (value.isNotBlank()) {
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
