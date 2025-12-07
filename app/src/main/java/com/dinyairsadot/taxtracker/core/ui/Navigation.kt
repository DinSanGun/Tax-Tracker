package com.dinyairsadot.taxtracker.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dinyairsadot.taxtracker.feature.category.CategoryListRoute
import com.dinyairsadot.taxtracker.feature.category.AddCategoryScreen

// Simple sealed class to define app routes
sealed class Screen(val route: String) {
    data object CategoryList : Screen("category_list")
    data object AddCategory : Screen("add_category")
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
        composable(Screen.CategoryList.route) {
            CategoryListRoute(
                onAddCategoryClick = {
                    navController.navigate(Screen.AddCategory.route)
                },
                onCategoryClick = { id ->
                    // TODO: later navigate to category details or invoices list
                }
            )
        }

        composable(Screen.AddCategory.route) {
            AddCategoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveCategory = { name, colorHex, description ->
                    // TODO: next step - send this to a ViewModel / repository
                    // For now we just ignore it (but we *receive* it successfully)
                }
            )
        }
    }
}
