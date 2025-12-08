package com.dinyairsadot.taxtracker.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.dinyairsadot.taxtracker.feature.category.AddCategoryScreen
import com.dinyairsadot.taxtracker.feature.category.CategoryListRoute
import com.dinyairsadot.taxtracker.feature.category.CategoryListViewModel
import com.dinyairsadot.taxtracker.feature.category.EditCategoryScreen

// Simple sealed class to define app routes
sealed class Screen(val route: String) {
    data object CategoryList : Screen("category_list")
    data object AddCategory : Screen("add_category")
    data object EditCategory : Screen("edit_category/{categoryId}") {
        fun routeWithId(id: Long) = "edit_category/$id"
    }
}

@Composable
fun TaxTrackerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.CategoryList.route,
        modifier = modifier
    ) {
        composable(Screen.CategoryList.route) { backStackEntry ->
            val viewModel: CategoryListViewModel = viewModel(backStackEntry)

            CategoryListRoute(
                onAddCategoryClick = {
                    navController.navigate(Screen.AddCategory.route)
                },
                onCategoryClick = { id ->
                    navController.navigate(Screen.EditCategory.routeWithId(id))
                },
                viewModel = viewModel
            )
        }

        composable(Screen.AddCategory.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.CategoryList.route)
            }
            val viewModel: CategoryListViewModel = viewModel(parentEntry)

            // ✅ Collect existing names (case-insensitive)
            val existingNamesLower = viewModel.uiState.value.categories
                .map { it.name.trim().lowercase() }
                .toSet()

            AddCategoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveCategory = { name, colorHex, description ->
                    viewModel.addCategory(name, colorHex, description)
                },
                existingNamesLower = existingNamesLower
            )
        }

        composable(
            route = Screen.EditCategory.route,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.CategoryList.route)
            }
            val viewModel: CategoryListViewModel = viewModel(parentEntry)

            val categoryId = backStackEntry.arguments?.getLong("categoryId")
            if (categoryId == null) {
                navController.popBackStack()
            } else {
                val categories = viewModel.uiState.value.categories
                val category = categories.find { it.id == categoryId }

                if (category == null) {
                    navController.popBackStack()
                } else {
                    // ✅ all other names (excluding the current category)
                    val otherNamesLower = categories
                        .filter { it.id != categoryId }
                        .map { it.name.trim().lowercase() }
                        .toSet()

                    EditCategoryScreen(
                        initialName = category.name,
                        initialColorHex = category.colorHex,
                        initialDescription = category.description,
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onSaveCategory = { name, colorHex, description ->
                            viewModel.updateCategory(categoryId, name, colorHex, description)
                        },
                        otherNamesLower = otherNamesLower
                    )
                }
            }
        }
    }
}
