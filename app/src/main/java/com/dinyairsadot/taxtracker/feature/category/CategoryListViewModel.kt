package com.dinyairsadot.taxtracker.feature.category

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CategoryUi(
    val id: Long,
    val name: String,
    val colorHex: String,
    val description: String
)

data class CategoryListUiState(
    val isLoading: Boolean = false,
    val categories: List<CategoryUi> = emptyList(),
    val errorMessage: String? = null
)

class CategoryListViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryListUiState(isLoading = true))
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    init {
        loadInitialCategories()
    }

    private fun loadInitialCategories() {
        val demo = listOf(
            CategoryUi(
                id = 1L,
                name = "Electricity",
                colorHex = "#FF9800",
                description = "Electricity provider bills"
            ),
            CategoryUi(
                id = 2L,
                name = "Water",
                colorHex = "#2196F3",
                description = "Water and sewage"
            ),
            CategoryUi(
                id = 3L,
                name = "City Taxes",
                colorHex = "#4CAF50",
                description = "Arnona / city hall payments"
            )
        )

        _uiState.value = CategoryListUiState(
            isLoading = false,
            categories = demo,
            errorMessage = null
        )
    }


    fun onCategoryClicked(id: Long) {
        // Add selection logic later
    }

    fun onAddCategoryClicked() {

    }

}
