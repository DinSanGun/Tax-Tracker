package com.dinyairsadot.clearledger.feature.invoice

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dinyairsadot.clearledger.R
import com.dinyairsadot.clearledger.core.domain.CategoryRepository
import com.dinyairsadot.clearledger.core.domain.DocumentType
import com.dinyairsadot.clearledger.core.domain.Invoice
import com.dinyairsadot.clearledger.core.domain.InvoiceCurrency
import com.dinyairsadot.clearledger.core.domain.InvoiceRepository
import com.dinyairsadot.clearledger.core.domain.PaymentStatus
import com.dinyairsadot.clearledger.core.domain.ServicePeriodMode
import com.dinyairsadot.clearledger.core.util.AttachmentStorage
import com.dinyairsadot.clearledger.core.util.AttachmentUtil
import com.dinyairsadot.clearledger.core.util.InvoiceCsvExportLabels
import com.dinyairsadot.clearledger.core.util.InvoiceCsvExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter


enum class SortOption {
    DATE_DESCENDING,
    DATE_ASCENDING,
    AMOUNT_DESCENDING,
    AMOUNT_ASCENDING
}

enum class SearchMode {
    INVOICE_NUMBER,
    AMOUNT
}

sealed interface InvoiceListScope {
    data object AllInvoices : InvoiceListScope
    data class CategoryInvoices(val categoryId: Long) : InvoiceListScope
}

data class InvoiceUi(
    val id: Long,
    val invoiceNumber: String,
    val amount: Double,
    val paymentStatus: PaymentStatus,
    val vendorName: String? = null,
    val issueDateText: String? = null,
    val dueDateText: String?,
    val dueDate: LocalDate? = null,
    val paymentDateText: String? = null,
    /** Fallback for sort when dueDate is null (service period end). Used for ordering only, not for display. */
    val servicePeriodEnd: LocalDate? = null,
    /** Used for filtering by service period. */
    val servicePeriodStart: LocalDate? = null,
    val servicePeriodStartText: String? = null,
    val servicePeriodEndText: String? = null,
    val notes: String?,
    val customFieldValues: List<String> = emptyList(),
    val documentType: DocumentType? = null,
    // New core fields
    val documentNumber: String = invoiceNumber,  // New field (fallback to invoiceNumber for compatibility)
    val amountDue: Double = amount,              // New field (fallback to amount for compatibility)
    val paymentMethod: String? = null,
    val numberOfPayments: String? = null,
    val confirmationNumber: String? = null,
    // Pinned snapshot
    val pinnedSnapshot: Map<String, String> = emptyMap(),
    // Explicit mode; never infer this from dates.
    val servicePeriodMode: ServicePeriodMode = ServicePeriodMode.MONTH,
    val amountCurrency: InvoiceCurrency = InvoiceCurrency.ILS,
    /** Legacy persisted `content://` Uri string of a not-yet-migrated attachment; null once migrated. */
    val attachmentUri: String? = null,
    /** Filename of the managed, app-private attachment copy; null if none. */
    val attachmentFileName: String? = null,
    /** Original display name of the attachment, for UI. */
    val attachmentDisplayName: String? = null,
    /** MIME type of the attachment, for opening it. */
    val attachmentMimeType: String? = null
) {
    /** True if this invoice currently has an attachment, managed or not-yet-migrated legacy. */
    val hasAttachment: Boolean
        get() = !attachmentFileName.isNullOrBlank() || !attachmentUri.isNullOrBlank()
}

data class InvoiceListUiState(
    val isLoading: Boolean = false,
    val categoryName: String? = null,
    val categoryColorHex: String? = null,
    val categoryCustomFieldTitles: List<String> = emptyList(),
    val sourceInvoices: List<InvoiceUi> = emptyList(),
    val visibleInvoices: List<InvoiceUi> = emptyList(),
    val visibleInvoiceDomains: List<Invoice> = emptyList(),
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val searchMode: SearchMode = SearchMode.INVOICE_NUMBER,
    val servicePeriodStartFilter: LocalDate? = null,
    val servicePeriodEndFilter: LocalDate? = null,
    val statusFilter: PaymentStatus? = null,
    val sortOption: SortOption = SortOption.DATE_DESCENDING
)

class InvoiceListViewModel(
    private val invoiceRepository: InvoiceRepository,
    private val categoryRepository: CategoryRepository,
    private val context: Context
) : ViewModel() {

    private var currentScope: InvoiceListScope? = null
    private var sourceDomainInvoices: List<Invoice> = emptyList()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private val _uiState = MutableStateFlow(InvoiceListUiState(isLoading = true))
    val uiState: StateFlow<InvoiceListUiState> = _uiState.asStateFlow()

    fun loadCategoryHeader(categoryId: Long) {
        viewModelScope.launch {
            try {
                val category = categoryRepository.getCategories().firstOrNull { it.id == categoryId }
                _uiState.value = _uiState.value.copy(
                    categoryName = category?.name,
                    categoryColorHex = category?.colorHex,
                    categoryCustomFieldTitles = category?.customFieldTitles ?: emptyList()
                )
            } catch (_: Exception) {
                // If category can't be loaded, keep title fallback in UI
                _uiState.value = _uiState.value.copy(
                    categoryName = null,
                    categoryColorHex = null,
                    categoryCustomFieldTitles = emptyList()
                )
            }
        }
    }

    /**
     * Load invoices for a given category.
     * This is called from the UI using LaunchedEffect(categoryId).
     */
    fun loadInvoices(categoryId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        val scope = InvoiceListScope.CategoryInvoices(categoryId)
        currentScope = scope

        viewModelScope.launch {
            loadInvoicesForScope(scope)
        }
    }

    private suspend fun loadInvoicesForScope(scope: InvoiceListScope) {
        try {
            val invoices: List<Invoice> = when (scope) {
                is InvoiceListScope.AllInvoices -> invoiceRepository.getAllInvoices()
                is InvoiceListScope.CategoryInvoices -> invoiceRepository.getInvoicesForCategory(scope.categoryId)
            }
            val migratedInvoices = invoices.map { migrateLegacyAttachmentIfNeeded(it) }
            sourceDomainInvoices = migratedInvoices
            val uiInvoices = migratedInvoices.map { it.toUi() }
            updateStateAndRecompute { state ->
                state.copy(
                    isLoading = false,
                    sourceInvoices = uiInvoices,
                    errorMessage = null
                )
            }
        } catch (_: Exception) {
            sourceDomainInvoices = emptyList()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                sourceInvoices = emptyList(),
                visibleInvoices = emptyList(),
                visibleInvoiceDomains = emptyList(),
                errorMessage = context.getString(R.string.failed_to_load_invoices)
            )
        }
    }

    /**
     * Lazily migrates a not-yet-migrated legacy `content://` attachment to a managed,
     * app-private copy, run every time the invoice list containing it is (re)loaded.
     *
     * If the legacy source is still readable, its bytes are copied into
     * `filesDir/invoice_attachments/` (see [AttachmentStorage]), the invoice is updated to
     * reference that managed copy instead, and the old persisted read permission is released
     * once no other persisted invoice still needs it (two invoices may share the same legacy
     * Uri). If the source is no longer accessible, or the copy/DB write fails, the invoice is
     * returned unchanged — its legacy reference (and thus the invoice itself) is never lost;
     * migration is simply retried the next time the list loads.
     */
    private suspend fun migrateLegacyAttachmentIfNeeded(invoice: Invoice): Invoice {
        val legacyUri = invoice.attachmentUri
        if (legacyUri.isNullOrBlank() || !invoice.attachmentFileName.isNullOrBlank()) return invoice

        val sourceUri = runCatching { Uri.parse(legacyUri) }.getOrNull() ?: return invoice
        val copied = AttachmentStorage.copyIntoManagedStorage(context, sourceUri) ?: return invoice

        val migrated = invoice.copy(
            attachmentUri = null,
            attachmentFileName = copied.fileName,
            attachmentDisplayName = copied.displayName,
            attachmentMimeType = copied.mimeType
        )
        return try {
            invoiceRepository.updateInvoice(migrated)
            AttachmentUtil.releaseIfUnreferenced(context, legacyUri) { uri ->
                invoiceRepository.countInvoicesWithAttachmentUri(uri) > 0
            }
            migrated
        } catch (_: Exception) {
            // DB write failed; drop the orphaned copy we just made and keep the invoice as-is.
            AttachmentStorage.deleteManagedFile(context, copied.fileName)
            invoice
        }
    }

    fun buildCsvContent(labels: InvoiceCsvExportLabels): String {
        val state = _uiState.value
        return InvoiceCsvExporter.generate(
            invoices = state.visibleInvoiceDomains,
            categoryName = state.categoryName.orEmpty(),
            customFieldTitles = state.categoryCustomFieldTitles,
            labels = labels
        )
    }

    private fun updateStateAndRecompute(transform: (InvoiceListUiState) -> InvoiceListUiState) {
        val intermediate = transform(_uiState.value)
        val filtered = applySearchAndFilters(intermediate.sourceInvoices, intermediate)
        val sorted = sortInvoices(filtered, intermediate.sortOption)
        val domainById = sourceDomainInvoices.associateBy { it.id }
        val sortedDomains = sorted.mapNotNull { domainById[it.id] }
        _uiState.value = intermediate.copy(
            visibleInvoices = sorted,
            visibleInvoiceDomains = sortedDomains
        )
    }

    private fun applySearchAndFilters(
        invoices: List<InvoiceUi>,
        state: InvoiceListUiState
    ): List<InvoiceUi> {
        val rawQuery = state.searchQuery.trim()

        val afterSearch = if (rawQuery.isBlank()) {
            invoices
        } else {
            when (state.searchMode) {
                SearchMode.INVOICE_NUMBER -> {
                    val normalizedQuery = rawQuery.lowercase()
                    invoices.filter { invoice ->
                        invoice.invoiceNumber.lowercase().contains(normalizedQuery)
                    }
                }
                SearchMode.AMOUNT -> {
                    val query = rawQuery
                    invoices.filter { invoice ->
                        invoice.amount.toString().contains(query)
                    }
                }
            }
        }

        val afterServicePeriod = applyServicePeriodFilters(
            invoices = afterSearch,
            startFilter = state.servicePeriodStartFilter,
            endFilter = state.servicePeriodEndFilter
        )

        val afterStatus = applyStatusFilter(
            invoices = afterServicePeriod,
            statusFilter = state.statusFilter
        )

        return afterStatus
    }

    private fun applyServicePeriodFilters(
        invoices: List<InvoiceUi>,
        startFilter: LocalDate?,
        endFilter: LocalDate?
    ): List<InvoiceUi> {
        if (startFilter == null && endFilter == null) return invoices

        return invoices.filter { invoice ->
            val start = invoice.servicePeriodStart ?: invoice.servicePeriodEnd
            val end = invoice.servicePeriodEnd ?: invoice.servicePeriodStart

            val startOk = startFilter?.let { filter ->
                start != null && !start.isBefore(filter)
            } ?: true

            val endOk = endFilter?.let { filter ->
                end != null && !end.isAfter(filter)
            } ?: true

            startOk && endOk
        }
    }

    private fun applyStatusFilter(
        invoices: List<InvoiceUi>,
        statusFilter: PaymentStatus?
    ): List<InvoiceUi> {
        return when (statusFilter) {
            null -> invoices // All
            PaymentStatus.PAID -> invoices.filter { it.paymentStatus == PaymentStatus.PAID }
            PaymentStatus.NOT_PAID -> invoices.filter { it.paymentStatus == PaymentStatus.NOT_PAID }
        }
    }

    fun setSortOption(sortOption: SortOption) {
        updateStateAndRecompute { state ->
            state.copy(sortOption = sortOption)
        }
    }

    fun setSearchQuery(query: String) {
        updateStateAndRecompute { state ->
            state.copy(searchQuery = query)
        }
    }

    fun setSearchMode(mode: SearchMode) {
        updateStateAndRecompute { state ->
            state.copy(searchMode = mode)
        }
    }

    fun setServicePeriodStartFilter(date: LocalDate?) {
        updateStateAndRecompute { state ->
            state.copy(servicePeriodStartFilter = date)
        }
    }

    fun setServicePeriodEndFilter(date: LocalDate?) {
        updateStateAndRecompute { state ->
            state.copy(servicePeriodEndFilter = date)
        }
    }

    /**
     * null means "All" (no filtering by status).
     */
    fun setStatusFilter(status: PaymentStatus?) {
        updateStateAndRecompute { state ->
            state.copy(statusFilter = status)
        }
    }

    fun clearFilters() {
        updateStateAndRecompute { state ->
            state.copy(
                servicePeriodStartFilter = null,
                servicePeriodEndFilter = null,
                statusFilter = null
            )
        }
    }
    
    private fun sortInvoices(invoices: List<InvoiceUi>, sortOption: SortOption): List<InvoiceUi> {
        return when (sortOption) {
            SortOption.DATE_DESCENDING -> invoices.sortedWith(
                compareByDescending<InvoiceUi> { it.dueDate ?: it.servicePeriodEnd ?: LocalDate.MIN }
                    .thenByDescending { it.id }
            )
            SortOption.DATE_ASCENDING -> invoices.sortedWith(
                compareBy<InvoiceUi> { it.dueDate ?: it.servicePeriodEnd ?: LocalDate.MAX }
                    .thenBy { it.id }
            )
            SortOption.AMOUNT_DESCENDING -> invoices.sortedWith(
                compareByDescending<InvoiceUi> { it.amount }
                    .thenByDescending { it.id }
            )
            SortOption.AMOUNT_ASCENDING -> invoices.sortedWith(
                compareBy<InvoiceUi> { it.amount }
                    .thenBy { it.id }
            )
        }
    }

    fun addInvoice(
        categoryId: Long,
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
        customFieldValues: List<String> = emptyList(),
        amountCurrency: InvoiceCurrency = InvoiceCurrency.ILS,
        attachmentFileName: String? = null,
        attachmentDisplayName: String? = null,
        attachmentMimeType: String? = null
    ) {
        viewModelScope.launch {
            try {
                fun parseDate(dateText: String): LocalDate? {
                    val trimmed = dateText.trim()
                    return runCatching {
                        LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }.getOrNull()
                }

                val servicePeriodStart = parseDate(servicePeriodStartText)
                val servicePeriodEnd = parseDate(servicePeriodEndText)

                // Snapshot category's pinned defaults at invoice creation time
                val category = categoryRepository.getCategories().firstOrNull { it.id == categoryId }
                val pinnedSnapshot = category?.pinnedDefaults ?: emptyMap()

                // Use id=0 to let Room auto-generate the ID
                val newInvoice = Invoice(
                    id = 0,
                    categoryId = categoryId,
                    // Old fields (backward compatibility)
                    invoiceNumber = documentNumber,  // Map new to old
                    amount = amountDue,             // Map new to old
                    paymentStatus = paymentStatus,
                    vendorName = vendorName,
                    issueDate = null,
                    dueDate = dueDate,
                    paymentDate = paymentDate,
                    servicePeriodStart = servicePeriodStart,
                    servicePeriodEnd = servicePeriodEnd,
                    consumptionValue = null,
                    consumptionUnit = null,
                    notes = notes.ifBlank { null },
                    customFieldValues = customFieldValues.map { it.trim() },
                    documentType = null,
                    // New fields
                    amountDue = amountDue,
                    documentNumber = documentNumber,
                    paymentMethod = paymentMethod,
                    numberOfPayments = numberOfPayments,
                    confirmationNumber = confirmationNumber,
                    // Pinned snapshot: capture category defaults at creation time
                    pinnedSnapshot = pinnedSnapshot,
                    servicePeriodMode = servicePeriodMode,
                    amountCurrency = amountCurrency,
                    // New invoices always go through the copy-on-pick flow, so they only ever
                    // have a managed attachment reference — never a legacy attachmentUri.
                    attachmentFileName = attachmentFileName,
                    attachmentDisplayName = attachmentDisplayName,
                    attachmentMimeType = attachmentMimeType
                )

                invoiceRepository.addInvoice(newInvoice)
                loadInvoices(categoryId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = context.getString(R.string.failed_to_save_invoice)
                )
            }
        }
    }

    suspend fun getInvoice(invoiceId: Long): Invoice? {
        return invoiceRepository.getInvoiceById(invoiceId)
    }

    fun updateInvoice(
        invoiceId: Long,
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
        customFieldValues: List<String> = emptyList(),
        amountCurrency: InvoiceCurrency = InvoiceCurrency.ILS,
        attachmentFileName: String? = null,
        attachmentDisplayName: String? = null,
        attachmentMimeType: String? = null,
        attachmentUri: String? = null
    ) {
        viewModelScope.launch {
            try {
                val existing = invoiceRepository.getInvoiceById(invoiceId) ?: return@launch

                fun parseDate(dateText: String): LocalDate? {
                    val trimmed = dateText.trim()
                    return runCatching {
                        LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }.getOrNull()
                }

                val servicePeriodStart = parseDate(servicePeriodStartText)
                val servicePeriodEnd = parseDate(servicePeriodEndText)

                val updated = existing.copy(
                    // Old fields (backward compatibility)
                    invoiceNumber = documentNumber,
                    amount = amountDue,
                    paymentStatus = paymentStatus,
                    vendorName = vendorName,
                    dueDate = dueDate,
                    paymentDate = paymentDate,
                    servicePeriodStart = servicePeriodStart,
                    servicePeriodEnd = servicePeriodEnd,
                    notes = notes.ifBlank { null },
                    customFieldValues = customFieldValues.map { it.trim() },
                    // New fields
                    amountDue = amountDue,
                    documentNumber = documentNumber,
                    paymentMethod = paymentMethod,
                    numberOfPayments = numberOfPayments,
                    confirmationNumber = confirmationNumber,
                    servicePeriodMode = servicePeriodMode,
                    amountCurrency = amountCurrency,
                    attachmentFileName = attachmentFileName,
                    attachmentDisplayName = attachmentDisplayName,
                    attachmentMimeType = attachmentMimeType,
                    attachmentUri = attachmentUri
                )

                invoiceRepository.updateInvoice(updated)

                // The old managed file (if replaced or removed) may still be referenced by a
                // *different* invoice, so only delete it once no persisted invoice needs it
                // anymore. The new managed file was already copied when the user picked it.
                if (existing.attachmentFileName != attachmentFileName) {
                    existing.attachmentFileName?.takeIf { it.isNotBlank() }?.let { oldFileName ->
                        val stillReferenced = invoiceRepository
                            .countInvoicesWithAttachmentFileName(oldFileName) > 0
                        if (!stillReferenced) {
                            AttachmentStorage.deleteManagedFile(context, oldFileName)
                        }
                    }
                }

                // The old legacy attachment (if replaced or removed) may still be referenced by
                // a *different* invoice that happens to point at the same Uri, so only release
                // its persisted SAF read permission once no persisted invoice needs it anymore.
                if (existing.attachmentUri != attachmentUri) {
                    AttachmentUtil.releaseIfUnreferenced(context, existing.attachmentUri) { uri ->
                        invoiceRepository.countInvoicesWithAttachmentUri(uri) > 0
                    }
                }

                // Refresh list so InvoiceListScreen sees updated data
                loadInvoices(existing.categoryId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = context.getString(R.string.failed_to_save_invoice)
                )
            }
        }
    }

    fun deleteInvoice(
        invoiceId: Long,
        categoryId: Long
    ) {
        viewModelScope.launch {
            try {
                val existing = invoiceRepository.getInvoiceById(invoiceId)
                invoiceRepository.deleteInvoice(invoiceId)
                // Another invoice may still reference the same managed file; only delete it
                // once no persisted invoice needs it anymore.
                existing?.attachmentFileName?.takeIf { it.isNotBlank() }?.let { fileName ->
                    val stillReferenced = invoiceRepository
                        .countInvoicesWithAttachmentFileName(fileName) > 0
                    if (!stillReferenced) {
                        AttachmentStorage.deleteManagedFile(context, fileName)
                    }
                }
                // Another invoice may still reference the same legacy attachment Uri; only
                // release the persisted permission once no persisted invoice needs it anymore.
                existing?.attachmentUri?.let { uri ->
                    AttachmentUtil.releaseIfUnreferenced(context, uri) { candidateUri ->
                        invoiceRepository.countInvoicesWithAttachmentUri(candidateUri) > 0
                    }
                }
                // Refresh list so InvoiceListScreen sees updated data
                loadInvoices(categoryId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = context.getString(R.string.failed_to_delete_invoice)
                )
            }
        }
    }
}

// Mapping from domain model to UI model
private fun Invoice.toUi(): InvoiceUi {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    return InvoiceUi(
        id = this.id,
        invoiceNumber = this.invoiceNumber,
        amount = this.amount,
        paymentStatus = this.paymentStatus,
        vendorName = this.vendorName,
        issueDateText = this.issueDate?.format(dateFormatter),
        dueDateText = this.dueDate?.format(dateFormatter),
        dueDate = this.dueDate,
        paymentDateText = this.paymentDate?.format(dateFormatter),
        servicePeriodEnd = this.servicePeriodEnd,
        servicePeriodStart = this.servicePeriodStart,
        servicePeriodStartText = this.servicePeriodStart?.format(dateFormatter),
        servicePeriodEndText = this.servicePeriodEnd?.format(dateFormatter),
        notes = this.notes,
        customFieldValues = this.customFieldValues,
        documentType = this.documentType,
        // New core fields
        documentNumber = this.documentNumber,
        amountDue = this.amountDue,
        paymentMethod = this.paymentMethod,
        numberOfPayments = this.numberOfPayments,
        confirmationNumber = this.confirmationNumber,
        pinnedSnapshot = this.pinnedSnapshot,
        servicePeriodMode = this.servicePeriodMode,
        amountCurrency = this.amountCurrency,
        attachmentUri = this.attachmentUri,
        attachmentFileName = this.attachmentFileName,
        attachmentDisplayName = this.attachmentDisplayName,
        attachmentMimeType = this.attachmentMimeType
    )
}
