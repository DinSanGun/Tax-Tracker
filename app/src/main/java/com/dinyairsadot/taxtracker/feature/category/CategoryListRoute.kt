package com.dinyairsadot.taxtracker.feature.category

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CategoryListRoute(
    onAddCategoryClick: () -> Unit,
    onCategoryClick: (Long) -> Unit,
    viewModel: CategoryListViewModel,
    showCategoryAddedMessage: Boolean = false,
    onCategoryAddedMessageShown: () -> Unit = {}
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
        },
        onDeleteCategory = { id ->
            viewModel.deleteCategory(id)
        },
        showCategoryAddedMessage = showCategoryAddedMessage,
        onCategoryAddedMessageShown = onCategoryAddedMessageShown
    )
}
