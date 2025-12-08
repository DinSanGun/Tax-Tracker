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

    fun addCategory(name: String, colorHex: String, description: String) {
        viewModelScope.launch {
            try {
                val current = categoryRepository.getCategories()
                val nextId = (current.maxOfOrNull { it.id } ?: 0L) + 1L

                // ✅ If color is blank, use a default
                val safeColorHex = if (colorHex.isBlank()) "#FF9800" else colorHex.trim()

                val newCategory = Category(
                    id = nextId,
                    name = name,
                    colorHex = safeColorHex,
                    description = description.ifBlank { null }
                )

                categoryRepository.addCategory(newCategory)

                val updated = categoryRepository.getCategories()
                _uiState.value = _uiState.value.copy(
                    categories = updated.map { it.toUi() },
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to add category"
                )
            }
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            try {
                categoryRepository.deleteCategory(id)

                val updated = categoryRepository.getCategories()
                _uiState.value = _uiState.value.copy(
                    categories = updated.map { it.toUi() },
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to delete category"
                )
            }
        }
    }

    fun updateCategory(
        id: Long,
        name: String,
        colorHex: String,
        description: String
    ) {
        viewModelScope.launch {
            try {
                // Reuse the same safe color logic as addCategory
                val safeColorHex = if (colorHex.isBlank()) "#FF9800" else colorHex.trim()

                val updatedCategory = Category(
                    id = id,
                    name = name.trim(),
                    colorHex = safeColorHex,
                    description = description.trim().ifBlank { null }
                )

                categoryRepository.updateCategory(updatedCategory)

                val updatedList = categoryRepository.getCategories()
                _uiState.value = _uiState.value.copy(
                    categories = updatedList.map { it.toUi() },
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to update category"
                )
            }
        }
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
