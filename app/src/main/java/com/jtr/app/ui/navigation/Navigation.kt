package com.jtr.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.category.CategoriesScreen
import com.jtr.app.ui.category.CategoryDetailScreen
import com.jtr.app.ui.category.CategoryGroupDetailScreen
import com.jtr.app.ui.home.HomeScreen
import com.jtr.app.ui.map.MapScreen
import com.jtr.app.ui.person.*
import com.jtr.app.ui.settings.SettingsScreen
import com.jtr.app.ui.theme.ThemePreset
import com.jtr.app.ui.trash.TrashScreen
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    // Route optionnelle : categoryId pré-remplit la catégorie lors de la création
    const val ADD_PERSON = "add_person?categoryId={categoryId}"
    const val PERSON_DETAIL = "person_detail/{personId}"
    const val CATEGORIES = "categories"
    const val CATEGORY_DETAIL = "category_detail/{categoryId}"
    const val CATEGORY_GROUP_DETAIL = "category_group_detail/{groupId}"
    const val SETTINGS = "settings"
    const val MAP_PICKER = "map_picker"
    const val TRASH = "trash"

    fun personDetail(personId: String) = "person_detail/$personId"
    fun categoryDetail(categoryId: String) = "category_detail/$categoryId"
    fun categoryGroupDetail(groupId: Long) = "category_group_detail/$groupId"

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

@Composable
fun JTRMainScaffold(
    navController: NavHostController,
    repository: PersonRepository,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    selectedPreset: ThemePreset,
    onPresetSelected: (ThemePreset) -> Unit,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Mode sélection des catégories (« Samsung Galerie ») hoissé ici : il masque la
    // BottomNavigationBar globale et laisse place au footer contextuel de l'écran.
    var categoriesSelectionMode by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.CATEGORIES) categoriesSelectionMode = false
    }

    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.CATEGORIES, Routes.SETTINGS) &&
        !categoriesSelectionMode

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                NavigationBar {
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
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToAddPerson = { navController.navigate(Routes.addPerson()) },
                    onNavigateToPersonDetail = { id -> navController.navigate(Routes.personDetail(id)) }
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
                var categoryNames by remember { mutableStateOf<List<String>>(emptyList()) }
                val scope = rememberCoroutineScope()
                val db = AppDatabase.getInstance(LocalContext.current)
                val cityFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("selected_city", null)
                    .collectAsStateWithLifecycle()
                val latFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<Double?>("selected_lat", null)
                    .collectAsStateWithLifecycle()
                val lngFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<Double?>("selected_lng", null)
                    .collectAsStateWithLifecycle()

                // Chargement unique des métadonnées (catégories), puis écoute réactive
                // de la personne via Flow Room : toute sauvegarde (commitAllEdits) déclenche
                // une recomposition immédiate sans quitter l'écran.
                LaunchedEffect(personId) {
                    val categoryIds = db.personCategoryDao().getCategoryIdsForPersonSync(personId)
                    categoryNames = categoryIds.mapNotNull { db.categoryDao().getById(it)?.name }
                    repository.markAsContacted(personId)
                    repository.observeById(personId).collect { updated ->
                        if (updated != null) person = updated
                    }
                }

                PersonDetailScreen(
                    person = person,
                    categoryNames = categoryNames,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPerson = { id -> navController.navigate(Routes.personDetail(id)) },
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
                        navController.navigate(Routes.categoryDetail(categoryId))
                    },
                    onGroupClick = { groupId ->
                        navController.navigate(Routes.categoryGroupDetail(groupId))
                    },
                    onSelectionModeChange = { categoriesSelectionMode = it }
                )
            }

            // Intérieur d'un dossier (drill-down) — transition latérale standard.
            composable(
                route = Routes.CATEGORY_GROUP_DETAIL,
                arguments = listOf(navArgument("groupId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
            ) {
                CategoryGroupDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCategoryClick = { categoryId ->
                        navController.navigate(Routes.categoryDetail(categoryId))
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
                    // FAB : navigue vers AddPersonScreen avec la catégorie pré-assignée
                    onNavigateToAddPerson = {
                        navController.navigate(Routes.addPersonInCategory(categoryId))
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    isDarkMode = isDarkMode,
                    onDarkModeChange = onDarkModeChange,
                    selectedPreset = selectedPreset,
                    onPresetSelected = onPresetSelected,
                    onNavigateToTrash = { navController.navigate(Routes.TRASH) }
                )
            }

            composable(Routes.TRASH) {
                TrashScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
