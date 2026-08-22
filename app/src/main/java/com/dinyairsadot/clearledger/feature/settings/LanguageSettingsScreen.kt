package com.dinyairsadot.clearledger.feature.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinyairsadot.clearledger.R
import com.dinyairsadot.clearledger.core.data.LanguageChangeProtectionManager
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * How long interaction stays blocked (via [isApplyingLanguage]) after a language change is
 * triggered, or after the Activity is recreated as part of one. This is a pragmatic workaround
 * for a known transition window where the recreated Activity/Configuration has not fully
 * settled yet; it does not change the underlying language-switch mechanism in any way.
 */
private const val LANGUAGE_CHANGE_PROTECTION_MS = 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    onNavigateBack: () -> Unit,
    activity: Activity? = null
) {
    val context = LocalContext.current
    val viewModel: LanguageViewModel = viewModel(
        factory = LanguageViewModelFactory(context)
    )
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val availableLanguages = viewModel.getAvailableLanguages()
    val protectionManager = remember { LanguageChangeProtectionManager(context) }

    // Transient "just applied a language change" flag. A plain `remember` wouldn't survive the
    // Activity recreation triggered by the language switch, so on first composition we consume a
    // small persisted flag (set right before recreate()) to know we should keep blocking
    // interaction for a moment on the freshly recreated screen too.
    var isApplyingLanguage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (protectionManager.consumePending()) {
            isApplyingLanguage = true
        }
    }

    LaunchedEffect(isApplyingLanguage) {
        if (isApplyingLanguage) {
            delay(LANGUAGE_CHANGE_PROTECTION_MS)
            isApplyingLanguage = false
        }
    }

    // Swallow Back presses while the language switch is settling.
    BackHandler(enabled = isApplyingLanguage) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.language_settings)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isApplyingLanguage) onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_language),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                availableLanguages.forEach { language ->
                    val isSelected = currentLanguage == language.code
                    LanguageOptionItem(
                        language = language,
                        isSelected = isSelected,
                        enabled = !isApplyingLanguage,
                        onClick = {
                            if (activity != null && !isApplyingLanguage && language.code != currentLanguage) {
                                // Persist the protection flag first so it survives even if
                                // recreate() tears the process state down immediately after.
                                protectionManager.markPending()
                                isApplyingLanguage = true
                                val locale = Locale.forLanguageTag(language.code)
                                viewModel.changeLanguage(locale, activity)
                            }
                        }
                    )
                }
            }

            if (isApplyingLanguage) {
                LanguageChangeProtectionOverlay()
            }
        }
    }
}

/**
 * Full-bleed scrim that consumes all pointer input so nothing underneath (language rows, etc.)
 * can be tapped while the language switch + Activity recreation is settling.
 */
@Composable
private fun LanguageChangeProtectionOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.applying_language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LanguageOptionItem(
    language: LanguageOption,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = language.englishName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
