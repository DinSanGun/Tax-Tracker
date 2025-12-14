package com.dinyairsadot.taxtracker.feature.category

import com.dinyairsadot.taxtracker.core.domain.Category
import com.dinyairsadot.taxtracker.core.domain.CategoryRepository

object InMemoryCategoryRepository : CategoryRepository {

    // Mutable list so we can add items at runtime
    private val categories = mutableListOf(
        Category(
            id = 1,
            name = "Electricity",
            colorHex = "#FF9800",
            description = "Electricity provider bills"
        ),
        Category(
            id = 2,
            name = "Water",
            colorHex = "#2196F3",
            description = "Water and sewage"
        ),
        Category(
            id = 3,
            name = "City Taxes",
            colorHex = "#4CAF50",
            description = "Arnona / city hall payments"
        )
    )

    override suspend fun getCategories(): List<Category> {
        // Return a copy to avoid exposing the mutable list
        return categories.toList()
    }

    override suspend fun addCategory(category: Category) {
        categories.add(category)
    }

    override suspend fun deleteCategory(id: Long) {
        categories.removeAll { it.id == id }
    }

    override suspend fun updateCategory(category: Category) {
        val index = categories.indexOfFirst { it.id == category.id }
        if (index != -1) {
            categories[index] = category
        }
    }
}
