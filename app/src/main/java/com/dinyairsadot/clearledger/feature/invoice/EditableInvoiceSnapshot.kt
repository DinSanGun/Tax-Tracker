package com.dinyairsadot.clearledger.feature.invoice

import com.dinyairsadot.clearledger.core.domain.PaymentStatus
import com.dinyairsadot.clearledger.core.domain.ServicePeriodMode

/**
 * Normalized snapshot of meaningful invoice form fields for unsaved-change detection.
 * Excludes UI-only state such as validation errors, touched flags, and dialog visibility.
 */
internal data class EditableInvoiceSnapshot(
    val documentNumber: String,
    val amount: String,
    val currencyCode: String,
    val paymentStatus: PaymentStatus,
    val servicePeriodMode: ServicePeriodMode,
    val servicePeriodStartText: String,
    val servicePeriodEndText: String,
    val startYear: Int,
    val startMonth: Int,
    val showEndMonth: Boolean,
    val endYear: Int,
    val endMonth: Int,
    val paymentDateText: String,
    val dueDateText: String,
    val paymentMethod: String,
    val paymentMethodOtherText: String,
    val numberOfPayments: String,
    val confirmationNumber: String,
    val vendorName: String,
    val notes: String,
    val customFieldValues: List<String>,
    val attachmentFileName: String,
    val attachmentUri: String
)

internal fun editableInvoiceSnapshot(
    documentNumber: String,
    amount: String,
    currencyCode: String,
    paymentStatus: PaymentStatus,
    servicePeriodMode: ServicePeriodMode,
    servicePeriodStartText: String,
    servicePeriodEndText: String,
    startYear: Int,
    startMonth: Int,
    showEndMonth: Boolean,
    endYear: Int,
    endMonth: Int,
    paymentDateText: String,
    dueDateText: String,
    paymentMethod: String,
    paymentMethodOtherText: String,
    numberOfPayments: String,
    confirmationNumber: String,
    vendorName: String,
    notes: String,
    customFieldValues: List<String>,
    attachmentFileName: String = "",
    attachmentUri: String = ""
): EditableInvoiceSnapshot {
    return EditableInvoiceSnapshot(
        documentNumber = documentNumber.trim(),
        amount = amount.trim(),
        currencyCode = currencyCode.trim(),
        paymentStatus = paymentStatus,
        servicePeriodMode = servicePeriodMode,
        servicePeriodStartText = servicePeriodStartText.trim(),
        servicePeriodEndText = servicePeriodEndText.trim(),
        startYear = startYear,
        startMonth = startMonth,
        showEndMonth = showEndMonth,
        endYear = endYear,
        endMonth = endMonth,
        paymentDateText = paymentDateText.trim(),
        dueDateText = dueDateText.trim(),
        paymentMethod = paymentMethod.trim(),
        paymentMethodOtherText = paymentMethodOtherText.trim(),
        numberOfPayments = numberOfPayments.trim(),
        confirmationNumber = confirmationNumber.trim(),
        vendorName = vendorName.trim(),
        notes = notes.trim(),
        customFieldValues = customFieldValues.map { it.trim() },
        attachmentFileName = attachmentFileName.trim(),
        attachmentUri = attachmentUri.trim()
    )
}
