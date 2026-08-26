package com.dinyairsadot.clearledger.feature.invoice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.dinyairsadot.clearledger.core.domain.DocumentType
import com.dinyairsadot.clearledger.feature.invoice.formatServicePeriodForDisplay
import com.dinyairsadot.clearledger.core.domain.PaymentStatus
import com.dinyairsadot.clearledger.core.domain.PaymentMethodOption
import com.dinyairsadot.clearledger.core.ui.SwipeDismissSnackbarHost
import com.dinyairsadot.clearledger.core.ui.categoryTopAppBarColors
import com.dinyairsadot.clearledger.core.util.AttachmentUtil
import androidx.compose.material3.LocalContentColor
import com.dinyairsadot.clearledger.R
import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailsScreen(
    invoice: InvoiceUi,
    categoryCustomFieldTitles: List<String>,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    categoryColorHex: String?
) {
    val currentLocale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val attachmentUnavailableMessage = stringResource(R.string.attachment_unavailable_message)
    val attachmentNoViewerMessage = stringResource(R.string.attachment_no_viewer_app_message)

    Scaffold(
        snackbarHost = { SwipeDismissSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.invoice_details),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    // IconButton/Icon inherit the top app bar's contrast-aware content color.
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_invoice)
                        )
                    }
                },
                colors = categoryTopAppBarColors(categoryColorHex)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        val invoiceNumberText = invoice.invoiceNumber.ifBlank {
                            stringResource(R.string.invoice_number_fallback, invoice.id)
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.invoice_number_label, "").trimEnd(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = keepLastTwoCharactersTogetherForDisplay(invoiceNumberText),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    // Expose the original, unmodified invoice number to
                                    // accessibility services (no invisible joiner character).
                                    .semantics { contentDescription = invoiceNumberText }
                            )
                        }

                        invoice.documentType?.let { docType ->
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            val docTypeText = when (docType) {
                                DocumentType.BILL_DEMAND -> stringResource(R.string.document_type_bill_demand)
                                DocumentType.TAX_INVOICE -> stringResource(R.string.document_type_tax_invoice)
                                DocumentType.INVOICE_RECEIPT -> stringResource(R.string.document_type_invoice_receipt)
                            }
                            DetailFieldRow(
                                label = stringResource(R.string.document_type_label, "").trimEnd(),
                                value = docTypeText
                            )
                        }

                        invoice.vendorName?.takeIf { it.isNotBlank() }?.let { vendor ->
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            DetailFieldRow(
                                label = stringResource(R.string.vendor_name_label, "").trimEnd(),
                                value = vendor
                            )
                        }

                        Spacer(
                            modifier = Modifier.padding(
                                top = if (
                                    invoice.documentType != null ||
                                    !invoice.vendorName.isNullOrBlank()
                                ) {
                                    4.dp
                                } else {
                                    8.dp
                                }
                            )
                        )

                        DetailFieldRow(
                            label = stringResource(R.string.amount_label, "").trimEnd(),
                            value = formatAmountWithCurrency(
                                LocalContext.current,
                                invoice.amount,
                                invoice.amountCurrency
                            )
                        )

                        Spacer(modifier = Modifier.padding(top = 4.dp))

                        val statusText = when (invoice.paymentStatus) {
                            PaymentStatus.PAID -> stringResource(R.string.paid)
                            PaymentStatus.NOT_PAID -> stringResource(R.string.not_paid)
                        }
                        DetailFieldRow(
                            label = stringResource(R.string.status),
                            value = statusText,
                            valueColor = invoice.paymentStatus.toDisplayColor()
                        )

                        // Payment details (shown only when present)
                        invoice.paymentMethod?.takeIf { it.isNotBlank() }?.let { methodValue ->
                            val methodLabel = when (methodValue) {
                                PaymentMethodOption.CREDIT.value -> stringResource(R.string.payment_method_credit)
                                PaymentMethodOption.BANK_TRANSFER.value -> stringResource(R.string.payment_method_bank_transfer)
                                PaymentMethodOption.CASH.value -> stringResource(R.string.payment_method_cash)
                                PaymentMethodOption.CHECK.value -> stringResource(R.string.payment_method_check)
                                PaymentMethodOption.DIGITAL_WALLET.value -> stringResource(R.string.payment_method_digital_wallet)
                                PaymentMethodOption.OTHER.value -> stringResource(R.string.payment_method_other)
                                else -> methodValue
                            }
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            DetailFieldRow(
                                label = stringResource(R.string.payment_method_label, "").trimEnd(),
                                value = methodLabel
                            )
                        }

                        invoice.numberOfPayments?.takeIf { it.isNotBlank() }?.let { count ->
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            DetailFieldRow(
                                label = stringResource(R.string.number_of_payments_label, "").trimEnd(),
                                value = count
                            )
                        }

                        invoice.confirmationNumber?.takeIf { it.isNotBlank() }?.let { confirmation ->
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            DetailFieldRow(
                                label = stringResource(R.string.confirmation_number_label, "").trimEnd(),
                                value = confirmation
                            )
                        }

                        invoice.issueDateText?.let { issue ->
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            DetailFieldRow(
                                label = stringResource(R.string.issue_date_label, "").trimEnd(),
                                value = issue
                            )
                        }

                        invoice.dueDateText?.let { due ->
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            DetailFieldRow(
                                label = stringResource(R.string.due_date_label, "").trimEnd(),
                                value = due
                            )
                        }

                        invoice.paymentDateText?.let { paid ->
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            DetailFieldRow(
                                label = stringResource(R.string.paid_date_label, "").trimEnd(),
                                value = paid
                            )
                        }

                        formatServicePeriodForDisplay(
                            invoice.servicePeriodStartText,
                            invoice.servicePeriodEndText,
                            invoice.servicePeriodMode,
                            currentLocale
                        )?.let { formattedPeriod ->
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            DetailFieldRow(
                                label = stringResource(R.string.service_period_label, "").trimEnd(),
                                value = formattedPeriod
                            )
                        }

                        // Custom fields
                        if (categoryCustomFieldTitles.isNotEmpty() && invoice.customFieldValues.isNotEmpty()) {
                            categoryCustomFieldTitles.forEachIndexed { index, fieldTitle ->
                                invoice.customFieldValues.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { value ->
                                    Spacer(modifier = Modifier.padding(top = 4.dp))
                                    DetailFieldRow(
                                        label = "$fieldTitle:",
                                        value = value
                                    )
                                }
                            }
                        }

                        invoice.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                            Spacer(modifier = Modifier.padding(top = 8.dp))
                            Text(
                                text = stringResource(R.string.notes_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.padding(top = 2.dp))
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        invoice.attachmentUri?.takeIf { it.isNotBlank() }?.let { attachmentUri ->
                            val attachmentDisplayName = rememberAttachmentDisplayName(attachmentUri)
                            Spacer(modifier = Modifier.padding(top = 8.dp))
                            Text(
                                text = stringResource(R.string.attachment_section_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.padding(top = 2.dp))
                            Text(
                                text = attachmentDisplayName
                                    ?: stringResource(R.string.attachment_unknown_filename),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.padding(top = 2.dp))
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        when (AttachmentUtil.openAttachment(context, attachmentUri)) {
                                            AttachmentUtil.OpenResult.Opened -> Unit
                                            AttachmentUtil.OpenResult.NotAccessible ->
                                                snackbarHostState.showSnackbar(attachmentUnavailableMessage)
                                            AttachmentUtil.OpenResult.NoViewerApp ->
                                                snackbarHostState.showSnackbar(attachmentNoViewerMessage)
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(stringResource(R.string.attachment_open_action))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailFieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    valueFontWeight: FontWeight = FontWeight.Normal
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = valueFontWeight,
            color = if (valueColor != Color.Unspecified) valueColor else LocalContentColor.current,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 4.dp)
        )
    }
}

/**
 * Inserts an invisible Unicode WORD JOINER (`\u2060`) between the final two
 * characters of [value] so that, if the displayed invoice number needs to
 * wrap onto a second line, the break never leaves just one lone character
 * behind. This is a display-only concern: it never touches the stored
 * invoice number, and callers must pass the original string as the
 * accessibility content description.
 */
private fun keepLastTwoCharactersTogetherForDisplay(value: String): String {
    if (value.length < 2) return value

    val splitIndex = value.length - 1
    return value.substring(0, splitIndex) +
        "\u2060" +
        value.substring(splitIndex)
}

@Composable
private fun PaymentStatus.toDisplayColor(): Color {
    return when (this) {
        PaymentStatus.PAID -> Color(0xFF4CAF50)
        PaymentStatus.NOT_PAID -> MaterialTheme.colorScheme.error
    }
}
