package com.dinyairsadot.taxtracker.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dinyairsadot.taxtracker.core.domain.Category
import com.dinyairsadot.taxtracker.core.domain.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

class CategoryListViewModel(
    private val categoryRepository: CategoryRepository = InMemoryCategoryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryListUiState(isLoading = true))
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    init {
        loadInitialCategories()
    }

    private fun loadInitialCategories() {
        viewModelScope.launch {
            _uiState.value = CategoryListUiState(isLoading = true)

            try {
                val categories = categoryRepository.getCategories()
                val uiCategories = categories.map { it.toUi() }

                _uiState.value = CategoryListUiState(
                    isLoading = false,
                    categories = uiCategories,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = CategoryListUiState(
                    isLoading = false,
                    categories = emptyList(),
                    errorMessage = "Failed to load categories"
                )
            }
        }
    }

    fun onCategoryClicked(id: Long) {
        // Add selection logic later
    }

    fun onAddCategoryClicked() {
        // Add analytics / logging later
    }
}

// Mapping from domain model to UI model
private fun Category.toUi(): CategoryUi {
    return CategoryUi(
        id = this.id.toLong(),
        name = this.name,
        colorHex = this.colorHex,
        description = this.description ?: ""
    )
}
