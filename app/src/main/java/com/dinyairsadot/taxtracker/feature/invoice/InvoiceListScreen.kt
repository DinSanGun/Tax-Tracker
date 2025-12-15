package com.dinyairsadot.taxtracker.feature.invoice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    categoryId: Long,
    uiState: InvoiceListUiState,
    onBackClick: () -> Unit,
    onEditCategoryClick: () -> Unit,
    onAddInvoiceClick: () -> Unit,
    onInvoiceClick: (Long) -> Unit,
    onDeleteInvoice: (Long) -> Unit
) {

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingDeleteInvoiceId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.categoryName ?: "Invoices") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onEditCategoryClick) {
                        Text("Edit category")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddInvoiceClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add invoice"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.errorMessage != null -> {
                    ErrorState(
                        message = uiState.errorMessage,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.invoices.isEmpty() -> {
                    EmptyInvoicesState(
                        categoryId = categoryId,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    InvoiceListContent(
                        invoices = uiState.invoices,
                        modifier = Modifier.fillMaxSize(),
                        onInvoiceClick = onInvoiceClick,
                        onRequestDeleteInvoice = { id -> pendingDeleteInvoiceId = id }
                    )
                }
            }
        }
    }

    pendingDeleteInvoiceId?.let { invoiceId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteInvoiceId = null },
            title = { Text("Delete invoice?") },
            text = { Text("Are you sure you want to delete this invoice? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteInvoice(invoiceId)
                        pendingDeleteInvoiceId = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invoice deleted")
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteInvoiceId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text(
            text = "Please try again later.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EmptyInvoicesState(
    categoryId: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No invoices yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.padding(top = 4.dp))
        Text(
            text = "Tap + to add your first invoice for this category.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun InvoiceListContent(
    invoices: List<InvoiceUi>,
    modifier: Modifier = Modifier,
    onInvoiceClick: (Long) -> Unit,
    onRequestDeleteInvoice: (Long) -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(invoices) { invoice ->
            InvoiceItem(
                invoice = invoice,
                onClick = { onInvoiceClick(invoice.id) },
                onDeleteClick = { onRequestDeleteInvoice(invoice.id) }
            )
        }
    }
}

@Composable
private fun InvoiceItem(
    invoice: InvoiceUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Left content (everything that should flow top->bottom)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 84.dp) // reserve space for amount + delete so they never overlap text
            ) {
                Text(
                    text = invoice.invoiceNumber.ifBlank { "Invoice #${invoice.id}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.padding(top = 4.dp))

                Text(
                    text = when (invoice.paymentStatus) {
                        com.dinyairsadot.taxtracker.core.domain.PaymentStatus.PAID_FULL -> "Paid in full"
                        com.dinyairsadot.taxtracker.core.domain.PaymentStatus.NOT_PAID -> "Not paid"
                        com.dinyairsadot.taxtracker.core.domain.PaymentStatus.PAID_CREDIT -> "Paid with credit"
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                invoice.dueDateText?.let { due ->
                    Text(
                        text = "Due: $due",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                invoice.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Right content pinned to the vertical center of the entire card content
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatAmountILS(invoice.amount),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete invoice",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatAmountILS(amount: Double): String {
    return "₪%.2f".format(amount)
}

