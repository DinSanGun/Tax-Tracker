package com.dinyairsadot.taxtracker.feature.invoice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dinyairsadot.taxtracker.core.domain.PaymentStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInvoiceScreen(
    categoryId: Long,
    onNavigateBack: () -> Unit,
    onSaveInvoice: (
        amount: Double,
        dateText: String,
        paymentStatus: PaymentStatus,
        notes: String
    ) -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var paymentStatus by rememberSaveable { mutableStateOf(PaymentStatus.NOT_PAID) }

    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var dateError by rememberSaveable { mutableStateOf<String?>(null) }

    fun handleSave() {
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            amountError = "Enter a valid amount"
            return
        } else {
            amountError = null
        }

        if (dateText.isNotBlank() && dateText.length < 8) {
            // very light validation, proper parsing happens in ViewModel
            dateError = "Use format YYYY-MM-DD or leave empty"
            return
        } else {
            dateError = null
        }

        onSaveInvoice(amount, dateText.trim(), paymentStatus, notes.trim())
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add invoice") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Category ID: $categoryId",
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.padding(top = 12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") },
                isError = amountError != null,
                supportingText = amountError?.let { msg -> { Text(msg) } }
            )

            Spacer(modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date (YYYY-MM-DD, optional)") },
                isError = dateError != null,
                supportingText = dateError?.let { msg -> { Text(msg) } }
            )

            Spacer(modifier = Modifier.padding(top = 8.dp))

            Text(text = "Payment status", fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.padding(top = 4.dp))

            PaymentStatusSelector(
                selected = paymentStatus,
                onSelectedChange = { paymentStatus = it }
            )

            Spacer(modifier = Modifier.padding(top = 8.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes (optional)") },
                minLines = 3
            )

            Spacer(modifier = Modifier.padding(top = 16.dp))

            Button(
                onClick = { handleSave() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save invoice")
            }
        }
    }
}

@Composable
private fun PaymentStatusSelector(
    selected: PaymentStatus,
    onSelectedChange: (PaymentStatus) -> Unit
) {
    // Simple row of text buttons; can be styled better later
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(
            onClick = { onSelectedChange(PaymentStatus.NOT_PAID) }
        ) {
            Text(
                text = "Not paid",
                fontWeight = if (selected == PaymentStatus.NOT_PAID) FontWeight.Bold else FontWeight.Normal
            )
        }
        TextButton(
            onClick = { onSelectedChange(PaymentStatus.PAID_FULL) }
        ) {
            Text(
                text = "Paid full",
                fontWeight = if (selected == PaymentStatus.PAID_FULL) FontWeight.Bold else FontWeight.Normal
            )
        }
        TextButton(
            onClick = { onSelectedChange(PaymentStatus.PAID_CREDIT) }
        ) {
            Text(
                text = "Paid credit",
                fontWeight = if (selected == PaymentStatus.PAID_CREDIT) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
