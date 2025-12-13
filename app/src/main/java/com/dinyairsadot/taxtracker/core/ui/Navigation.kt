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
import com.dinyairsadot.taxtracker.feature.invoice.AddInvoiceScreen

import androidx.compose.runtime.*

import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding

import com.dinyairsadot.taxtracker.feature.invoice.InvoiceDetailsScreen
import com.dinyairsadot.taxtracker.feature.invoice.InvoiceListViewModel

import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier


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

    object AddInvoice : Screen("add_invoice/{categoryId}") {
        fun routeWithCategoryId(categoryId: Long) = "add_invoice/$categoryId"
    }

    object InvoiceDetails : Screen("invoice_details/{invoiceId}") {
        fun routeWithId(invoiceId: Long) = "invoice_details/$invoiceId"
    }

}

@OptIn(ExperimentalMaterial3Api::class)
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
                    navController.navigate(Screen.InvoiceList.routeWithCategoryId(id))
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

            val viewModel: InvoiceListViewModel = viewModel(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Load invoices whenever the categoryId changes (or first time we enter)
            androidx.compose.runtime.LaunchedEffect(categoryId) {
                viewModel.loadInvoices(categoryId)
            }

            InvoiceListScreen(
                categoryId = categoryId,
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onEditCategoryClick = {
                    navController.navigate(Screen.EditCategory.routeWithId(categoryId))
                },
                onAddInvoiceClick = {
                    navController.navigate(Screen.AddInvoice.routeWithCategoryId(categoryId))
                },
                onInvoiceClick = { invoiceId ->
                    navController.navigate(
                        Screen.InvoiceDetails.routeWithId(invoiceId)
                    )
                }
            )
        }
        // -------------------------
        // Invoice add screen
        // -------------------------
        composable(
            route = Screen.AddInvoice.route,
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: return@composable

            // We don't need uiState here, just the ability to add an invoice.
            val viewModel: InvoiceListViewModel = viewModel(backStackEntry)

            AddInvoiceScreen(
                categoryId = categoryId,
                onNavigateBack = { navController.popBackStack() },
                onSaveInvoice = { amount, dateText, paymentStatus, notes ->
                    viewModel.addInvoice(
                        categoryId = categoryId,
                        amount = amount,
                        dateText = dateText,
                        paymentStatus = paymentStatus,
                        notes = notes
                    )
                    // AddInvoiceScreen will also call onNavigateBack() after this
                }
            )
        }

        composable(
            route = Screen.InvoiceDetails.route,
            arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
        ) { backStackEntry ->
            val invoiceId = backStackEntry.arguments?.getLong("invoiceId") ?: return@composable

            // Share InvoiceListViewModel between list and details,
            // just like you share CategoryListViewModel across screens.
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.InvoiceList.route)
            }
            val viewModel: InvoiceListViewModel = viewModel(parentEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val invoice = uiState.invoices.firstOrNull { it.id == invoiceId }

            if (invoice == null) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Invoice not found") },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("This invoice could not be loaded.")
                    }
                }
            } else {
                InvoiceDetailsScreen(
                    invoice = invoice,
                    onBackClick = { navController.popBackStack() },
                    onEditClick = {
                        // TODO.
                        // Next step: navigate to EditInvoiceScreen here.
                    }
                )
            }
        }
    }
}
