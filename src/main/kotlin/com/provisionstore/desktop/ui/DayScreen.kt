package com.provisionstore.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.provisionstore.app.util.DAY_MS
import com.provisionstore.app.util.dayLabel
import com.provisionstore.app.util.money
import com.provisionstore.app.util.startOfToday
import com.provisionstore.app.util.timeOnly
import com.provisionstore.desktop.data.PaymentMode
import com.provisionstore.desktop.data.Store
import java.io.File
import kotlin.system.exitProcess

/** One line in the day's entries list. */
private class DayRow(
    val time: Long,
    val title: String,
    val detail: String,
    val amount: Long,
    val onDelete: () -> Unit,
)

@Composable
fun DayScreen(store: Store, onMessage: (String) -> Unit) {
    val revision = store.revision
    var day by remember { mutableStateOf(startOfToday()) }
    var adding by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<DayRow?>(null) }
    var restoreFrom by remember { mutableStateOf<File?>(null) }

    val end = day + DAY_MS
    val sales = remember(revision, day) { store.sales(day, end) }
    val lines = remember(revision, day) { store.saleLines(day, end) }
    val payments = remember(revision, day) { store.payments(day, end) }
    val purchases = remember(revision, day) { store.purchases(day, end) }
    val expenses = remember(revision, day) { store.expenses(day, end) }
    val customers = remember(revision) { store.customers() }
    val customerNames = customers.associate { it.id to it.name }

    // Sales from the Sell screen have item lines. The rest are day-book sales.
    val itemSaleIds = lines.map { it.saleId }.toSet()
    val quickSales = sales.filter { it.id !in itemSaleIds }

    val salesTotal = sales.sumOf { it.total }
    fun modeTotal(mode: PaymentMode): Long = sales.filter { it.paymentMode == mode.name }.sumOf { it.total }
    val received = -payments.sumOf { it.amount }
    val purchaseTotal = purchases.sumOf { it.amount }
    val expenseTotal = expenses.sumOf { it.amount }
    val leftover = salesTotal - purchaseTotal - expenseTotal
    val owed = customers.filter { it.balance > 0 }.sumOf { it.balance }

    val rows = (
        quickSales.map { s ->
            DayRow(
                time = s.createdAt,
                title = "Sale (${s.paymentMode.lowercase()})" +
                    (s.customerId?.let { customerNames[it] }?.let { " - $it" } ?: ""),
                detail = timeOnly(s.createdAt),
                amount = s.total,
                onDelete = { store.deleteQuickSale(s.id) },
            )
        } +
            purchases.map { p ->
                DayRow(
                    time = p.createdAt,
                    title = "Purchase" + if (p.supplier.isNotBlank()) " - ${p.supplier}" else "",
                    detail = listOf(timeOnly(p.createdAt), p.note).filter { it.isNotBlank() }.joinToString("  "),
                    amount = p.amount,
                    onDelete = { store.deletePurchase(p.id) },
                )
            } +
            expenses.map { e ->
                DayRow(
                    time = e.createdAt,
                    title = "Expense - ${e.category}",
                    detail = listOf(timeOnly(e.createdAt), e.note).filter { it.isNotBlank() }.joinToString("  "),
                    amount = e.amount,
                    onDelete = { store.deleteExpense(e.id) },
                )
            }
        ).sortedByDescending { it.time }

    // Entries are recorded at the current time of day, on the selected date.
    fun entryTime(): Long = day + (System.currentTimeMillis() - startOfToday())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { day -= DAY_MS }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day") }
            Text(dayLabel(day), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { day += DAY_MS }, enabled = day < startOfToday()) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
            }
        }

        StatRow("Sales (${sales.size} bills)", salesTotal.money(), big = true)
        StatRow("   Cash", modeTotal(PaymentMode.CASH).money())
        StatRow("   UPI", modeTotal(PaymentMode.UPI).money())
        StatRow("   On credit (khata)", modeTotal(PaymentMode.CREDIT).money())
        StatRow("Payments received from khata", received.money())
        StatRow("Purchases", purchaseTotal.money())
        StatRow("Expenses", expenseTotal.money())
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        StatRow("Left after purchases and expenses", leftover.money(), big = true)
        StatRow("Customers owe you (total)", owed.money())

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { adding = "sale" }) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Sale")
            }
            Button(onClick = { adding = "purchase" }) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Purchase")
            }
            Button(onClick = { adding = "expense" }) {
                Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Expense")
            }
            OutlinedButton(onClick = {
                printLines(
                    buildList {
                        add("Provision Store - ${dayLabel(day)}")
                        add("")
                        add("Sales: ${salesTotal.money()} (${sales.size} bills)")
                        add("   Cash: ${modeTotal(PaymentMode.CASH).money()}")
                        add("   UPI: ${modeTotal(PaymentMode.UPI).money()}")
                        add("   Credit: ${modeTotal(PaymentMode.CREDIT).money()}")
                        add("Payments received: ${received.money()}")
                        add("Purchases: ${purchaseTotal.money()}")
                        add("Expenses: ${expenseTotal.money()}")
                        add("Left after purchases and expenses: ${leftover.money()}")
                        add("")
                        rows.forEach { add("${timeOnly(it.time)}  ${it.title}  ${it.amount.money()}") }
                    }
                )
            }) {
                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Print day report")
            }
            OutlinedButton(onClick = {
                val target = chooseBackupTarget() ?: return@OutlinedButton
                store.backupTo(target)
                onMessage("Backup saved to ${target.name}.")
            }) { Text("Back up") }
            OutlinedButton(onClick = {
                restoreFrom = chooseBackupSource()
            }) { Text("Restore") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Entries", style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) {
            Text("Nothing entered for this day yet.", modifier = Modifier.padding(top = 4.dp))
        }
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.title)
                    Text(row.detail, style = MaterialTheme.typography.bodySmall)
                }
                Text(row.amount.money())
                IconButton(onClick = { confirmDelete = row }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                }
            }
        }
    }

    restoreFrom?.let { source ->
        ConfirmDialog(
            title = "Restore this backup?",
            text = "All current data on this computer is replaced with ${source.name}. The app will close; open it again to see the restored data.",
            confirmText = "Restore and close",
            onConfirm = {
                // Restoring overwrites the database file, so the connection must close first.
                store.close()
                source.copyTo(store.file, overwrite = true)
                exitProcess(0)
            },
            onDismiss = { restoreFrom = null },
        )
    }

    when (adding) {
        "sale" -> SaleDialog(
            customers = customers,
            onDismiss = { adding = null },
            onSave = { amount, mode, customerId ->
                store.quickSale(amount, mode, customerId, entryTime())
                adding = null
            },
        )
        "purchase" -> PurchaseDialog(
            onDismiss = { adding = null },
            onSave = { amount, supplier, note ->
                store.addPurchase(amount, supplier, note, entryTime())
                adding = null
            },
        )
        "expense" -> ExpenseDialog(
            onDismiss = { adding = null },
            onSave = { category, amount, note ->
                store.addExpense(category, amount, note, entryTime())
                adding = null
            },
        )
    }

    confirmDelete?.let { row ->
        ConfirmDialog(
            title = "Delete this entry?",
            text = "${row.title}  ${row.amount.money()}",
            confirmText = "Delete",
            onConfirm = {
                row.onDelete()
                onMessage("Entry deleted.")
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, big: Boolean = false) {
    val style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, style = style)
    }
}
