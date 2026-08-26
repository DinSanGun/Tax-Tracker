package com.dinyairsadot.clearledger.core.domain

import java.time.Instant
import java.time.LocalDate

enum class PaymentStatus {
    PAID,
    NOT_PAID
}

enum class PaymentMethodOption(val value: String) {
    NOT_SPECIFIED(""),
    CREDIT("credit"),
    BANK_TRANSFER("bank_transfer"),
    CASH("cash"),
    CHECK("check"),
    DIGITAL_WALLET("digital_wallet"),
    OTHER("other")
}

/**
 * Controls how the service period is entered and stored for an invoice.
 *
 * MONTH – user picks a start month (+ optional end month). Dates are snapped:
 *         startDate = first day of start month, endDate = last day of end month.
 * DATE  – user picks exact calendar dates; stored as-is.
 *
 * This enum is the single source of truth. Mode must NEVER be inferred from dates.
 */
enum class ServicePeriodMode {
    MONTH,
    DATE
}

enum class CustomFieldType {
    TEXT,
    NUMBER,
    DATE,
    BOOLEAN
}

enum class DocumentType {
    BILL_DEMAND,
    TAX_INVOICE,
    INVOICE_RECEIPT
}

/** Display / metadata only; amounts are not converted between currencies. */
enum class InvoiceCurrency {
    ILS,
    USD
}

data class Category(
    val id: Long,
    val name: String,
    val colorHex: String,
    val description: String? = null,
    val customFieldTitles: List<String> = emptyList(), // Up to 10 custom field titles
    val supplierName: String? = null,  // Kept for backward compatibility
    val pinnedDefaults: Map<String, String> = emptyMap(),  // Pinned default values (e.g., "supplierName" -> "Israel Electric Company")
    val seedKey: String? = null,       // Stable key for seeded categories (e.g. "arnona"); null for user-created
    val userEdited: Boolean = false,   // True after user saves edits; prevents locale overwrite
    val orderIndex: Int = 0            // Stable manual order in category list
) {
    // Backward compatibility: getters for old field names
    val customFieldTitle1: String? get() = customFieldTitles.getOrNull(0)
    val customFieldTitle2: String? get() = customFieldTitles.getOrNull(1)
    val customFieldTitle3: String? get() = customFieldTitles.getOrNull(2)
    
    companion object {
        const val MAX_CUSTOM_FIELDS = 10
        const val PINNED_KEY_SUPPLIER_NAME = "supplierName"
    }
}

/**
 * Defines a custom field in a given category's schema.
 * Example: "Meter ID" (TEXT), "Billing period start" (DATE)
 */
data class CustomFieldDefinition(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val type: CustomFieldType,
    val isRequired: Boolean,
    val isArchived: Boolean = false, // future soft-delete support
    val order: Int = 0
)

/**
 * Represents a single invoice / bill entry.
 *
 * Date semantics:
 * - [paymentDate]: actual date the invoice was paid (optional)
 * - [dueDate]: deadline to pay (optional). Never inferred from service period.
 *
 * New minimal core fields: amountDue, documentNumber, servicePeriodStart, servicePeriodEnd,
 * paymentMethod (optional). Old fields kept for backward compatibility.
 */
data class Invoice(
    val id: Long,
    val categoryId: Long,
    // Old fields (kept for compatibility)
    val invoiceNumber: String,
    val amount: Double,
    val paymentStatus: PaymentStatus,
    val vendorName: String? = null,
    val issueDate: LocalDate? = null,
    /** Deadline to pay. Optional; never inferred from service period. */
    val dueDate: LocalDate? = null,
    /** Actual date the invoice was paid. Optional. */
    val paymentDate: LocalDate? = null,
    val servicePeriodStart: LocalDate? = null,
    val servicePeriodEnd: LocalDate? = null,
    val consumptionValue: Double? = null,
    val consumptionUnit: String? = null,
    val notes: String? = null,
    val customFieldValues: List<String> = emptyList(),
    val documentType: DocumentType? = null,
    // New minimal core fields (added for future migration)
    val amountDue: Double = amount,  // defaults to old amount
    val documentNumber: String = invoiceNumber,  // defaults to old invoiceNumber
    val paymentMethod: String? = null,
    val numberOfPayments: String? = null,
    val confirmationNumber: String? = null,
    // Pinned snapshot: captures category.pinnedDefaults at invoice creation time
    val pinnedSnapshot: Map<String, String> = emptyMap(),
    // Explicit mode – source of truth; chosen per-invoice in the form.
    val servicePeriodMode: ServicePeriodMode = ServicePeriodMode.MONTH,
    /** Stored display currency; existing DB rows default to ILS via migration. */
    val amountCurrency: InvoiceCurrency = InvoiceCurrency.ILS,
    /**
     * Legacy persisted `content://` Uri string of the single local image/PDF attachment for
     * this invoice, or null. Only ever set for invoices created before the app-private
     * attachment storage refactor; new attachments never populate this field. Lazily migrated
     * to [attachmentFileName] the next time this invoice's list is loaded, if the source
     * document is still accessible (see `InvoiceListViewModel.migrateLegacyAttachmentIfNeeded`).
     * If migration isn't possible (source deleted/moved), this is left as-is so the invoice
     * itself is never lost.
     */
    val attachmentUri: String? = null,
    /**
     * Filename of the managed, app-private copy of this invoice's attachment under
     * `filesDir/invoice_attachments/` (see `AttachmentStorage`), or null if none. Once set,
     * the invoice no longer depends on the original external document: it can be deleted or
     * moved by the user without breaking the attachment.
     *
     * Local-only for now: managed attachment files are not yet included in backup/restore
     * (see `core/util/backup/BackupMapper.kt`); only these reference fields are persisted.
     */
    val attachmentFileName: String? = null,
    /** Original display name of the attachment, kept for UI display since [attachmentFileName] is an internal identifier. */
    val attachmentDisplayName: String? = null,
    /** MIME type of the attachment, kept for opening it with the correct viewer. */
    val attachmentMimeType: String? = null
) {
    /** True if this invoice currently has an attachment, managed or not-yet-migrated legacy. */
    val hasAttachment: Boolean
        get() = !attachmentFileName.isNullOrBlank() || !attachmentUri.isNullOrBlank()

    // Backward compatibility: getters for old field names
    val customFieldValue1: String? get() = customFieldValues.getOrNull(0)
    val customFieldValue2: String? get() = customFieldValues.getOrNull(1)
    val customFieldValue3: String? get() = customFieldValues.getOrNull(2)
}

/**
 * Stores the value of a custom field for a specific invoice.
 * The value is stored as String and interpreted according to CustomFieldType.
 */
data class InvoiceCustomFieldValue(
    val id: Long,
    val invoiceId: Long,
    val fieldDefinitionId: Long,
    val value: String
)

/**
 * Attachment (photo / image) of an invoice.
 */
data class InvoiceImage(
    val id: Long,
    val invoiceId: Long,
    val uri: String,             // URI/path to the image in app storage
    val createdAt: Instant = Instant.now()
)
