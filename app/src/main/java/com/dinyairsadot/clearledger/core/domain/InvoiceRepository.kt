package com.dinyairsadot.clearledger.core.domain

interface InvoiceRepository {

    /**
     * Returns all invoices that belong to the given category.
     */
    suspend fun getInvoicesForCategory(categoryId: Long): List<Invoice>

    /**
     * Returns all invoices across all categories.
     */
    suspend fun getAllInvoices(): List<Invoice>

    /**
     * Returns a single invoice by its id, or null if it does not exist.
     */
    suspend fun getInvoiceById(id: Long): Invoice?

    /**
     * Adds a new invoice.
     *
     * The caller (ViewModel) is responsible for providing a unique id
     * in the in-memory implementation. Later, Room will generate ids.
     */
    suspend fun addInvoice(invoice: Invoice)

    /**
     * Updates an existing invoice. If the invoice does not exist,
     * implementations may choose to ignore or throw.
     */
    suspend fun updateInvoice(invoice: Invoice)

    /**
     * Deletes an invoice by id.
     */
    suspend fun deleteInvoice(id: Long)

    /**
     * Returns how many persisted invoices currently reference [attachmentUri]. Used to decide
     * whether a persisted SAF read permission for that legacy Uri is safe to release, since
     * multiple invoices may point at the same attachment.
     */
    suspend fun countInvoicesWithAttachmentUri(attachmentUri: String): Int

    /**
     * Returns how many persisted invoices currently reference the managed attachment file
     * [attachmentFileName]. Used to decide whether that file is safe to delete.
     */
    suspend fun countInvoicesWithAttachmentFileName(attachmentFileName: String): Int
}
