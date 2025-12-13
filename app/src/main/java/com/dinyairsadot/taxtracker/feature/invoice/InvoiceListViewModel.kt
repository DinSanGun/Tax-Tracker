package com.dinyairsadot.taxtracker.feature.invoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dinyairsadot.taxtracker.core.domain.Invoice
import com.dinyairsadot.taxtracker.core.domain.InvoiceRepository
import com.dinyairsadot.taxtracker.core.domain.PaymentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate


data class InvoiceUi(
    val id: Long,
    val invoiceNumber: String,
    val amount: Double,
    val paymentStatus: PaymentStatus,
    val dueDateText: String?,
    val notes: String?
)

data class InvoiceListUiState(
    val isLoading: Boolean = false,
    val invoices: List<InvoiceUi> = emptyList(),
    val errorMessage: String? = null
)

class InvoiceListViewModel(
    private val invoiceRepository: InvoiceRepository = InMemoryInvoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceListUiState(isLoading = true))
    val uiState: StateFlow<InvoiceListUiState> = _uiState.asStateFlow()

    /**
     * Load invoices for a given category.
     * This is called from the UI using LaunchedEffect(categoryId).
     */
    fun loadInvoices(categoryId: Long) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val invoices = invoiceRepository.getInvoicesForCategory(categoryId)
                _uiState.value = InvoiceListUiState(
                    isLoading = false,
                    invoices = invoices.map { it.toUi() },
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = InvoiceListUiState(
                    isLoading = false,
                    invoices = emptyList(),
                    errorMessage = "Failed to load invoices"
                )
            }
        }
    }

    fun addInvoice(
        categoryId: Long,
        amount: Double,
        dateText: String,
        paymentStatus: PaymentStatus,
        notes: String
    ) {
        viewModelScope.launch {
            // Compute next id based on current invoices from repository
            val existing = invoiceRepository.getInvoicesForCategory(categoryId)
            val nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1L

            val parsedDate = dateText
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

            val newInvoice = Invoice(
                id = nextId,
                categoryId = categoryId,
                invoiceNumber = "", // can be expanded later
                amount = amount,
                paymentStatus = paymentStatus,
                dueDate = parsedDate,
                paymentDate = null,
                consumptionValue = null,
                consumptionUnit = null,
                notes = notes.ifBlank { null }
            )

            invoiceRepository.addInvoice(newInvoice)
            loadInvoices(categoryId)
        }
    }

    suspend fun getInvoice(invoiceId: Long): Invoice? {
        return invoiceRepository.getInvoiceById(invoiceId)
    }

    fun updateInvoice(
        invoiceId: Long,
        amount: Double,
        dateText: String,
        paymentStatus: PaymentStatus,
        notes: String
    ) {
        viewModelScope.launch {
            val existing = invoiceRepository.getInvoiceById(invoiceId) ?: return@launch

            val parsedDate = dateText
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

            val updated = existing.copy(
                amount = amount,
                paymentStatus = paymentStatus,
                dueDate = parsedDate,
                notes = notes.ifBlank { null }
            )

            invoiceRepository.updateInvoice(updated)
            // Reload list for this category so the InvoiceListScreen sees the changes
            loadInvoices(existing.categoryId)
        }
    }
}

// Mapping from domain model to UI model
private fun Invoice.toUi(): InvoiceUi {
    return InvoiceUi(
        id = this.id,
        invoiceNumber = this.invoiceNumber,
        amount = this.amount,
        paymentStatus = this.paymentStatus,
        dueDateText = this.dueDate?.toString(), // later we can pretty-format
        notes = this.notes
    )
}
