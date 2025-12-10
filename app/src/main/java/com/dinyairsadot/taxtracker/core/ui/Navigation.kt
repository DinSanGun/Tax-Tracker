package com.dinyairsadot.taxtracker.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dinyairsadot.taxtracker.feature.category.AddCategoryScreen
import com.dinyairsadot.taxtracker.feature.category.CategoryListRoute
import com.dinyairsadot.taxtracker.feature.category.CategoryListViewModel
import com.dinyairsadot.taxtracker.feature.category.EditCategoryScreen
import com.dinyairsadot.taxtracker.feature.invoice.InvoiceListScreen

// Adjust if your Screen definitions live elsewhere
sealed class Screen(val route: String) {
    object CategoryList : Screen("category_list")
    object AddCategory : Screen("add_category")
    object EditCategory : Screen("edit_category/{categoryId}") {
        const val ARG_CATEGORY_ID = "categoryId"
        fun routeWithId(id: Long): String = "edit_category/$id"
    }

    object InvoiceList : Screen("invoice_list/{categoryId}") {
        fun routeWithCategoryId(categoryId: Long) = "invoice_list/$categoryId"
    }
}

@Composable
fun TaxTrackerNavHost(
    navController: NavHostController,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.CategoryList.route,
        modifier = modifier
    ) {
        // -------------------------
        // Category list screen
        // -------------------------
        composable(Screen.CategoryList.route) { backStackEntry ->
            val viewModel: CategoryListViewModel = viewModel(backStackEntry)

            // Read "category_added" flag from saved state (for snackbar)
            val categoryAdded =
                backStackEntry.savedStateHandle.get<Boolean>("category_added") == true

            CategoryListRoute(
                onAddCategoryClick = {
                    navController.navigate(Screen.AddCategory.route)
                },
                onCategoryClick = { id ->
                    navController.navigate(Screen.EditCategory.routeWithId(id))
                },
                viewModel = viewModel,
                showCategoryAddedMessage = categoryAdded,
                onCategoryAddedMessageShown = {
                    backStackEntry.savedStateHandle.remove<Boolean>("category_added")
                }
            )
        }

        // -------------------------
        // Add category screen
        // -------------------------
        composable(Screen.AddCategory.route) { backStackEntry ->
            // Share the same ViewModel instance as CategoryList
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.CategoryList.route)
            }
            val viewModel: CategoryListViewModel = viewModel(parentEntry)

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val existingNamesLower = uiState.categories
                .map { it.name.trim().lowercase() }
                .toSet()

            AddCategoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveCategory = { name, colorHex, description ->
                    viewModel.addCategory(name, colorHex, description)
                },
                existingNamesLower = existingNamesLower,
                onCategorySaved = {
                    // Set flag so CategoryList can show "Category added" snackbar
                    navController
                        .previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("category_added", true)
                }
            )
        }

        // -------------------------
        // Edit category screen
        // -------------------------
        composable(
            route = Screen.EditCategory.route,
            arguments = listOf(
                navArgument(Screen.EditCategory.ARG_CATEGORY_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val categoryId =
                backStackEntry.arguments?.getLong(Screen.EditCategory.ARG_CATEGORY_ID)
                    ?: return@composable

            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.CategoryList.route)
            }
            val viewModel: CategoryListViewModel = viewModel(parentEntry)

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val category = uiState.categories.firstOrNull { it.id == categoryId }
            if (category == null) {
                // If category is missing (e.g. deleted), go back
                navController.popBackStack()
                return@composable
            }

            val otherNamesLower = uiState.categories
                .filter { it.id != categoryId }
                .map { it.name.trim().lowercase() }
                .toSet()

            EditCategoryScreen(
                initialName = category.name,
                initialColorHex = category.colorHex,
                initialDescription = category.description,
                otherNamesLower = otherNamesLower,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveCategory = { name, colorHex, description ->
                    viewModel.updateCategory(
                        id = category.id,
                        name = name,
                        colorHex = colorHex,
                        description = description
                    )
                },
                onDeleteCategory = {
                    viewModel.deleteCategory(category.id)
                    // EditCategoryScreen's delete dialog already calls onNavigateBack()
                }
            )
        }
        // -------------------------
        // Invoice list screen
        // -------------------------
        composable(
            route = Screen.InvoiceList.route,
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: return@composable

            InvoiceListScreen(
                categoryId = categoryId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
