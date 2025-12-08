package com.dinyairsadot.taxtracker.feature.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoryScreen(
    onNavigateBack: () -> Unit,
    onSaveCategory: (name: String, colorHex: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var colorError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Category") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (nameError != null && it.isNotBlank()) {
                        nameError = null
                    }
                },
                label = { Text("Category name *") },
                isError = nameError != null,
                supportingText = {
                    if (nameError != null) {
                        Text(nameError!!)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = colorHex,
                onValueChange = {
                    colorHex = it
                    if (colorError != null) {
                        colorError = null
                    }
                },
                label = { Text("Color hex (#RRGGBB, optional)") },
                placeholder = { Text("#FF9800") },
                isError = colorError != null,
                supportingText = {
                    if (colorError != null) {
                        Text(colorError!!)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    var hasError = false

                    // Name must not be blank
                    if (name.isBlank()) {
                        nameError = "Name is required"
                        hasError = true
                    }

                    // Color must be either empty or look like #RRGGBB
                    if (colorHex.isNotBlank()) {
                        val pattern = Regex("^#[0-9A-Fa-f]{6}\$")
                        if (!pattern.matches(colorHex.trim())) {
                            colorError = "Use format #RRGGBB (e.g. #FF9800)"
                            hasError = true
                        }
                    }

                    if (!hasError) {
                        onSaveCategory(
                            name.trim(),
                            colorHex.trim(),
                            description.trim()
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
