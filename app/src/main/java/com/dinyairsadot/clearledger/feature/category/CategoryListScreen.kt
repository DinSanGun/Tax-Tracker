package com.dinyairsadot.clearledger.feature.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.dinyairsadot.clearledger.core.ui.AnimatedDropdownMenu
import com.dinyairsadot.clearledger.core.ui.AppSnackbar
import com.dinyairsadot.clearledger.core.ui.rememberAnimatedDropdownMenuState
import com.dinyairsadot.clearledger.core.util.AllDataZipExporter
import com.dinyairsadot.clearledger.core.util.CategoriesCsvLabels
import com.dinyairsadot.clearledger.feature.invoice.rememberInvoiceCsvExportLabels
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dinyairsadot.clearledger.core.ui.SwipeDismissSnackbarHost
import com.dinyairsadot.clearledger.core.ui.categoryTopAppBarColors
import com.dinyairsadot.clearledger.feature.category.CategoryColorPreview
import androidx.compose.foundation.BorderStroke
import com.dinyairsadot.clearledger.R
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Delay before top-edge scroll correction so `animateItem` placement can show first. */
private const val TOP_EDGE_SCROLL_AFTER_PLACEMENT_MS = 64L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    isLoading: Boolean,
    categories: List<CategoryUi>,
    errorMessage: String?,
    onAddCategoryClick: () -> Unit,
    onCategoryClick: (Long) -> Unit,
    onEditCategoryClick: (Long) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    isReorderMode: Boolean,
    onEnterReorderMode: () -> Unit,
    onExitReorderMode: () -> Unit,
    onMoveCategoryUp: (Long) -> Unit,
    onMoveCategoryDown: (Long) -> Unit,
    showCategoryAddedMessage: Boolean,
    onCategoryAddedMessageShown: () -> Unit,
    viewModel: CategoryListViewModel
) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var isFileOperationInProgress by remember { mutableStateOf(false) }
    val overflowMenuState = rememberAnimatedDropdownMenuState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val invoiceCsvLabels = rememberInvoiceCsvExportLabels()
    val customFieldTitleHeaderTemplate =
        stringResource(R.string.csv_export_custom_field_title_header)
    val categoriesCsvLabels = CategoriesCsvLabels(
        categoryNameHeader = stringResource(R.string.csv_export_category_name),
        descriptionHeader = stringResource(R.string.csv_export_category_description),
        orderHeader = stringResource(R.string.csv_export_category_order),
        customFieldTitleHeader = { index ->
            String.format(customFieldTitleHeaderTemplate, index)
        }
    )

    val categoryAddedMessage = stringResource(R.string.category_added)
    val categoryDeletedMessage = stringResource(R.string.category_deleted)
    val exportAllDataMessage = stringResource(R.string.export_all_data)
    val noDataToExportMessage = stringResource(R.string.no_data_to_export)
    val exportCompletedMessage = stringResource(R.string.export_completed)
    val exportFailedMessage = stringResource(R.string.export_failed)

    LaunchedEffect(showCategoryAddedMessage) {
        if (showCategoryAddedMessage) {
            // launch snackbar in a scope that survives the key change
            coroutineScope.launch {
                snackbarHostState.showSnackbar(categoryAddedMessage)
            }

            // consume immediately so it won't re-trigger on return
            onCategoryAddedMessageShown()
        }
    }

    DisposableEffect(Unit) {
        onDispose { snackbarHostState.currentSnackbarData?.dismiss() }
    }

    // Always intercept Back at the root (start) destination.
    // Reorder mode: cancel reorder mode and stay on screen (existing behavior).
    // Normal mode: silently consume the press. This prevents NavController's
    // OnBackPressedCallback from firing on a rapid second press during a pop
    // animation, which would pop the start destination and blank the NavHost.
    BackHandler(enabled = true) {
        if (isReorderMode) {
            onExitReorderMode()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isFileOperationInProgress = true
            try {
                val allData = viewModel.loadAllDataForExport()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        AllDataZipExporter.writeZip(
                            outputStream,
                            allData,
                            invoiceCsvLabels,
                            categoriesCsvLabels
                        )
                    } ?: throw IOException("Failed to open output stream")
                }
                snackbarHostState.showSnackbar(exportCompletedMessage)
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(exportFailedMessage)
            } finally {
                isFileOperationInProgress = false
            }
        }
    }

    // Refresh invoice counts when screen resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = if (isReorderMode) {
                        stringResource(R.string.reorder_categories)
                    } else {
                        stringResource(R.string.bills_and_taxes)
                    }
                    Text(titleText)
                },
                actions = {
                    if (isReorderMode) {
                        TextButton(onClick = onExitReorderMode) {
                            Text(text = stringResource(R.string.done))
                        }
                    } else {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                        IconButton(onClick = { overflowMenuState.open() }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }
                        AnimatedDropdownMenu(
                            state = overflowMenuState,
                            onDismissRequest = {}
                        ) {
                            DropdownMenuItem(
                                text = { Text(exportAllDataMessage) },
                                enabled = !isFileOperationInProgress,
                                onClick = {
                                    overflowMenuState.dismiss()
                                    if (categories.isEmpty()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(noDataToExportMessage)
                                        }
                                    } else {
                                        val filename =
                                            "clear_ledger_all_data_export_${LocalDate.now()}.zip"
                                        exportLauncher.launch(filename)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reorder_categories)) },
                                onClick = {
                                    overflowMenuState.dismiss()
                                    onEnterReorderMode()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isReorderMode) {
                FloatingActionButton(
                    onClick = onAddCategoryClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_category)
                    )
                }
            }
        },
        snackbarHost = {
            SwipeDismissSnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    AppSnackbar(message = snackbarData.visuals.message)
                }
            )
        }
    ) { innerPadding ->
        if (isFileOperationInProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = innerPadding.calculateTopPadding())
            )
        }

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
                    onEditCategoryClick = onEditCategoryClick,
                    onRequestDeleteCategory = { id ->
                        pendingDeleteId = id
                    },
                    isReorderMode = isReorderMode,
                    onMoveCategoryUp = onMoveCategoryUp,
                    onMoveCategoryDown = onMoveCategoryDown,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        // Confirmation dialog
        pendingDeleteId?.let { id ->
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text(stringResource(R.string.delete_category)) },
                text = {
                    Text(stringResource(R.string.delete_category_confirmation))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteCategory(id)
                            pendingDeleteId = null

                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(categoryDeletedMessage)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingDeleteId = null },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.cancel))
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
    onEditCategoryClick: (Long) -> Unit,
    onRequestDeleteCategory: (Long) -> Unit,
    isReorderMode: Boolean,
    onMoveCategoryUp: (Long) -> Unit,
    onMoveCategoryDown: (Long) -> Unit,
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
                text = stringResource(R.string.no_categories_yet),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        val listState = rememberLazyListState()
        var pendingScrollToTopAfterSwap by remember { mutableStateOf(false) }
        val categoryOrderKey = remember(categories) {
            categories.joinToString(separator = ",") { it.id.toString() }
        }

        LaunchedEffect(categoryOrderKey) {
            if (!pendingScrollToTopAfterSwap) return@LaunchedEffect
            try {
                // Let LazyColumn item placement run briefly before correcting scroll;
                // immediate scrollToItem(0) can squash the top-edge swap animation.
                delay(TOP_EDGE_SCROLL_AFTER_PLACEMENT_MS)
                listState.animateScrollToItem(index = 0)
            } finally {
                pendingScrollToTopAfterSwap = false
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = categories,
                key = { category -> category.id }
            ) { category ->
                val index = categories.indexOf(category)
                CategoryItem(
                    category = category,
                    modifier = Modifier.animateItem(),
                    onClick = { if (!isReorderMode) onCategoryClick(category.id) },
                    onEditClick = { if (!isReorderMode) onEditCategoryClick(category.id) },
                    onDeleteClick = { if (!isReorderMode) onRequestDeleteCategory(category.id) },
                    isReorderMode = isReorderMode,
                    canMoveUp = index > 0,
                    canMoveDown = index < categories.lastIndex,
                    onMoveUp = {
                        if (index == 1) pendingScrollToTopAfterSwap = true
                        onMoveCategoryUp(category.id)
                    },
                    onMoveDown = {
                        if (index == 0) pendingScrollToTopAfterSwap = true
                        onMoveCategoryDown(category.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(
    category: CategoryUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isReorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val cardContainerColor = lerp(
        MaterialTheme.colorScheme.surface,
        Color.White,
        0.22f
    )

    Card(
        modifier = modifier
            .fillMaxWidth(),
        border = BorderStroke(
            width = 2.2.dp,
            color = parseColor(category.colorHex).copy(alpha = 0.65f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor
        )
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
                    .clickable(enabled = !isReorderMode) { onClick() }
                    .padding(16.dp)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (category.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.invoices_summary_dynamic,
                        category.unpaidInvoicesCount,
                        category.totalInvoicesCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isReorderMode) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 8.dp)
                ) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = stringResource(R.string.move_up)
                        )
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = stringResource(R.string.move_down)
                        )
                    }
                }
            } else {
                val itemMenuState = rememberAnimatedDropdownMenuState()
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 8.dp)
                ) {
                    IconButton(onClick = { itemMenuState.open() }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.category_options)
                        )
                    }
                    AnimatedDropdownMenu(
                        state = itemMenuState,
                        onDismissRequest = {}
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                itemMenuState.dismiss()
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                itemMenuState.dismiss()
                                onDeleteClick()
                            }
                        )
                    }
                }
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

