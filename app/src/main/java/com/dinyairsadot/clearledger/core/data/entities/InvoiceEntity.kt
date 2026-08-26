package com.dinyairsadot.clearledger.core.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.dinyairsadot.clearledger.core.data.converters.DocumentTypeConverter
import com.dinyairsadot.clearledger.core.data.converters.LocalDateConverter
import com.dinyairsadot.clearledger.core.data.converters.PaymentStatusConverter
import com.dinyairsadot.clearledger.core.data.converters.ServicePeriodModeConverter
import com.dinyairsadot.clearledger.core.data.converters.StringListConverter
import com.dinyairsadot.clearledger.core.data.converters.StringMapConverter
import com.dinyairsadot.clearledger.core.domain.Invoice
import com.dinyairsadot.clearledger.core.domain.InvoiceCurrency
import com.dinyairsadot.clearledger.core.domain.ServicePeriodMode

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"])]
)
@TypeConverters(
    LocalDateConverter::class,
    PaymentStatusConverter::class,
    ServicePeriodModeConverter::class,
    StringListConverter::class,
    DocumentTypeConverter::class
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    // Old fields (kept for backward compatibility)
    val invoiceNumber: String,
    val amount: Double,
    val paymentStatus: com.dinyairsadot.clearledger.core.domain.PaymentStatus,
    val vendorName: String? = null,
    val issueDateEpochDay: Long? = null,
    val dueDateEpochDay: Long? = null,
    val paymentDateEpochDay: Long? = null,
    val servicePeriodStartEpochDay: Long? = null,
    val servicePeriodEndEpochDay: Long? = null,
    val consumptionValue: Double? = null,
    val consumptionUnit: String? = null,
    val notes: String? = null,
    val customFieldValuesJson: String? = null,
    val documentTypeString: String? = null,
    // New minimal core fields
    val amountDue: Double? = null,
    val documentNumber: String? = null,
    val paymentMethodString: String? = null,
    val numberOfPayments: String? = null,
    val confirmationNumber: String? = null,
    // Pinned snapshot
    val pinnedSnapshotJson: String? = null,
    // Explicit service-period input mode. Non-null; migration sets DEFAULT 'MONTH'.
    val servicePeriodMode: ServicePeriodMode = ServicePeriodMode.MONTH,
    /** [InvoiceCurrency.name]; migration defaults existing rows to ILS. */
    val amountCurrencyCode: String = InvoiceCurrency.ILS.name,
    /** Legacy persisted `content://` Uri string of the single local attachment; null once migrated. */
    val attachmentUri: String? = null,
    /** Filename of the managed, app-private attachment copy under `invoice_attachments/`; null if none. */
    val attachmentFileName: String? = null,
    /** Original display name of the attachment, for UI. */
    val attachmentDisplayName: String? = null,
    /** MIME type of the attachment, for opening it. */
    val attachmentMimeType: String? = null
) {
    fun toDomain(): Invoice {
        val issueDate = issueDateEpochDay?.let { 
            LocalDateConverter().toLocalDate(it) 
        }
        val dueDate = dueDateEpochDay?.let { 
            LocalDateConverter().toLocalDate(it) 
        }
        val paymentDate = paymentDateEpochDay?.let { 
            LocalDateConverter().toLocalDate(it) 
        }
        val servicePeriodStart = servicePeriodStartEpochDay?.let {
            LocalDateConverter().toLocalDate(it)
        }
        val servicePeriodEnd = servicePeriodEndEpochDay?.let {
            LocalDateConverter().toLocalDate(it)
        }
        val customFieldValues = if (customFieldValuesJson.isNullOrBlank()) {
            emptyList()
        } else {
            StringListConverter().toStringList(customFieldValuesJson)
        }
        val documentType = documentTypeString?.let {
            DocumentTypeConverter().toDocumentType(it)
        }
        
        val pinnedSnapshot = if (pinnedSnapshotJson.isNullOrBlank()) {
            emptyMap()
        } else {
            StringMapConverter().toStringMap(pinnedSnapshotJson)
        }

        val amountCurrency = runCatching { InvoiceCurrency.valueOf(amountCurrencyCode) }
            .getOrDefault(InvoiceCurrency.ILS)

        return Invoice(
            id = id,
            categoryId = categoryId,
            invoiceNumber = invoiceNumber,
            amount = amount,
            paymentStatus = paymentStatus,
            vendorName = vendorName,
            issueDate = issueDate,
            dueDate = dueDate,
            paymentDate = paymentDate,
            servicePeriodStart = servicePeriodStart,
            servicePeriodEnd = servicePeriodEnd,
            consumptionValue = consumptionValue,
            consumptionUnit = consumptionUnit,
            notes = notes,
            customFieldValues = customFieldValues,
            documentType = documentType,
            // New fields: fallback to old values if null
            amountDue = amountDue ?: amount,
            documentNumber = documentNumber ?: invoiceNumber,
            paymentMethod = paymentMethodString,
            numberOfPayments = numberOfPayments,
            confirmationNumber = confirmationNumber,
            pinnedSnapshot = pinnedSnapshot,
            servicePeriodMode = servicePeriodMode,
            amountCurrency = amountCurrency,
            attachmentUri = attachmentUri,
            attachmentFileName = attachmentFileName,
            attachmentDisplayName = attachmentDisplayName,
            attachmentMimeType = attachmentMimeType
        )
    }
    
    companion object {
        fun fromDomain(invoice: Invoice): InvoiceEntity {
            val issueDateEpochDay = invoice.issueDate?.let { 
                LocalDateConverter().fromLocalDate(it) 
            }
            val dueDateEpochDay = invoice.dueDate?.let { 
                LocalDateConverter().fromLocalDate(it) 
            }
            val paymentDateEpochDay = invoice.paymentDate?.let { 
                LocalDateConverter().fromLocalDate(it) 
            }
            val servicePeriodStartEpochDay = invoice.servicePeriodStart?.let {
                LocalDateConverter().fromLocalDate(it)
            }
            val servicePeriodEndEpochDay = invoice.servicePeriodEnd?.let {
                LocalDateConverter().fromLocalDate(it)
            }
            val customFieldValuesJson = if (invoice.customFieldValues.isEmpty()) {
                null
            } else {
                StringListConverter().fromStringList(invoice.customFieldValues)
            }
            val documentTypeString = invoice.documentType?.let {
                DocumentTypeConverter().fromDocumentType(it)
            }
            
            val pinnedSnapshotJson = if (invoice.pinnedSnapshot.isEmpty()) {
                null
            } else {
                StringMapConverter().fromStringMap(invoice.pinnedSnapshot)
            }
            
            return InvoiceEntity(
                id = invoice.id,
                categoryId = invoice.categoryId,
                invoiceNumber = invoice.invoiceNumber,
                amount = invoice.amount,
                paymentStatus = invoice.paymentStatus,
                vendorName = invoice.vendorName,
                issueDateEpochDay = issueDateEpochDay,
                dueDateEpochDay = dueDateEpochDay,
                paymentDateEpochDay = paymentDateEpochDay,
                servicePeriodStartEpochDay = servicePeriodStartEpochDay,
                servicePeriodEndEpochDay = servicePeriodEndEpochDay,
                consumptionValue = invoice.consumptionValue,
                consumptionUnit = invoice.consumptionUnit,
                notes = invoice.notes,
                customFieldValuesJson = customFieldValuesJson,
                documentTypeString = documentTypeString,
                // New fields: always write from domain
                amountDue = invoice.amountDue,
                documentNumber = invoice.documentNumber,
                paymentMethodString = invoice.paymentMethod,
                numberOfPayments = invoice.numberOfPayments,
                confirmationNumber = invoice.confirmationNumber,
                pinnedSnapshotJson = pinnedSnapshotJson,
                servicePeriodMode = invoice.servicePeriodMode,
                amountCurrencyCode = invoice.amountCurrency.name,
                attachmentUri = invoice.attachmentUri,
                attachmentFileName = invoice.attachmentFileName,
                attachmentDisplayName = invoice.attachmentDisplayName,
                attachmentMimeType = invoice.attachmentMimeType
            )
        }
    }
}
