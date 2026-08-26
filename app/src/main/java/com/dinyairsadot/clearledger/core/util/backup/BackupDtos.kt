package com.dinyairsadot.clearledger.core.util.backup

import com.dinyairsadot.clearledger.core.domain.Category
import com.dinyairsadot.clearledger.core.domain.Invoice
import java.time.Instant

/** Input container for backup export (domain models before mapping). */
data class BackupData(
    val categories: List<Category>,
    val invoices: List<Invoice>
)

data class BackupMetadata(
    val exportedAt: String,
    val dbSchemaVersion: Int,
    val producer: String
) {
    companion object {
        fun create(): BackupMetadata = BackupMetadata(
            exportedAt = Instant.now().toString(),
            dbSchemaVersion = BackupFormat.DB_SCHEMA_VERSION,
            producer = BackupFormat.PRODUCER
        )
    }
}

data class BackupCategoryDto(
    val id: Long,
    val name: String,
    val colorHex: String,
    val description: String?,
    val customFieldTitles: List<String>?,
    val supplierName: String?,
    val pinnedDefaults: Map<String, String>?,
    val seedKey: String?,
    val userEdited: Boolean,
    val orderIndex: Int
)

/**
 * Intentionally does **not** include any of [com.dinyairsadot.clearledger.core.domain.Invoice]'s
 * attachment fields (`attachmentUri`, `attachmentFileName`, `attachmentDisplayName`,
 * `attachmentMimeType`). Invoice attachments — including the managed app-private copies
 * introduced alongside these fields — are local to the device; attachment-aware backup/restore
 * (including packaging the managed files themselves into the backup ZIP) remains the next
 * stage. Existing backups created before the attachment feature remain restorable unchanged;
 * new backups simply omit attachment info for now.
 */
data class BackupInvoiceDto(
    val id: Long,
    val categoryId: Long,
    val invoiceNumber: String,
    val amount: Double,
    val amountDue: Double,
    val documentNumber: String,
    val paymentStatus: String,
    val amountCurrency: String,
    val vendorName: String?,
    val issueDate: String?,
    val dueDate: String?,
    val paymentDate: String?,
    val servicePeriodStart: String?,
    val servicePeriodEnd: String?,
    val servicePeriodMode: String,
    val documentType: String?,
    val paymentMethod: String?,
    val numberOfPayments: String?,
    val confirmationNumber: String?,
    val consumptionValue: Double?,
    val consumptionUnit: String?,
    val notes: String?,
    val customFieldValues: List<String>?,
    val pinnedSnapshot: Map<String, String>?
)

data class BackupPayload(
    val formatVersion: Int,
    val metadata: BackupMetadata,
    val categories: List<BackupCategoryDto>,
    val invoices: List<BackupInvoiceDto>
)
