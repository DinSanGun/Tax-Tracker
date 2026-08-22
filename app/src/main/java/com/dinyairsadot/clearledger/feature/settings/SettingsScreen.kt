package com.dinyairsadot.clearledger.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dinyairsadot.clearledger.R
import com.dinyairsadot.clearledger.core.domain.AppTextSize
import com.dinyairsadot.clearledger.core.ui.AppSnackbar
import com.dinyairsadot.clearledger.core.ui.SwipeDismissSnackbarHost
import com.dinyairsadot.clearledger.core.util.backup.BackupPayload
import com.dinyairsadot.clearledger.core.util.backup.BackupValidationResult
import com.dinyairsadot.clearledger.core.util.backup.BackupZipExporter
import com.dinyairsadot.clearledger.feature.category.CategoryListViewModel
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Central Settings screen reached from the Category List top bar. Groups access to
 * existing Language, Text size, Backup/Restore, Reset, and About functionality that
 * previously lived directly in the Category List overflow menu.
 *
 * This screen does not own any business logic itself: language/about navigate to their
 * existing dedicated screens, and backup/restore/reset reuse the same [CategoryListViewModel]
 * calls and SAF launchers that previously lived in `CategoryListScreen`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLanguageSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    currentTextSize: AppTextSize,
    onTextSizeSelected: (AppTextSize) -> Unit,
    viewModel: CategoryListViewModel
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showTextSizeDialog by remember { mutableStateOf(false) }
    var pendingRestorePayload by remember { mutableStateOf<BackupPayload?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var isFileOperationInProgress by remember { mutableStateOf(false) }

    val createBackupMessage = stringResource(R.string.create_backup)
    val backupCreatedMessage = stringResource(R.string.backup_created)
    val backupFailedMessage = stringResource(R.string.backup_failed)
    val restoreBackupMessage = stringResource(R.string.restore_backup)
    val restoreBackupDialogTitle = stringResource(R.string.restore_backup_dialog_title)
    val restoreBackupDialogMessage = stringResource(R.string.restore_backup_dialog_message)
    val restoreButtonLabel = stringResource(R.string.restore)
    val restoreCompletedMessage = stringResource(R.string.restore_completed)
    val restoreFailedMessage = stringResource(R.string.restore_failed)
    val restoreInvalidBackupMessage = stringResource(R.string.restore_invalid_backup)
    val restoreUnsupportedVersionMessage = stringResource(R.string.restore_unsupported_version)
    val resetAllDataMessage = stringResource(R.string.reset_all_data)
    val resetAllDataDialogTitle = stringResource(R.string.reset_all_data_dialog_title)
    val resetAllDataDialogMessage = stringResource(R.string.reset_all_data_dialog_message)
    val resetButtonLabel = stringResource(R.string.reset)
    val dataResetCompleteMessage = stringResource(R.string.data_reset_complete)
    val resetFailedMessage = stringResource(R.string.reset_failed)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isFileOperationInProgress = true
            try {
                val backupData = viewModel.loadAllDataForBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        BackupZipExporter.writeZip(outputStream, backupData)
                    } ?: throw IOException("Failed to open output stream")
                }
                snackbarHostState.showSnackbar(backupCreatedMessage)
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(backupFailedMessage)
            } finally {
                isFileOperationInProgress = false
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isFileOperationInProgress = true
            try {
                val result = withContext(Dispatchers.IO) {
                    viewModel.validateAndParseBackup(uri)
                }
                when (result) {
                    is BackupValidationResult.Valid -> pendingRestorePayload = result.payload
                    is BackupValidationResult.UnsupportedVersion -> {
                        snackbarHostState.showSnackbar(restoreUnsupportedVersionMessage)
                    }
                    is BackupValidationResult.Invalid -> {
                        snackbarHostState.showSnackbar(restoreInvalidBackupMessage)
                    }
                }
            } finally {
                isFileOperationInProgress = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SwipeDismissSnackbarHost(hostState = snackbarHostState) { snackbarData ->
                AppSnackbar(message = snackbarData.visuals.message)
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isFileOperationInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Column(modifier = Modifier.fillMaxSize()) {
                SettingsSectionHeader(text = stringResource(R.string.settings_section_general))
                SettingsRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language_settings),
                    onClick = onLanguageSettingsClick
                )
                SettingsRow(
                    icon = Icons.Default.FormatSize,
                    title = stringResource(R.string.text_size_label),
                    subtitle = currentTextSizeLabel(currentTextSize),
                    onClick = { showTextSizeDialog = true }
                )

                HorizontalDivider()

                SettingsSectionHeader(text = stringResource(R.string.settings_section_data))
                SettingsRow(
                    icon = Icons.Default.SaveAlt,
                    title = createBackupMessage,
                    enabled = !isFileOperationInProgress,
                    onClick = {
                        val filename = "clear_ledger_backup_${LocalDate.now()}.zip"
                        backupLauncher.launch(filename)
                    }
                )
                SettingsRow(
                    icon = Icons.Default.SettingsBackupRestore,
                    title = restoreBackupMessage,
                    enabled = !isFileOperationInProgress,
                    onClick = { restoreLauncher.launch(arrayOf("application/zip")) }
                )
                SettingsRow(
                    icon = Icons.Default.DeleteForever,
                    title = resetAllDataMessage,
                    enabled = !isFileOperationInProgress,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showResetConfirmDialog = true }
                )

                HorizontalDivider()

                SettingsSectionHeader(text = stringResource(R.string.settings_section_about))
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about),
                    onClick = onAboutClick
                )
            }
        }

        if (showTextSizeDialog) {
            AlertDialog(
                onDismissRequest = { showTextSizeDialog = false },
                title = { Text(stringResource(R.string.text_size_label)) },
                text = {
                    Column {
                        TextSizeOptionRow(
                            label = stringResource(R.string.text_size_normal),
                            selected = currentTextSize == AppTextSize.NORMAL,
                            onClick = {
                                onTextSizeSelected(AppTextSize.NORMAL)
                                showTextSizeDialog = false
                            }
                        )
                        TextSizeOptionRow(
                            label = stringResource(R.string.text_size_large),
                            selected = currentTextSize == AppTextSize.LARGE,
                            onClick = {
                                onTextSizeSelected(AppTextSize.LARGE)
                                showTextSizeDialog = false
                            }
                        )
                        TextSizeOptionRow(
                            label = stringResource(R.string.text_size_extra_large),
                            selected = currentTextSize == AppTextSize.EXTRA_LARGE,
                            onClick = {
                                onTextSizeSelected(AppTextSize.EXTRA_LARGE)
                                showTextSizeDialog = false
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showTextSizeDialog = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        pendingRestorePayload?.let { payload ->
            AlertDialog(
                onDismissRequest = { pendingRestorePayload = null },
                title = { Text(restoreBackupDialogTitle) },
                text = { Text(restoreBackupDialogMessage) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingRestorePayload = null
                            coroutineScope.launch {
                                isFileOperationInProgress = true
                                try {
                                    viewModel.performRestore(payload)
                                    viewModel.refresh()
                                    snackbarHostState.showSnackbar(restoreCompletedMessage)
                                } catch (_: Exception) {
                                    snackbarHostState.showSnackbar(restoreFailedMessage)
                                } finally {
                                    isFileOperationInProgress = false
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(text = restoreButtonLabel)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingRestorePayload = null },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text(resetAllDataDialogTitle) },
                text = { Text(resetAllDataDialogMessage) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetConfirmDialog = false
                            coroutineScope.launch {
                                isFileOperationInProgress = true
                                try {
                                    viewModel.performReset()
                                    viewModel.refresh()
                                    snackbarHostState.showSnackbar(dataResetCompleteMessage)
                                } catch (_: Exception) {
                                    snackbarHostState.showSnackbar(resetFailedMessage)
                                } finally {
                                    isFileOperationInProgress = false
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(text = resetButtonLabel)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetConfirmDialog = false },
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
private fun currentTextSizeLabel(textSize: AppTextSize): String = when (textSize) {
    AppTextSize.NORMAL -> stringResource(R.string.text_size_normal)
    AppTextSize.LARGE -> stringResource(R.string.text_size_large)
    AppTextSize.EXTRA_LARGE -> stringResource(R.string.text_size_extra_large)
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    titleColor: Color = Color.Unspecified
) {
    val iconTint = if (titleColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        titleColor
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick),
        headlineContent = {
            Text(text = title, color = titleColor)
        },
        supportingContent = subtitle?.let { { Text(text = it) } },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/**
 * A single "Normal"/"Large" choice row in the text size dialog. Tapping the row
 * (label or radio button) immediately applies that choice via [onClick].
 */
@Composable
private fun TextSizeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
