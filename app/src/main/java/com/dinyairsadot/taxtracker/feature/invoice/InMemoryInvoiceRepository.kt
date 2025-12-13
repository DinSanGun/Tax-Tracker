package com.dinyairsadot.taxtracker.feature.invoice

import com.dinyairsadot.taxtracker.core.domain.Invoice
import com.dinyairsadot.taxtracker.core.domain.InvoiceRepository
import com.dinyairsadot.taxtracker.core.domain.PaymentStatus
import java.time.LocalDate

object InMemoryInvoiceRepository : InvoiceRepository {

    // Mutable list so we can add/update/delete invoices at runtime.
    // For now we seed a couple of example invoices under categoryId = 1.
    private val invoices = mutableListOf(
        Invoice(
            id = 1L,
            categoryId = 1L,
            invoiceNumber = "EL-2025-001",
            amount = 250.0,
            paymentStatus = PaymentStatus.PAID_FULL,
            dueDate = LocalDate.now().minusDays(10),
            paymentDate = LocalDate.now().minusDays(5),
            consumptionValue = 120.0,
            consumptionUnit = "kWh",
            notes = "Sample paid electricity invoice"
        ),
        Invoice(
            id = 2L,
            categoryId = 1L,
            invoiceNumber = "EL-2025-002",
            amount = 300.0,
            paymentStatus = PaymentStatus.NOT_PAID,
            dueDate = LocalDate.now().plusDays(10),
            paymentDate = null,
            consumptionValue = 140.0,
            consumptionUnit = "kWh",
            notes = "Upcoming invoice example"
        )
    )

    override suspend fun getInvoicesForCategory(categoryId: Long): List<Invoice> {
        return invoices.filter { it.categoryId == categoryId }
    }

    override suspend fun getInvoiceById(id: Long): Invoice? {
        return invoices.firstOrNull { it.id == id }
    }

    override suspend fun addInvoice(invoice: Invoice) {
        invoices.add(invoice)
    }

    override suspend fun updateInvoice(invoice: Invoice) {
        val index = invoices.indexOfFirst { it.id == invoice.id }
        if (index != -1) {
            invoices[index] = invoice
        }
    }

    override suspend fun deleteInvoice(id: Long) {
        invoices.removeAll { it.id == id }
    }
}
