package com.jtr.app.ui.navigation

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
import androidx.compose.ui.platform.LocalContext
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.category.CategoriesScreen
import com.jtr.app.ui.category.CategoryDetailScreen
import com.jtr.app.ui.home.HomeScreen
import com.jtr.app.ui.map.MapScreen
import com.jtr.app.ui.person.*
import com.jtr.app.ui.settings.SettingsScreen
import com.jtr.app.ui.theme.ThemePreset
import com.jtr.app.ui.trash.TrashScreen
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    // Route optionnelle : categoryId pré-remplit la catégorie lors de la création
    const val ADD_PERSON = "add_person?categoryId={categoryId}"
    const val PERSON_DETAIL = "person_detail/{personId}"
    const val EDIT_PERSON = "edit_person/{personId}"
    const val CATEGORIES = "categories"
    const val CATEGORY_DETAIL = "category_detail/{categoryId}"
    const val SETTINGS = "settings"
    const val MAP_PICKER = "map_picker"
    const val TRASH = "trash"

    fun personDetail(personId: String) = "person_detail/$personId"
    fun editPerson(personId: String) = "edit_person/$personId"
    fun categoryDetail(categoryId: String) = "category_detail/$categoryId"

    /** Navigation vers AddPersonScreen depuis une catégorie (contact pré-assigné). */
    fun addPersonInCategory(categoryId: String) = "add_person?categoryId=$categoryId"

    /** Navigation vers AddPersonScreen sans catégorie pré-assignée. */
    fun addPerson() = "add_person"
}

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, Icons.Default.Home, "Accueil"),
    BottomNavItem(Routes.CATEGORIES, Icons.Default.Folder, "Catégories"),
    BottomNavItem(Routes.SETTINGS, Icons.Default.Settings, "Paramètres"),
)

@Composable
fun JTRMainScaffold(
    navController: NavHostController,
    repository: PersonRepository,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    selectedPreset: ThemePreset,
    onPresetSelected: (ThemePreset) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.CATEGORIES, Routes.SETTINGS)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
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
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
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

                LaunchedEffect(personId) {
                    person = repository.getById(personId)
                    // Récupère tous les noms de catégories via la table de jointure (Many-to-Many)
                    val categoryIds = db.personCategoryDao().getCategoryIdsForPersonSync(personId)
                    categoryNames = categoryIds.mapNotNull { db.categoryDao().getById(it)?.name }
                    repository.markAsContacted(personId)
                }

                PersonDetailScreen(
                    person = person,
                    categoryNames = categoryNames,
                    onNavigateBack = { navController.popBackStack() },
                    onEditClick = { navController.navigate(Routes.editPerson(personId)) },
                    onDeleteClick = {
                        scope.launch {
                            repository.softDelete(personId)
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(
                route = Routes.EDIT_PERSON,
                arguments = listOf(navArgument("personId") { type = NavType.StringType })
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId") ?: ""
                val cityFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<String?>("selected_city", null)
                    .collectAsStateWithLifecycle()
                val latFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<Double?>("selected_lat", null)
                    .collectAsStateWithLifecycle()
                val lngFromMap by backStackEntry.savedStateHandle
                    .getStateFlow<Double?>("selected_lng", null)
                    .collectAsStateWithLifecycle()
                EditPersonScreen(
                    personId = personId,
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
