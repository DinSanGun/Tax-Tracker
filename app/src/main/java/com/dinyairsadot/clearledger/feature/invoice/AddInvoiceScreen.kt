@file:OptIn(ExperimentalFoundationApi::class)

package com.dinyairsadot.clearledger.feature.invoice

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dinyairsadot.clearledger.R
import com.dinyairsadot.clearledger.core.domain.InvoiceCurrency
import com.dinyairsadot.clearledger.core.domain.PaymentMethodOption
import com.dinyairsadot.clearledger.core.domain.PaymentStatus
import com.dinyairsadot.clearledger.core.domain.ServicePeriodMode
import com.dinyairsadot.clearledger.core.ui.SwipeDismissSnackbarHost
import com.dinyairsadot.clearledger.core.ui.UnsavedChangesDialog
import com.dinyairsadot.clearledger.core.ui.categoryTopAppBarColors
import com.dinyairsadot.clearledger.core.ui.requestAnchoredDropdownExpansion
import com.dinyairsadot.clearledger.core.util.AttachmentUtil
import androidx.compose.material3.LocalContentColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class InvoiceFirstInvalidAnchor {
    DocumentNumber,
    Amount,
    ServicePeriod
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInvoiceScreen(
    categoryId: Long,
    categoryName: String?,
    categoryColorHex: String?,
    categoryCustomFieldTitles: List<String>,
    onSaveInvoice: (
        documentNumber: String,
        amountDue: Double,
        paymentStatus: PaymentStatus,
        servicePeriodStartText: String,
        servicePeriodEndText: String,
        servicePeriodMode: ServicePeriodMode,
        paymentDate: LocalDate?,
        dueDate: LocalDate?,
        paymentMethod: String?,
        numberOfPayments: String?,
        confirmationNumber: String?,
        vendorName: String?,
        notes: String,
        customFieldValues: List<String>,
        amountCurrency: InvoiceCurrency,
        attachmentUri: String?
    ) -> Unit,
    isAttachmentUriInUse: suspend (String) -> Boolean,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val documentNumberBringIntoViewRequester = remember { BringIntoViewRequester() }
    val amountBringIntoViewRequester = remember { BringIntoViewRequester() }
    val servicePeriodBringIntoViewRequester = remember { BringIntoViewRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    // Main required fields
    var documentNumberText by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var paymentStatus by rememberSaveable { mutableStateOf(PaymentStatus.NOT_PAID) }
    var servicePeriodStartText by rememberSaveable { mutableStateOf("") }
    var servicePeriodEndText by rememberSaveable { mutableStateOf("") }
    var servicePeriodMode by rememberSaveable { mutableStateOf(ServicePeriodMode.MONTH) }

    val today = LocalDate.now()
    var startYear by rememberSaveable { mutableStateOf(today.year) }
    var startMonth by rememberSaveable { mutableStateOf(today.monthValue) }
    var endYear by rememberSaveable { mutableStateOf(today.year) }
    var endMonth by rememberSaveable { mutableStateOf(today.monthValue) }
    var showEndMonth by rememberSaveable { mutableStateOf(false) }

    // Conditional fields: Paid/Credit group
    var paymentDateText by rememberSaveable { mutableStateOf("") }
    var paymentMethod by rememberSaveable { mutableStateOf(PaymentMethodOption.NOT_SPECIFIED.value) }
    var paymentMethodOtherText by rememberSaveable { mutableStateOf("") }
    var numberOfPayments by rememberSaveable { mutableStateOf("") }
    var confirmationNumber by rememberSaveable { mutableStateOf("") }

    // Conditional fields: Not paid group
    var dueDateText by rememberSaveable { mutableStateOf("") }

    var amountCurrencyCode by rememberSaveable { mutableStateOf(InvoiceCurrency.ILS.name) }
    var vendorName by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var customFieldValues by rememberSaveable {
        mutableStateOf(List(categoryCustomFieldTitles.size) { "" })
    }
    var attachmentUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }

    // Validation state
    var documentNumberError by rememberSaveable { mutableStateOf<String?>(null) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var paymentStatusError by rememberSaveable { mutableStateOf<String?>(null) }
    var servicePeriodStartError by rememberSaveable { mutableStateOf<String?>(null) }
    var servicePeriodEndError by rememberSaveable { mutableStateOf<String?>(null) }
    var startMonthError by rememberSaveable { mutableStateOf<String?>(null) }
    var endMonthError by rememberSaveable { mutableStateOf<String?>(null) }

    var documentNumberTouched by rememberSaveable { mutableStateOf(false) }
    var amountTouched by rememberSaveable { mutableStateOf(false) }
    var servicePeriodStartTouched by rememberSaveable { mutableStateOf(false) }
    var servicePeriodEndTouched by rememberSaveable { mutableStateOf(false) }

    fun handleSave() {
        var hasError = false
        var firstInvalid: InvoiceFirstInvalidAnchor? = null
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        if (documentNumberText.trim().isBlank()) {
            documentNumberError = context.getString(R.string.invoice_number_required)
            hasError = true
            if (firstInvalid == null) firstInvalid = InvoiceFirstInvalidAnchor.DocumentNumber
        } else {
            documentNumberError = null
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            amountError = context.getString(R.string.enter_valid_amount)
            hasError = true
            if (firstInvalid == null) firstInvalid = InvoiceFirstInvalidAnchor.Amount
        } else {
            amountError = null
        }

        paymentStatusError = null

        var finalStartText = ""
        var finalEndText = ""

        if (servicePeriodMode == ServicePeriodMode.MONTH) {
            val actualEndYear = if (showEndMonth) endYear else startYear
            val actualEndMonth = if (showEndMonth) endMonth else startMonth
            if (showEndMonth) {
                val startYM = YearMonth.of(startYear, startMonth)
                val endYM = YearMonth.of(actualEndYear, actualEndMonth)
                if (endYM.isBefore(startYM)) {
                    endMonthError = context.getString(R.string.service_period_end_before_start)
                    hasError = true
                    if (firstInvalid == null) firstInvalid = InvoiceFirstInvalidAnchor.ServicePeriod
                } else {
                    endMonthError = null
                }
            } else {
                endMonthError = null
            }
            startMonthError = null
            if (!hasError) {
                finalStartText = LocalDate.of(startYear, startMonth, 1).format(dateFormatter)
                finalEndText = YearMonth.of(actualEndYear, actualEndMonth).atEndOfMonth().format(dateFormatter)
            }
        } else {
            val trimmedStart = servicePeriodStartText.trim()
            val trimmedEnd = servicePeriodEndText.trim()
            if (trimmedStart.isBlank() && trimmedEnd.isBlank()) {
                servicePeriodStartError = null
                servicePeriodEndError = null
                finalStartText = ""
                finalEndText = ""
            } else {
                val startDate = if (trimmedStart.isBlank()) {
                    servicePeriodStartError = context.getString(R.string.date_required)
                    hasError = true
                    if (firstInvalid == null) firstInvalid = InvoiceFirstInvalidAnchor.ServicePeriod
                    null
                } else {
                    servicePeriodStartError = null
                    parseStrictDateOrNull(trimmedStart)
                }

                val endDate = if (trimmedEnd.isBlank()) {
                    servicePeriodEndError = context.getString(R.string.date_required)
                    hasError = true
                    if (firstInvalid == null) firstInvalid = InvoiceFirstInvalidAnchor.ServicePeriod
                    null
                } else {
                    servicePeriodEndError = null
                    parseStrictDateOrNull(trimmedEnd)
                }

                if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                    servicePeriodEndError = context.getString(R.string.service_period_end_before_start)
                    hasError = true
                    if (firstInvalid == null) firstInvalid = InvoiceFirstInvalidAnchor.ServicePeriod
                }
                finalStartText = trimmedStart
                finalEndText = trimmedEnd
            }
        }

        if (hasError) {
            coroutineScope.launch {
                delay(50)
                when (firstInvalid) {
                    InvoiceFirstInvalidAnchor.DocumentNumber ->
                        documentNumberBringIntoViewRequester.bringIntoView()
                    InvoiceFirstInvalidAnchor.Amount ->
                        amountBringIntoViewRequester.bringIntoView()
                    InvoiceFirstInvalidAnchor.ServicePeriod ->
                        servicePeriodBringIntoViewRequester.bringIntoView()
                    null -> { }
                }
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.please_fix_highlighted_fields),
                    withDismissAction = true
                )
            }
            return
        }

        val paymentDateInput = paymentDateText.trim()
        val dueDateInput = dueDateText.trim()

        val paymentDate = if (paymentDateInput.isBlank()) {
            null
        } else {
            parseStrictDateOrNull(paymentDateInput)
        }
        val dueDate = if (dueDateInput.isBlank()) {
            null
        } else {
            parseStrictDateOrNull(dueDateInput)
        }

        val finalPaymentMethod = when (paymentMethod) {
            PaymentMethodOption.OTHER.value -> {
                val custom = paymentMethodOtherText.trim()
                if (custom.isNotBlank()) custom else PaymentMethodOption.OTHER.value
            }
            PaymentMethodOption.NOT_SPECIFIED.value, "" -> ""
            else -> paymentMethod
        }

        onSaveInvoice(
            documentNumberText.trim(),
            amount!!,
            paymentStatus,
            finalStartText,
            finalEndText,
            servicePeriodMode,
            paymentDate,
            dueDate,
            finalPaymentMethod.takeIf { it.isNotBlank() },
            numberOfPayments.takeIf { it.isNotBlank() },
            confirmationNumber.takeIf { it.isNotBlank() },
            vendorName.trim().takeIf { it.isNotBlank() },
            notes.trim(),
            customFieldValues,
            runCatching { InvoiceCurrency.valueOf(amountCurrencyCode) }.getOrDefault(InvoiceCurrency.ILS),
            attachmentUri
        )
        onNavigateBack()
    }

    val originalSnapshot = remember(categoryCustomFieldTitles.size) {
        editableInvoiceSnapshot(
            documentNumber = "",
            amount = "",
            currencyCode = InvoiceCurrency.ILS.name,
            paymentStatus = PaymentStatus.NOT_PAID,
            servicePeriodMode = ServicePeriodMode.MONTH,
            servicePeriodStartText = "",
            servicePeriodEndText = "",
            startYear = today.year,
            startMonth = today.monthValue,
            showEndMonth = false,
            endYear = today.year,
            endMonth = today.monthValue,
            paymentDateText = "",
            dueDateText = "",
            paymentMethod = PaymentMethodOption.NOT_SPECIFIED.value,
            paymentMethodOtherText = "",
            numberOfPayments = "",
            confirmationNumber = "",
            vendorName = "",
            notes = "",
            customFieldValues = List(categoryCustomFieldTitles.size) { "" },
            attachmentUri = ""
        )
    }
    val hasUnsavedChanges = editableInvoiceSnapshot(
        documentNumber = documentNumberText,
        amount = amountText,
        currencyCode = amountCurrencyCode,
        paymentStatus = paymentStatus,
        servicePeriodMode = servicePeriodMode,
        servicePeriodStartText = servicePeriodStartText,
        servicePeriodEndText = servicePeriodEndText,
        startYear = startYear,
        startMonth = startMonth,
        showEndMonth = showEndMonth,
        endYear = endYear,
        endMonth = endMonth,
        paymentDateText = paymentDateText,
        dueDateText = dueDateText,
        paymentMethod = paymentMethod,
        paymentMethodOtherText = paymentMethodOtherText,
        numberOfPayments = numberOfPayments,
        confirmationNumber = confirmationNumber,
        vendorName = vendorName,
        notes = notes,
        customFieldValues = customFieldValues,
        attachmentUri = attachmentUri.orEmpty()
    ) != originalSnapshot

    fun onBackRequested() {
        if (hasUnsavedChanges) {
            showUnsavedChangesDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        onBackRequested()
    }

    val showPaidGroup = paymentStatus == PaymentStatus.PAID
    val showDueDateGroup = paymentStatus == PaymentStatus.NOT_PAID

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = { SwipeDismissSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_invoice_title)) },
                colors = categoryTopAppBarColors(categoryColorHex),
                navigationIcon = {
                    IconButton(onClick = ::onBackRequested) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { handleSave() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = LocalContentColor.current
                        )
                    ) {
                        Text(text = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showUnsavedChangesDialog) {
            UnsavedChangesDialog(
                onSave = {
                    showUnsavedChangesDialog = false
                    handleSave()
                },
                onDiscard = {
                    showUnsavedChangesDialog = false
                    // A freshly-picked attachment was never saved on any invoice; release its
                    // persisted read permission, unless another saved invoice already
                    // references the exact same Uri.
                    val pendingUri = attachmentUri
                    coroutineScope.launch {
                        AttachmentUtil.releaseIfUnreferenced(
                            context = context,
                            candidateUri = pendingUri,
                            isReferencedElsewhere = isAttachmentUriInUse
                        )
                    }
                    onNavigateBack()
                },
                onDismiss = { showUnsavedChangesDialog = false }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)

        ) {
            Spacer(modifier = Modifier.padding(top = 8.dp))

            InvoiceFormSectionHeader(
                title = stringResource(R.string.invoice_form_section_basic_details),
                isFirstSection = true
            )

            // Invoice number *
            OutlinedTextField(
                value = documentNumberText,
                onValueChange = {
                    documentNumberText = it
                    documentNumberTouched = true
                    documentNumberError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(documentNumberBringIntoViewRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                // Wait briefly for the IME to appear so we scroll to the right position.
                                delay(250)
                                documentNumberBringIntoViewRequester.bringIntoView()
                            }
                        } else if (documentNumberTouched) {
                            documentNumberError = if (documentNumberText.trim().isBlank()) {
                                context.getString(R.string.invoice_number_required)
                            } else null
                        }
                    },
                label = { Text("${stringResource(R.string.invoice_number)} *") },
                isError = documentNumberError != null,
                supportingText = documentNumberError?.let { err -> { Text(err) } }
            )

            Spacer(modifier = Modifier.padding(top = 8.dp))

            // Amount due *
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    amountTouched = true
                    amountError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(amountBringIntoViewRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                delay(250)
                                amountBringIntoViewRequester.bringIntoView()
                            }
                        } else if (amountTouched) {
                            val amt = amountText.toDoubleOrNull()
                            amountError = if (amt == null || amt <= 0.0) {
                                context.getString(R.string.enter_valid_amount)
                            } else null
                        }
                    },
                label = { Text("${stringResource(R.string.amount_due)} *") },
                isError = amountError != null,
                supportingText = { amountError?.let { Text(it) } },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .width(56.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outline)
                                .align(Alignment.CenterStart)
                        )
                        TextButton(
                            onClick = {
                                amountCurrencyCode =
                                    if (amountCurrencyCode == InvoiceCurrency.USD.name) {
                                        InvoiceCurrency.ILS.name
                                    } else {
                                        InvoiceCurrency.USD.name
                                    }
                            },
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (amountCurrencyCode == InvoiceCurrency.USD.name) {
                                        stringResource(R.string.currency_usd)
                                    } else {
                                        stringResource(R.string.currency_ils)
                                    }
                                )
                            }
                        }
                    }
                }
            )

            InvoiceFormSectionHeader(
                title = stringResource(R.string.service_period),
                isFirstSection = false
            )

            // Service period (optional)
            ServicePeriodModeSelector(
                selectedMode = servicePeriodMode,
                onModeSelected = { servicePeriodMode = it },
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(servicePeriodBringIntoViewRequester)
            ) {
                ServicePeriodInput(
                    mode = servicePeriodMode,
                    startDateText = servicePeriodStartText,
                    onStartDateTextChange = {
                        servicePeriodStartText = it
                        servicePeriodStartError = null
                    },
                    startDateError = servicePeriodStartError,
                    onStartDateTouched = {
                        servicePeriodStartTouched = true
                    },
                    endDateText = servicePeriodEndText,
                    onEndDateTextChange = {
                        servicePeriodEndText = it
                        servicePeriodEndError = null
                    },
                    endDateError = servicePeriodEndError,
                    onEndDateTouched = {
                        servicePeriodEndTouched = true
                    },
                    startYear = startYear,
                    startMonth = startMonth,
                    onStartMonthSelected = { y, m -> startYear = y; startMonth = m; startMonthError = null },
                    startMonthError = startMonthError,
                    showEndMonth = showEndMonth,
                    onToggleEndMonth = {
                        showEndMonth = !showEndMonth
                        if (!showEndMonth) {
                            endYear = startYear
                            endMonth = startMonth
                            endMonthError = null
                        }
                    },
                    endYear = endYear,
                    endMonth = endMonth,
                    onEndMonthSelected = { y, m -> endYear = y; endMonth = m; endMonthError = null },
                    endMonthError = endMonthError,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            InvoiceFormSectionHeader(
                title = stringResource(R.string.invoice_form_section_payment),
                isFirstSection = false
            )

            // Payment status *
            PaymentStatusSelector(
                selected = paymentStatus,
                onSelectedChange = { paymentStatus = it }
            )

            Spacer(modifier = Modifier.padding(top = 8.dp))

            // ── Conditional: Paid ──
            if (showPaidGroup) {
                PaymentMethodSelector(
                    selected = paymentMethod,
                    onSelected = { paymentMethod = it },
                    modifier = Modifier.fillMaxWidth()
                )
                if (paymentMethod == PaymentMethodOption.OTHER.value) {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = paymentMethodOtherText,
                        onValueChange = { paymentMethodOtherText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.payment_method_specify_other)) }
                    )
                }
                if (paymentMethod == PaymentMethodOption.CREDIT.value) {
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    val numberOfPaymentsBringIntoViewRequester = remember { BringIntoViewRequester() }
                    OutlinedTextField(
                        value = numberOfPayments,
                        onValueChange = { numberOfPayments = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(numberOfPaymentsBringIntoViewRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    coroutineScope.launch {
                                        delay(250)
                                        numberOfPaymentsBringIntoViewRequester.bringIntoView()
                                    }
                                }
                            },
                        placeholder = { Text(stringResource(R.string.number_of_payments_hint)) }
                    )
                }
                Spacer(modifier = Modifier.padding(top = 8.dp))

                val confirmationNumberBringIntoViewRequester = remember { BringIntoViewRequester() }
                OutlinedTextField(
                    value = confirmationNumber,
                    onValueChange = { confirmationNumber = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(confirmationNumberBringIntoViewRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                coroutineScope.launch {
                                    delay(250)
                                    confirmationNumberBringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                    label = { Text(stringResource(R.string.confirmation_number)) }
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))

                ExactDateField(
                    value = paymentDateText,
                    onValueChange = { paymentDateText = it },
                    label = stringResource(R.string.payment_date_label_hint),
                    error = null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
            }

            // ── Conditional: Not paid ──
            if (showDueDateGroup) {
                ExactDateField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = stringResource(R.string.due_date_label_hint),
                    error = null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
            }

            InvoiceFormSectionHeader(
                title = stringResource(R.string.invoice_form_section_additional_details),
                isFirstSection = false
            )

            val vendorBringIntoViewRequester = remember { BringIntoViewRequester() }
            OutlinedTextField(
                value = vendorName,
                onValueChange = { vendorName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(vendorBringIntoViewRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                delay(250)
                                vendorBringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                label = { Text(stringResource(R.string.vendor_name)) }
            )

            if (categoryCustomFieldTitles.isNotEmpty()) {
                categoryCustomFieldTitles.forEachIndexed { index, fieldTitle ->
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    val customFieldBringIntoViewRequester = remember { BringIntoViewRequester() }
                    OutlinedTextField(
                        value = customFieldValues.getOrNull(index) ?: "",
                        onValueChange = { newValue ->
                            val newList = customFieldValues.toMutableList()
                            while (newList.size <= index) newList.add("")
                            newList[index] = newValue
                            customFieldValues = newList
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(customFieldBringIntoViewRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    coroutineScope.launch {
                                        delay(250)
                                        customFieldBringIntoViewRequester.bringIntoView()
                                    }
                                }
                            },
                        label = { Text(fieldTitle) }
                    )
                }
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))

            val notesBringIntoViewRequester = remember { BringIntoViewRequester() }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(notesBringIntoViewRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                delay(250)
                                notesBringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                label = { Text(stringResource(R.string.notes_additional_info)) },
                minLines = 3
            )

            InvoiceFormSectionHeader(
                title = stringResource(R.string.attachment_section_title),
                isFirstSection = false
            )

            InvoiceAttachmentField(
                attachmentUri = attachmentUri,
                onAttach = { uri ->
                    val newUriString = uri.toString()
                    AttachmentUtil.takePersistableReadPermission(context, uri)
                    // Add screen never has a saved attachment yet, so any previous pick this
                    // session is abandoned in favor of the new one — unless another saved
                    // invoice already references that exact Uri, or the user re-picked the
                    // exact same file (nothing to abandon in that case).
                    val abandonedUri = attachmentUri
                    if (abandonedUri != newUriString) {
                        coroutineScope.launch {
                            AttachmentUtil.releaseIfUnreferenced(
                                context = context,
                                candidateUri = abandonedUri,
                                isReferencedElsewhere = isAttachmentUriInUse
                            )
                        }
                    }
                    attachmentUri = newUriString
                },
                onRemove = {
                    val abandonedUri = attachmentUri
                    coroutineScope.launch {
                        AttachmentUtil.releaseIfUnreferenced(
                            context = context,
                            candidateUri = abandonedUri,
                            isReferencedElsewhere = isAttachmentUriInUse
                        )
                    }
                    attachmentUri = null
                }
            )

            Spacer(modifier = Modifier.padding(top = 16.dp))

            Button(
                onClick = { handleSave() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text(stringResource(R.string.save_invoice))
            }

            // Small bottom buffer so user can scroll Save button above keyboard
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodSelector(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val anchorBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val displayText = when (selected) {
        PaymentMethodOption.NOT_SPECIFIED.value, "" -> stringResource(R.string.payment_method_not_specified)
        PaymentMethodOption.CREDIT.value -> stringResource(R.string.payment_method_credit)
        PaymentMethodOption.BANK_TRANSFER.value -> stringResource(R.string.payment_method_bank_transfer)
        PaymentMethodOption.CASH.value -> stringResource(R.string.payment_method_cash)
        PaymentMethodOption.CHECK.value -> stringResource(R.string.payment_method_check)
        PaymentMethodOption.DIGITAL_WALLET.value -> stringResource(R.string.payment_method_digital_wallet)
        PaymentMethodOption.OTHER.value -> stringResource(R.string.payment_method_other)
        else -> stringResource(R.string.payment_method_not_specified)
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { shouldExpand ->
            requestAnchoredDropdownExpansion(
                shouldExpand = shouldExpand,
                scope = coroutineScope,
                bringIntoViewRequester = anchorBringIntoViewRequester,
                onExpandedChange = { expanded = it }
            )
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .bringIntoViewRequester(anchorBringIntoViewRequester),
            label = { Text(stringResource(R.string.payment_method)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchTextFieldWidth = true
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.payment_method_not_specified)) },
                onClick = {
                    onSelected(PaymentMethodOption.NOT_SPECIFIED.value)
                    expanded = false
                }
            )
            PaymentMethodOption.entries
                .filter { it != PaymentMethodOption.NOT_SPECIFIED }
                .forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                PaymentMethodOption.CREDIT -> stringResource(R.string.payment_method_credit)
                                PaymentMethodOption.BANK_TRANSFER -> stringResource(R.string.payment_method_bank_transfer)
                                PaymentMethodOption.CASH -> stringResource(R.string.payment_method_cash)
                                PaymentMethodOption.CHECK -> stringResource(R.string.payment_method_check)
                                PaymentMethodOption.DIGITAL_WALLET -> stringResource(R.string.payment_method_digital_wallet)
                                PaymentMethodOption.OTHER -> stringResource(R.string.payment_method_other)
                                PaymentMethodOption.NOT_SPECIFIED -> stringResource(R.string.payment_method_not_specified)
                            }
                        )
                    },
                    onClick = {
                        onSelected(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun PaymentStatusSelector(
    selected: PaymentStatus,
    onSelectedChange: (PaymentStatus) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${stringResource(R.string.status)} *",
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row(modifier = Modifier.weight(1f)) {
            TextButton(
                onClick = { onSelectedChange(PaymentStatus.NOT_PAID) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text(
                    text = stringResource(R.string.not_paid),
                    fontWeight = if (selected == PaymentStatus.NOT_PAID) FontWeight.Bold else FontWeight.Normal
                )
            }
            TextButton(
                onClick = { onSelectedChange(PaymentStatus.PAID) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text(
                    text = stringResource(R.string.paid),
                    fontWeight = if (selected == PaymentStatus.PAID) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
