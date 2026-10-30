package com.provisionstore.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.provisionstore.app.util.dateTime
import com.provisionstore.app.util.money
import com.provisionstore.desktop.data.Customer
import com.provisionstore.desktop.data.Store

@Composable
fun KhataScreen(store: Store, onMessage: (String) -> Unit) {
    val revision = store.revision
    val customers = remember(revision) { store.customers() }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf(false) }
    val selected = customers.firstOrNull { it.id == selectedId }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(340.dp).fillMaxHeight()) {
            Text("Khata (credit)", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Add customer")
            }
            if (customers.isEmpty()) Text("No customers yet.")
            LazyColumn(Modifier.weight(1f)) {
                items(customers, key = { it.id }) { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { selectedId = c.id },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                if (c.phone.isNotBlank()) Text(c.phone)
                            }
                            Text(balanceText(c.balance))
                        }
                    }
                }
            }
        }

        if (selected == null) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Select a customer to see the account.")
            }
        } else {
            CustomerAccount(
                store = store,
                customer = selected,
                onPay = { paying = true },
                onPrint = { onMessage("Statement printed.") },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }

    if (adding) {
        CustomerDialog(
            onDismiss = { adding = false },
            onSave = { name, phone ->
                store.addCustomer(name, phone)
                onMessage("Added $name.")
                adding = false
            },
        )
    }

    if (paying && selected != null) {
        PaymentDialog(
            customerName = selected.name,
            onDismiss = { paying = false },
            onSave = { amount ->
                store.recordPayment(selected.id, amount, System.currentTimeMillis())
                onMessage("Payment of ${amount.money()} recorded.")
                paying = false
            },
        )
    }
}

@Composable
private fun CustomerAccount(
    store: Store,
    customer: Customer,
    onPay: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier,
) {
    val revision = store.revision
    val ledger = remember(revision, customer.id) { store.ledgerFor(customer.id) }

    Column(modifier) {
        Text(customer.name, style = MaterialTheme.typography.headlineSmall)
        if (customer.phone.isNotBlank()) Text(customer.phone)
        Spacer(Modifier.height(8.dp))
        Text(
            balanceText(customer.balance),
            style = MaterialTheme.typography.titleLarge,
            color = if (customer.balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPay) {
                Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Record payment")
            }
            OutlinedButton(onClick = {
                printLines(
                    buildList {
                        add("Account statement - ${customer.name}")
                        add("")
                        ledger.sortedBy { it.createdAt }.forEach { e ->
                            val kind = if (e.amount < 0) "Paid" else "Credit"
                            add("${dateTime(e.createdAt)}  ${e.note}  $kind ${kotlin.math.abs(e.amount).money()}")
                        }
                        add("")
                        add("Balance due: ${customer.balance.money()}")
                    }
                )
                onPrint()
            }) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Print statement")
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("History", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(ledger, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.note)
                        Text(dateTime(entry.createdAt), style = MaterialTheme.typography.bodySmall)
                    }
                    val payment = entry.amount < 0
                    Text(
                        if (payment) "- ${(-entry.amount).money()}" else "+ ${entry.amount.money()}",
                        color = if (payment) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun balanceText(balance: Long): String = when {
    balance > 0 -> "Owes ${balance.money()}"
    balance < 0 -> "Advance ${(-balance).money()}"
    else -> "Settled"
}
