package com.dinyairsadot.clearledger.core.util.backup

import com.dinyairsadot.clearledger.core.domain.Category
import com.dinyairsadot.clearledger.core.domain.DocumentType
import com.dinyairsadot.clearledger.core.domain.Invoice
import com.dinyairsadot.clearledger.core.domain.InvoiceCurrency
import com.dinyairsadot.clearledger.core.domain.PaymentStatus
import com.dinyairsadot.clearledger.core.domain.ServicePeriodMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Maps domain models to backup DTOs.
 *
 * Backup stores the app's effective domain state (post-[toDomain]), not a byte-for-byte
 * raw database dump. Restore will later go through [fromDomain], so the round-trip is
 * complete. Known implication: fields filled by domain fallbacks (e.g. [Invoice.amountDue]
 * defaults to legacy [Invoice.amount] when the DB column is null) are exported as their
 * effective values.
 *
 * [Invoice.attachmentUri] is deliberately **not** mapped to/from [BackupInvoiceDto]. Invoice
 * attachment references are local to the device and attachment file backup/restore will be
 * implemented in the next stage; restoring an old or new backup simply leaves attachmentUri
 * null.
 */
object BackupMapper {

    private val isoDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun toPayload(data: BackupData, metadata: BackupMetadata): BackupPayload {
        return BackupPayload(
            formatVersion = BackupFormat.FORMAT_VERSION,
            metadata = metadata,
            categories = data.categories.map(::toCategoryDto),
            invoices = data.invoices.map(::toInvoiceDto)
        )
    }

    fun toCategoryDto(category: Category): BackupCategoryDto {
        return BackupCategoryDto(
            id = category.id,
            name = category.name,
            colorHex = category.colorHex,
            description = category.description,
            customFieldTitles = category.customFieldTitles,
            supplierName = category.supplierName,
            pinnedDefaults = category.pinnedDefaults,
            seedKey = category.seedKey,
            userEdited = category.userEdited,
            orderIndex = category.orderIndex
        )
    }

    fun toInvoiceDto(invoice: Invoice): BackupInvoiceDto {
        return BackupInvoiceDto(
            id = invoice.id,
            categoryId = invoice.categoryId,
            invoiceNumber = invoice.invoiceNumber,
            amount = invoice.amount,
            amountDue = invoice.amountDue,
            documentNumber = invoice.documentNumber,
            paymentStatus = invoice.paymentStatus.name,
            amountCurrency = invoice.amountCurrency.name,
            vendorName = invoice.vendorName,
            issueDate = formatDate(invoice.issueDate),
            dueDate = formatDate(invoice.dueDate),
            paymentDate = formatDate(invoice.paymentDate),
            servicePeriodStart = formatDate(invoice.servicePeriodStart),
            servicePeriodEnd = formatDate(invoice.servicePeriodEnd),
            servicePeriodMode = invoice.servicePeriodMode.name,
            documentType = invoice.documentType?.name,
            paymentMethod = invoice.paymentMethod,
            numberOfPayments = invoice.numberOfPayments,
            confirmationNumber = invoice.confirmationNumber,
            consumptionValue = invoice.consumptionValue,
            consumptionUnit = invoice.consumptionUnit,
            notes = invoice.notes,
            customFieldValues = invoice.customFieldValues,
            pinnedSnapshot = invoice.pinnedSnapshot
        )
    }

    fun fromCategoryDto(dto: BackupCategoryDto): Category {
        return Category(
            id = dto.id,
            name = dto.name,
            colorHex = dto.colorHex,
            description = dto.description,
            customFieldTitles = dto.customFieldTitles.orEmpty(),
            supplierName = dto.supplierName,
            pinnedDefaults = dto.pinnedDefaults.orEmpty(),
            seedKey = dto.seedKey,
            userEdited = dto.userEdited,
            orderIndex = dto.orderIndex
        )
    }

    fun fromInvoiceDto(dto: BackupInvoiceDto): Invoice {
        return Invoice(
            id = dto.id,
            categoryId = dto.categoryId,
            invoiceNumber = dto.invoiceNumber,
            amount = dto.amount,
            amountDue = dto.amountDue,
            documentNumber = dto.documentNumber,
            paymentStatus = PaymentStatus.valueOf(dto.paymentStatus),
            amountCurrency = InvoiceCurrency.valueOf(dto.amountCurrency),
            vendorName = dto.vendorName,
            issueDate = parseDate(dto.issueDate),
            dueDate = parseDate(dto.dueDate),
            paymentDate = parseDate(dto.paymentDate),
            servicePeriodStart = parseDate(dto.servicePeriodStart),
            servicePeriodEnd = parseDate(dto.servicePeriodEnd),
            servicePeriodMode = ServicePeriodMode.valueOf(dto.servicePeriodMode),
            documentType = dto.documentType?.let { DocumentType.valueOf(it) },
            paymentMethod = dto.paymentMethod,
            numberOfPayments = dto.numberOfPayments,
            confirmationNumber = dto.confirmationNumber,
            consumptionValue = dto.consumptionValue,
            consumptionUnit = dto.consumptionUnit,
            notes = dto.notes,
            customFieldValues = dto.customFieldValues.orEmpty(),
            pinnedSnapshot = dto.pinnedSnapshot.orEmpty()
        )
    }

    private fun formatDate(date: LocalDate?): String? {
        return date?.format(isoDateFormatter)
    }

    private fun parseDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it, isoDateFormatter) }
    }
}
