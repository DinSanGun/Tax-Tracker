package com.dinyairsadot.taxtracker.feature.category

import com.dinyairsadot.taxtracker.core.domain.Category
import com.dinyairsadot.taxtracker.core.domain.CategoryRepository

class InMemoryCategoryRepository : CategoryRepository {

    override suspend fun getCategories(): List<Category> {
        // Same data you used before in CategoryListScreen dummy list
        return listOf(
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
    }
}
