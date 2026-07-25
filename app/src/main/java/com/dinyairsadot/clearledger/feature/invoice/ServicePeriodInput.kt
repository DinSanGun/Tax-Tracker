@file:OptIn(ExperimentalFoundationApi::class)

package com.dinyairsadot.clearledger.feature.invoice

import androidx.compose.foundation.ExperimentalFoundationApi
import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.dinyairsadot.clearledger.R
import com.dinyairsadot.clearledger.core.domain.ServicePeriodMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Renders the "Service Period" section for invoice add/edit screens.
 *
 * The [mode] is fixed per category (Policy A) and is never changeable per invoice.
 *
 * DATE mode  – two free-text + calendar-picker fields (Start / End).
 * MONTH mode – a single month picker for the start month; an optional end-month
 *              picker revealed by "Add end month" button.
 *
 * All mutable state is hoisted to the caller so that it survives ViewModel-driven
 * recompositions and orientation changes via rememberSaveable.
 */
@Composable
fun ServicePeriodInput(
    mode: ServicePeriodMode,

    // ── DATE mode ────────────────────────────────────────────────────────────
    startDateText: String,
    onStartDateTextChange: (String) -> Unit,
    startDateError: String?,
    onStartDateTouched: () -> Unit,
    endDateText: String,
    onEndDateTextChange: (String) -> Unit,
    endDateError: String?,
    onEndDateTouched: () -> Unit,

    // ── MONTH mode ───────────────────────────────────────────────────────────
    startYear: Int,
    startMonth: Int,          // 1-12
    onStartMonthSelected: (year: Int, month: Int) -> Unit,
    startMonthError: String?,
    showEndMonth: Boolean,
    onToggleEndMonth: () -> Unit,
    endYear: Int,
    endMonth: Int,            // 1-12
    onEndMonthSelected: (year: Int, month: Int) -> Unit,
    endMonthError: String?,

    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Column(modifier = modifier) {
        when (mode) {
            ServicePeriodMode.DATE -> DateModeContent(
                startDateText = startDateText,
                onStartDateTextChange = onStartDateTextChange,
                startDateError = startDateError,
                onStartDateTouched = onStartDateTouched,
                endDateText = endDateText,
                onEndDateTextChange = onEndDateTextChange,
                endDateError = endDateError,
                onEndDateTouched = onEndDateTouched,
                dateFormatter = dateFormatter,
                context = context
            )

            ServicePeriodMode.MONTH -> MonthModeContent(
                startYear = startYear,
                startMonth = startMonth,
                onStartMonthSelected = onStartMonthSelected,
                startMonthError = startMonthError,
                showEndMonth = showEndMonth,
                onToggleEndMonth = onToggleEndMonth,
                endYear = endYear,
                endMonth = endMonth,
                onEndMonthSelected = onEndMonthSelected,
                endMonthError = endMonthError
            )
        }
    }
}

/**
 * Toggle between [ServicePeriodMode.MONTH] and [ServicePeriodMode.DATE].
 *
 * At normal font scale the two mode buttons sit side by side, at their natural
 * (compact) content width, aligned to the reading/start side of the form.
 * At increased accessibility font scale (>= [STACKED_LAYOUT_FONT_SCALE_THRESHOLD])
 * they stack vertically instead, still aligned to the start side, so long
 * localized labels (e.g. Hebrew "תאריכים מדויקים") never split into partial-word
 * fragments.
 */
private const val STACKED_LAYOUT_FONT_SCALE_THRESHOLD = 1.3f

@Composable
fun ServicePeriodModeSelector(
    selectedMode: ServicePeriodMode,
    onModeSelected: (ServicePeriodMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val useStackedLayout = LocalDensity.current.fontScale >= STACKED_LAYOUT_FONT_SCALE_THRESHOLD
    // Space below the selector, before the month/date controls that follow it.
    // Kept identical for both layouts so the gap to the next control never changes
    // depending on font scale.
    val containerModifier = modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp)

    if (useStackedLayout) {
        Column(
            modifier = containerModifier,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ServicePeriodModeButton(
                label = stringResource(R.string.service_period_mode_month),
                selected = selectedMode == ServicePeriodMode.MONTH,
                onClick = { onModeSelected(ServicePeriodMode.MONTH) },
                modifier = Modifier.wrapContentWidth()
            )
            ServicePeriodModeButton(
                label = stringResource(R.string.service_period_mode_dates),
                selected = selectedMode == ServicePeriodMode.DATE,
                onClick = { onModeSelected(ServicePeriodMode.DATE) },
                modifier = Modifier.wrapContentWidth()
            )
        }
    } else {
        Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServicePeriodModeButton(
                label = stringResource(R.string.service_period_mode_month),
                selected = selectedMode == ServicePeriodMode.MONTH,
                onClick = { onModeSelected(ServicePeriodMode.MONTH) }
            )
            ServicePeriodModeButton(
                label = stringResource(R.string.service_period_mode_dates),
                selected = selectedMode == ServicePeriodMode.DATE,
                onClick = { onModeSelected(ServicePeriodMode.DATE) }
            )
        }
    }
}

/**
 * Compact rounded outlined button used by [ServicePeriodModeSelector]. Uses its
 * natural content width (never stretched) so the two choices read as small
 * selectable controls rather than large tap targets with floating centered text.
 */
@Composable
private fun ServicePeriodModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    val borderWidth = if (selected) 2.dp else 1.dp
    val containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DATE mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DateModeContent(
    startDateText: String,
    onStartDateTextChange: (String) -> Unit,
    startDateError: String?,
    onStartDateTouched: () -> Unit,
    endDateText: String,
    onEndDateTextChange: (String) -> Unit,
    endDateError: String?,
    onEndDateTouched: () -> Unit,
    dateFormatter: DateTimeFormatter,
    context: android.content.Context
) {
    val coroutineScope = rememberCoroutineScope()
    val dateTemplateColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val dateTemplateTransformation = remember(dateTemplateColor) {
        DateTemplateVisualTransformation(templateColor = dateTemplateColor)
    }
    val readOnlyDateFieldColors = OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledBorderColor = MaterialTheme.colorScheme.outline,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surface
    )
    fun showPicker(currentText: String, onPicked: (String) -> Unit) {
        val cal = java.util.Calendar.getInstance()
        currentText.trim().takeIf { it.isNotBlank() }?.let {
            runCatching {
                val d = parseStrictDateOrNull(it) ?: return@runCatching
                cal.set(d.year, d.monthValue - 1, d.dayOfMonth)
            }
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onPicked(LocalDate.of(year, month + 1, day).format(dateFormatter))
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Start
    val startBringIntoViewRequester = remember { BringIntoViewRequester() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var startFieldValue by remember {
            mutableStateOf(
                TextFieldValue(
                    text = startDateText,
                    selection = TextRange(startDateText.length)
                )
            )
        }
        LaunchedEffect(startDateText) {
            if (startDateText != startFieldValue.text) {
                startFieldValue = TextFieldValue(
                    text = startDateText,
                    selection = TextRange(startDateText.length)
                )
            }
        }
        OutlinedTextField(
            value = startFieldValue,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .weight(1f)
                .bringIntoViewRequester(startBringIntoViewRequester)
                .clickable { showPicker(startDateText, onStartDateTextChange) }
                .onFocusChanged { fs ->
                    if (fs.isFocused) {
                        coroutineScope.launch {
                            delay(250)
                            startBringIntoViewRequester.bringIntoView()
                        }
                    }
                    if (!fs.isFocused) {
                        onStartDateTouched()
                    }
                },
            label = { Text(stringResource(R.string.service_period_start_short)) },
            visualTransformation = dateTemplateTransformation,
            supportingText = {
                if (startDateError != null) Text(startDateError)
                else Text(stringResource(R.string.format_hint_dd_mm_yyyy))
            },
            isError = startDateError != null,
            colors = readOnlyDateFieldColors
        )
        IconButton(onClick = { showPicker(startDateText, onStartDateTextChange) }) {
            Icon(Icons.Filled.CalendarToday, contentDescription = stringResource(R.string.pick_date))
        }
    }

    // End
    val endBringIntoViewRequester = remember { BringIntoViewRequester() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var endFieldValue by remember {
            mutableStateOf(
                TextFieldValue(
                    text = endDateText,
                    selection = TextRange(endDateText.length)
                )
            )
        }
        LaunchedEffect(endDateText) {
            if (endDateText != endFieldValue.text) {
                endFieldValue = TextFieldValue(
                    text = endDateText,
                    selection = TextRange(endDateText.length)
                )
            }
        }
        OutlinedTextField(
            value = endFieldValue,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .weight(1f)
                .bringIntoViewRequester(endBringIntoViewRequester)
                .clickable { showPicker(endDateText, onEndDateTextChange) }
                .onFocusChanged { fs ->
                    if (fs.isFocused) {
                        coroutineScope.launch {
                            delay(250)
                            endBringIntoViewRequester.bringIntoView()
                        }
                    }
                    if (!fs.isFocused) {
                        onEndDateTouched()
                    }
                },
            label = { Text(stringResource(R.string.service_period_end_short)) },
            visualTransformation = dateTemplateTransformation,
            supportingText = {
                if (endDateError != null) Text(endDateError)
                else Text(stringResource(R.string.format_hint_dd_mm_yyyy))
            },
            isError = endDateError != null,
            colors = readOnlyDateFieldColors
        )
        IconButton(onClick = { showPicker(endDateText, onEndDateTextChange) }) {
            Icon(Icons.Filled.CalendarToday, contentDescription = stringResource(R.string.pick_date))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MONTH mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MonthModeContent(
    startYear: Int,
    startMonth: Int,
    onStartMonthSelected: (year: Int, month: Int) -> Unit,
    startMonthError: String?,
    showEndMonth: Boolean,
    onToggleEndMonth: () -> Unit,
    endYear: Int,
    endMonth: Int,
    onEndMonthSelected: (year: Int, month: Int) -> Unit,
    endMonthError: String?
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val pickerFieldColors = OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledBorderColor = MaterialTheme.colorScheme.outline,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surface
    )

    if (showStartPicker) {
        MonthPickerDialog(
            initialYear = startYear,
            initialMonth = startMonth,
            onDismiss = { showStartPicker = false },
            onConfirm = { y, m ->
                onStartMonthSelected(y, m)
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        MonthPickerDialog(
            initialYear = endYear,
            initialMonth = endMonth,
            onDismiss = { showEndPicker = false },
            onConfirm = { y, m ->
                onEndMonthSelected(y, m)
                showEndPicker = false
            }
        )
    }

    val locale = Locale.getDefault()
    fun monthLabel(year: Int, month: Int): String =
        YearMonth.of(year, month).let {
            "${it.month.getDisplayName(TextStyle.SHORT, locale)} $year"
        }

    // Start month field
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = monthLabel(startYear, startMonth),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .weight(1f)
                .clickable { showStartPicker = true },
            label = null,
            isError = startMonthError != null,
            supportingText = { startMonthError?.let { Text(it) } },
            colors = pickerFieldColors
        )
        IconButton(onClick = { showStartPicker = true }) {
            Icon(Icons.Filled.CalendarToday, contentDescription = stringResource(R.string.pick_month))
        }
    }

    // End month field (only visible when range is active)
    if (showEndMonth) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = monthLabel(endYear, endMonth),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showEndPicker = true },
                label = null,
                isError = endMonthError != null,
                supportingText = { endMonthError?.let { Text(it) } },
                colors = pickerFieldColors
            )
            IconButton(onClick = { showEndPicker = true }) {
                Icon(Icons.Filled.CalendarToday, contentDescription = stringResource(R.string.pick_month))
            }
        }
    }

    // Toggle button
    TextButton(
        onClick = onToggleEndMonth,
        modifier = Modifier.padding(start = 0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Text(
            if (showEndMonth) stringResource(R.string.remove_end_month)
            else stringResource(R.string.add_end_month)
        )
    }
}

/**
 * Optional exact-date field for payment date / due date.
 * Uses OutlinedTextField + DatePickerDialog. No monthly mode toggle.
 */
@Composable
fun ExactDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    onFocusLost: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val coroutineScope = rememberCoroutineScope()
    val dateTemplateColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val dateTemplateTransformation = remember(dateTemplateColor) {
        DateTemplateVisualTransformation(templateColor = dateTemplateColor)
    }
    val readOnlyDateFieldColors = OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledBorderColor = MaterialTheme.colorScheme.outline,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surface
    )

    fun showPicker(currentText: String, onPicked: (String) -> Unit) {
        val cal = java.util.Calendar.getInstance()
        currentText.trim().takeIf { it.isNotBlank() }?.let {
            runCatching {
                val d = parseStrictDateOrNull(it) ?: return@runCatching
                cal.set(d.year, d.monthValue - 1, d.dayOfMonth)
            }
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onPicked(LocalDate.of(year, month + 1, day).format(dateFormatter))
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var fieldValue by remember {
            mutableStateOf(
                TextFieldValue(
                    text = value,
                    selection = TextRange(value.length)
                )
            )
        }
        LaunchedEffect(value) {
            if (value != fieldValue.text) {
                fieldValue = TextFieldValue(
                    text = value,
                    selection = TextRange(value.length)
                )
            }
        }
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .weight(1f)
                .bringIntoViewRequester(bringIntoViewRequester)
                .clickable { showPicker(value, onValueChange) }
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        coroutineScope.launch {
                            delay(250)
                            bringIntoViewRequester.bringIntoView()
                        }
                    } else {
                        onFocusLost()
                    }
                },
            label = { Text(label) },
            visualTransformation = dateTemplateTransformation,
            supportingText = {
                if (error != null) Text(error)
                else Text(stringResource(R.string.format_hint_dd_mm_yyyy))
            },
            isError = error != null,
            colors = readOnlyDateFieldColors
        )
        IconButton(onClick = { showPicker(value, onValueChange) }) {
            Icon(Icons.Filled.CalendarToday, contentDescription = stringResource(R.string.pick_date))
        }
    }
}
