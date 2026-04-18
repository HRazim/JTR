package com.jtr.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    const val ADD_PERSON = "add_person"
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
                    onNavigateToAddPerson = { navController.navigate(Routes.ADD_PERSON) },
                    onNavigateToPersonDetail = { id -> navController.navigate(Routes.personDetail(id)) }
                )
            }

            composable(Routes.ADD_PERSON) {
                AddPersonScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMap = { navController.navigate(Routes.MAP_PICKER) }
                )
            }

            composable(
                route = Routes.PERSON_DETAIL,
                arguments = listOf(navArgument("personId") { type = NavType.StringType })
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId") ?: ""
                var person by remember { mutableStateOf<Person?>(null) }
                var categoryName by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val db = AppDatabase.getInstance(LocalContext.current)

                LaunchedEffect(personId) {
                    person = repository.getById(personId)
                    categoryName = person?.categoryId?.let { db.categoryDao().getById(it)?.name }
                    repository.markAsContacted(personId)
                }

                PersonDetailScreen(
                    person = person,
                    categoryName = categoryName,
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
                EditPersonScreen(
                    personId = personId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMap = { navController.navigate(Routes.MAP_PICKER) }
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
            ) {
                CategoryDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPersonDetail = { id ->
                        navController.navigate(Routes.personDetail(id))
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
