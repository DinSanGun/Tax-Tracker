package com.dinyairsadot.clearledger.feature.category

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CategoryListRoute(
    onAddCategoryClick: () -> Unit,
    onCategoryClick: (Long) -> Unit,
    onEditCategoryClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
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
        onEditCategoryClick = onEditCategoryClick,
        onDeleteCategory = { id ->
            viewModel.deleteCategory(id)
        },
        onSettingsClick = onSettingsClick,
        isReorderMode = uiState.isReorderMode,
        onEnterReorderMode = { viewModel.enterReorderMode() },
        onExitReorderMode = { viewModel.exitReorderMode() },
        onMoveCategoryUp = { id -> viewModel.moveCategoryUp(id) },
        onMoveCategoryDown = { id -> viewModel.moveCategoryDown(id) },
        showCategoryAddedMessage = showCategoryAddedMessage,
        onCategoryAddedMessageShown = onCategoryAddedMessageShown,
        viewModel = viewModel
    )
}
