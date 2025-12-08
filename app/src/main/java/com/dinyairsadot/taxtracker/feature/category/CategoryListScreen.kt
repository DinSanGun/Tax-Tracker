package com.dinyairsadot.taxtracker.feature.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    isLoading: Boolean,
    categories: List<CategoryUi>,
    errorMessage: String?,
    onAddCategoryClick: () -> Unit,
    onCategoryClick: (Long) -> Unit,
    onDeleteCategory: (Long) -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCategoryClick
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add category"
                )
            }
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                CategoryListContent(
                    categories = categories,
                    onCategoryClick = onCategoryClick,
                    onRequestDeleteCategory = { id ->
                        pendingDeleteId = id
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        // Confirmation dialog
        pendingDeleteId?.let { id ->
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text("Delete category?") },
                text = {
                    Text(
                        "Are you sure you want to delete this category? " +
                                "All data associated with it (such as invoices) will be removed."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteCategory(id)
                            pendingDeleteId = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun CategoryListContent(
    categories: List<CategoryUi>,
    onCategoryClick: (Long) -> Unit,
    onRequestDeleteCategory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No categories yet.\nTap + to add your first category.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                CategoryItem(
                    category = category,
                    onClick = { onCategoryClick(category.id) },
                    onDeleteClick = { onRequestDeleteCategory(category.id) }
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(
    category: CategoryUi,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Color stripe on the left
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(IntrinsicSize.Min)
                    .background(parseColor(category.colorHex))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClick() }
                    .padding(16.dp)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                if (category.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "3 unpaid · 5 total invoices", // TODO: replace with real data
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete category"
                )
            }
        }
    }
}

/**
 * Helper to convert #RRGGBB to a Color.
 */
private fun parseColor(hex: String): Color {
    return try {
        if (hex.isBlank()) {
            Color.Gray
        } else {
            Color(hex.toColorInt())
        }
    } catch (e: IllegalArgumentException) {
        Color.Gray
    }
}

