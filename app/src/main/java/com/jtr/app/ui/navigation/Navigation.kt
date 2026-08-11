package com.jtr.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.annotation.StringRes
import com.jtr.app.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.net.Uri
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.ui.backup.BackupDialog
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.category.CategoriesScreen
import com.jtr.app.ui.category.CategoryDetailScreen
import com.jtr.app.ui.category.CategoryGroupDetailScreen
import com.jtr.app.ui.category.SelectContactsScreen
import com.jtr.app.ui.components.JtrBottomBarTransitions
import com.jtr.app.ui.contacts.ImportContactsScreen
import com.jtr.app.ui.home.HomeScreen
import com.jtr.app.ui.map.MapScreen
import com.jtr.app.ui.person.*
import com.jtr.app.ui.settings.SettingsScreen
import com.jtr.app.ui.theme.ThemePreset
import com.jtr.app.ui.trash.TrashScreen
import com.jtr.app.ui.welcome.WelcomeScreen
import com.jtr.app.ui.welcome.WelcomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    // Route optionnelle : categoryId pré-remplit la catégorie lors de la création
    const val ADD_PERSON = "add_person?categoryId={categoryId}"
    const val PERSON_DETAIL = "person_detail/{personId}"
    const val CATEGORIES = "categories"
    const val CATEGORY_DETAIL = "category_detail/{categoryId}"
    const val CATEGORY_GROUP_DETAIL = "category_group_detail/{groupId}"
    const val SELECT_CONTACTS = "select_contacts/{categoryId}"
    const val SETTINGS = "settings"
    const val MAP_PICKER = "map_picker"
    const val TRASH = "trash"
    const val IMPORT_CONTACTS = "import_contacts"

    fun personDetail(personId: String) = "person_detail/$personId"
    fun categoryDetail(categoryId: String) = "category_detail/$categoryId"
    fun categoryGroupDetail(groupId: Long) = "category_group_detail/$groupId"

    /** Sélecteur de contacts existants à associer à une catégorie (cinématique Accueil). */
    fun selectContacts(categoryId: String) = "select_contacts/$categoryId"

    /** Navigation vers AddPersonScreen depuis une catégorie (contact pré-assigné). */
    fun addPersonInCategory(categoryId: String) = "add_person?categoryId=$categoryId"

    /** Navigation vers AddPersonScreen sans catégorie pré-assignée. */
    fun addPerson() = "add_person"
}

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, Icons.Default.Home, R.string.nav_home),
    BottomNavItem(Routes.CATEGORIES, Icons.Default.Folder, R.string.nav_categories),
    BottomNavItem(Routes.SETTINGS, Icons.Default.Settings, R.string.nav_settings),
)

/**
 * SOURCE UNIQUE de l'inset des écrans IMBRIQUÉS dans [JTRMainScaffold] (Accueil, Catégories,
 * Paramètres). Leur `Scaffold` interne doit appliquer à son CONTENU défilant **uniquement
 * l'inset HORIZONTAL** des barres système : en edge-to-edge **paysage 3 boutons**, la barre de
 * navigation passe sur le CÔTÉ et sans cet inset le contenu de liste (et l'étoile favorite en
 * bout de ligne) passerait SOUS elle. Le HAUT est déjà géré par la `TopAppBar` de chaque écran,
 * le BAS par la `bottomBar` globale de [JTRMainScaffold] (d'où l'ancien `WindowInsets(0)` qui,
 * lui, oubliait l'horizontal). La `TopAppBar` et la `NavigationBar` s'insèrent déjà seules de
 * l'horizontal → on ne le ré-applique PAS au niveau parent (sinon double inset des barres).
 * En navigation gestuelle, l'inset horizontal est ~0 → contenu pleine largeur (comportement
 * voulu). Règle centralisée ici → cohérente dans toutes les orientations.
 */
val nestedScreenContentInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)

@Composable
fun JTRMainScaffold(
    navController: NavHostController,
    repository: PersonRepository,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    selectedPreset: ThemePreset,
    onPresetSelected: (ThemePreset) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    /** Id transporté par une notification de proximité → ouverture de la fiche. */
    notificationPersonId: String? = null,
    /** URI d'un `.jtr` ouvert depuis un gestionnaire de fichiers (v7.1.27). */
    importUri: Uri? = null,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // RTL : le glissement des transitions doit entrer du côté opposé en arabe.
    // On inverse simplement le signe des offsets selon la direction de mise en page.
    val rtlSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

    // GARDE ANTI « TOUCHE FANTÔME » : on suit l'état de cycle de vie de la destination
    // courante. Pendant une transition, la nouvelle destination n'est encore que
    // STARTED (pas RESUMED) → on bloque alors tout pointeur sur la zone de contenu,
    // de sorte que NI l'écran sortant NI l'écran entrant ne traite de clic durant le
    // fondu (la fenêtre interactive de chevauchement, cause du bug, disparaît).
    var contentResumed by remember { mutableStateOf(true) }
    DisposableEffect(navBackStackEntry) {
        val lifecycle = navBackStackEntry?.lifecycle
        if (lifecycle == null) {
            contentResumed = true
            onDispose { }
        } else {
            contentResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val observer = LifecycleEventObserver { _, _ ->
                contentResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }

    // Onboarding : première ouverture → écran de Bienvenue (flag persistant).
    val appContext = LocalContext.current.applicationContext
    val startDestination = remember {
        val prefs = appContext.getSharedPreferences("jtr_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(WelcomeViewModel.FIRST_LAUNCH_KEY, true)) Routes.WELCOME else Routes.HOME
    }

    // Modes sélection (« Samsung Galerie ») hoissés ici : ils masquent la
    // BottomNavigationBar globale et laissent place au footer contextuel de l'écran.
    var categoriesSelectionMode by remember { mutableStateOf(false) }
    var homeSelectionMode by remember { mutableStateOf(false) }
    // Contacts en attente de déplacement (action « Déplacer » de l'Accueil) : la
    // cible est choisie en touchant directement une catégorie dans l'onglet
    // Catégories — aucun dialogue intermédiaire.
    var pendingMovePersonIds by remember { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()

    // Deep link de notification (Moteur de Proximité v5.4) : navigation unique
    // vers la fiche du contact dès que le graphe est prêt.
    LaunchedEffect(notificationPersonId) {
        notificationPersonId?.let { id ->
            navController.navigate(Routes.personDetail(id)) { launchSingleTop = true }
        }
    }

    // Ouverture d'un `.jtr` depuis un gestionnaire (v7.1.27) : on présente le MÊME
    // dialogue Sauvegarde/restauration, pré-armé sur l'URI entrante → confirmation
    // explicite + validation par contenu avant toute écriture (réutilise BackupDialog,
    // BackupViewModel et les erreurs typées). Affiché uniquement déverrouillé.
    var externalImportUri by remember(importUri) { mutableStateOf(importUri) }
    externalImportUri?.let { uri ->
        BackupDialog(
            onDismiss = { externalImportUri = null },
            initialImportUri = uri
        )
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.CATEGORIES) categoriesSelectionMode = false
        if (currentRoute != Routes.HOME) homeSelectionMode = false
        // Le mode « choisir une cible » ne survit pas à une sortie de la section Catégories.
        if (currentRoute !in listOf(Routes.CATEGORIES, Routes.CATEGORY_GROUP_DETAIL)) {
            pendingMovePersonIds = null
        }
    }

    // Assigne les contacts en attente à la catégorie touchée, puis OUVRE cette
    // catégorie : l'utilisateur reste dans la cible et en sort par le bouton Retour.
    fun assignPendingMoveTo(categoryId: String) {
        val ids = pendingMovePersonIds ?: return
        pendingMovePersonIds = null
        scope.launch {
            repository.assignCategory(ids, categoryId)
            navController.navigate(Routes.categoryDetail(categoryId)) {
                launchSingleTop = true
            }
        }
    }

    val categoryRepository = remember { CategoryRepository(appContext) }

    // Création de catégorie À LA VOLÉE pendant le déplacement (v5.3.2) : insertion
    // de la catégorie + association PAR LOTS des contacts cochés sur Dispatchers.IO,
    // puis redirection vers le détail de la nouvelle cible (navigation sur Main).
    fun createTargetAndAssignPending(name: String, color: String, imagePath: String?) {
        val ids = pendingMovePersonIds ?: return
        pendingMovePersonIds = null
        scope.launch {
            val category = Category(name = name, color = color, imagePath = imagePath)
            withContext(Dispatchers.IO) {
                categoryRepository.add(category)
                repository.assignCategory(ids, category.id)
            }
            navController.navigate(Routes.categoryDetail(category.id)) {
                launchSingleTop = true
            }
        }
    }

    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.CATEGORIES, Routes.SETTINGS) &&
        !categoriesSelectionMode && !homeSelectionMode

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                // Même transition que les footers de sélection (fade + expand/shrink) :
                // la NavigationBar rétrécit pendant que le footer grandit → aucune
                // variation nette de hauteur, donc aucun saut de la liste.
                enter = JtrBottomBarTransitions.enter,
                exit = JtrBottomBarTransitions.exit
            ) {
                // Couleur du fond identique aux écrans (background teinté du thème)
                // → aucune couture/bande grise entre le contenu et la barre.
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
      Box(modifier = Modifier.padding(paddingValues)) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            // Transition DOUCE & RAPIDE (~220 ms) : fondu + léger glissement horizontal
            // pour les changements d'onglet et l'entrée sur un écran. La fenêtre de
            // « touche fantôme » qu'introduit l'animation est neutralisée par la garde
            // RESUMED (overlay ci-dessous). Les destinations ayant leur propre animation
            // (ex. détail = slide complet) la conservent.
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { rtlSign * it / 16 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { rtlSign * it / 16 } }
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToAddPerson = { navController.navigate(Routes.addPerson()) },
                    onNavigateToPersonDetail = { id -> navController.navigate(Routes.personDetail(id)) },
                    onSelectionModeChange = { homeSelectionMode = it },
                    // « Déplacer » : bascule fluide vers l'onglet Catégories en mode cible.
                    onMoveSelectionToCategory = { ids ->
                        pendingMovePersonIds = ids
                        navController.navigate(Routes.CATEGORIES) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // add_person accepte un categoryId optionnel (null = pas de catégorie pré-assignée)
            composable(
                route = Routes.ADD_PERSON,
                arguments = listOf(
                    navArgument("categoryId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val cityFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("selected_city", null)
                    .collectAsStateWithLifecycle()
                val latFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<Double?>("selected_lat", null)
                    .collectAsStateWithLifecycle()
                val lngFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<Double?>("selected_lng", null)
                    .collectAsStateWithLifecycle()
                AddPersonScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMap = { navController.navigate(Routes.MAP_PICKER) },
                    cityFromMap = cityFromMap,
                    latFromMap = latFromMap,
                    lngFromMap = lngFromMap,
                    onMapResultConsumed = {
                        backStackEntry.savedStateHandle.remove<String>("selected_city")
                        backStackEntry.savedStateHandle.remove<Double>("selected_lat")
                        backStackEntry.savedStateHandle.remove<Double>("selected_lng")
                    }
                )
            }

            composable(
                route = Routes.PERSON_DETAIL,
                arguments = listOf(navArgument("personId") { type = NavType.StringType })
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId") ?: ""
                var person by remember { mutableStateOf<Person?>(null) }
                val scope = rememberCoroutineScope()
                val cityFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("selected_city", null)
                    .collectAsStateWithLifecycle()
                val latFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<Double?>("selected_lat", null)
                    .collectAsStateWithLifecycle()
                val lngFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<Double?>("selected_lng", null)
                    .collectAsStateWithLifecycle()

                // Écoute réactive de la personne via Flow Room : toute sauvegarde
                // (commitAllEdits) déclenche une recomposition immédiate sans quitter l'écran.
                // Les catégories du profil (badges + sélecteur) sont gérées réactivement par
                // PersonCategoriesViewModel dans l'écran (v7.1.5).
                LaunchedEffect(personId) {
                    repository.markAsContacted(personId)
                    repository.observeById(personId).collect { updated ->
                        if (updated != null) person = updated
                    }
                }

                PersonDetailScreen(
                    person = person,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPerson = { id -> navController.navigate(Routes.personDetail(id)) },
                    // Badge de catégorie → détail de la catégorie, instantanément.
                    onNavigateToCategory = { id -> navController.navigate(Routes.categoryDetail(id)) },
                    onDeleteClick = {
                        scope.launch {
                            repository.softDelete(personId)
                            navController.popBackStack()
                        }
                    },
                    onNavigateToMap = { navController.navigate(Routes.MAP_PICKER) },
                    cityFromMap = cityFromMap,
                    latFromMap = latFromMap,
                    lngFromMap = lngFromMap,
                    onMapResultConsumed = {
                        backStackEntry.savedStateHandle.remove<String>("selected_city")
                        backStackEntry.savedStateHandle.remove<Double>("selected_lat")
                        backStackEntry.savedStateHandle.remove<Double>("selected_lng")
                    }
                )
            }

            composable(Routes.MAP_PICKER) {
                MapScreen(
                    onLocationSelected = { cityName, lat, lng ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.apply {
                                set("selected_city", cityName)
                                set("selected_lat", lat)
                                set("selected_lng", lng)
                            }
                        navController.popBackStack()
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(
                    onCategoryClick = { categoryId ->
                        // En mode cible, toucher une catégorie assigne les contacts en
                        // attente ; sinon, navigation normale vers le détail.
                        if (pendingMovePersonIds != null) assignPendingMoveTo(categoryId)
                        else navController.navigate(Routes.categoryDetail(categoryId))
                    },
                    onGroupClick = { groupId ->
                        navController.navigate(Routes.categoryGroupDetail(groupId))
                    },
                    onSelectionModeChange = { categoriesSelectionMode = it },
                    isPickingMoveTarget = pendingMovePersonIds != null,
                    onCancelMoveTarget = { pendingMovePersonIds = null },
                    onCreateMoveTarget = { name, color, imagePath ->
                        createTargetAndAssignPending(name, color, imagePath)
                    }
                )
            }

            // Intérieur d'un dossier (drill-down) — transition latérale standard.
            composable(
                route = Routes.CATEGORY_GROUP_DETAIL,
                arguments = listOf(navArgument("groupId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally(initialOffsetX = { rtlSign * it }) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -rtlSign * it / 3 }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -rtlSign * it / 3 }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { rtlSign * it }) }
            ) {
                CategoryGroupDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCategoryClick = { categoryId ->
                        // Le mode cible traverse les dossiers : toucher une catégorie
                        // membre assigne les contacts en attente.
                        if (pendingMovePersonIds != null) assignPendingMoveTo(categoryId)
                        else navController.navigate(Routes.categoryDetail(categoryId))
                    },
                    onGroupClick = { subGroupId ->
                        navController.navigate(Routes.categoryGroupDetail(subGroupId))
                    }
                )
            }

            composable(
                route = Routes.CATEGORY_DETAIL,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
                CategoryDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPersonDetail = { id ->
                        navController.navigate(Routes.personDetail(id))
                    },
                    // « + » : navigue vers AddPersonScreen avec la catégorie pré-assignée
                    onNavigateToAddPerson = {
                        navController.navigate(Routes.addPersonInCategory(categoryId))
                    },
                    // « Ajouter un contact existant » : cinématique type Accueil en mode
                    // sélection (aucun dialogue), retour automatique après validation.
                    onAddExistingContacts = {
                        navController.navigate(Routes.selectContacts(categoryId))
                    }
                )
            }

            composable(
                route = Routes.SELECT_CONTACTS,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
                enterTransition = { slideInHorizontally(initialOffsetX = { rtlSign * it }) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -rtlSign * it / 3 }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -rtlSign * it / 3 }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { rtlSign * it }) }
            ) {
                SelectContactsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    isDarkMode = isDarkMode,
                    onDarkModeChange = onDarkModeChange,
                    selectedPreset = selectedPreset,
                    onPresetSelected = onPresetSelected,
                    fontScale = fontScale,
                    onFontScaleChange = onFontScaleChange,
                    onNavigateToTrash = { navController.navigate(Routes.TRASH) },
                    onNavigateToImportContacts = { navController.navigate(Routes.IMPORT_CONTACTS) }
                )
            }

            composable(Routes.TRASH) {
                TrashScreen(onNavigateBack = { navController.popBackStack() })
            }

            // Import des contacts du téléphone depuis les Paramètres (v7.1.28) —
            // transition latérale standard (drill-down), permission déjà accordée en amont.
            composable(
                route = Routes.IMPORT_CONTACTS,
                enterTransition = { slideInHorizontally(initialOffsetX = { rtlSign * it }) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -rtlSign * it / 3 }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -rtlSign * it / 3 }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { rtlSign * it }) }
            ) {
                ImportContactsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        // Overlay de la garde RESUMED : tant que la destination courante n'est pas
        // RESUMED (= une transition est en cours), il recouvre la zone de contenu et
        // ABSORBE tous les pointeurs → aucun clic n'atteint l'écran sortant ni l'écran
        // entrant. Il disparaît dès la fin du fondu (ON_RESUME). La barre de navigation,
        // hors de cette Box, reste utilisable pendant l'animation.
        if (!contentResumed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }
      }
    }
}
