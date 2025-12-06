package com.dinyairsadot.taxtracker.feature.category

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CategoryListRoute(
    onAddCategoryClick: () -> Unit,
    onCategoryClick: (Long) -> Unit,
    viewModel: CategoryListViewModel = viewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    CategoryListScreen(
        isLoading = uiState.isLoading,
        categories = uiState.categories,
        errorMessage = uiState.errorMessage,
        onAddCategoryClick = {
            viewModel.onAddCategoryClicked()
            onAddCategoryClick()
        },
        onCategoryClick = { id ->
            viewModel.onCategoryClicked(id)
            onCategoryClick(id)
        }
    )
}
