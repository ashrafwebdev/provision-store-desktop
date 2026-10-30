package com.provisionstore.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.provisionstore.app.util.inputUnit
import com.provisionstore.app.util.money
import com.provisionstore.app.util.moneyInput
import com.provisionstore.app.util.parseEntry
import com.provisionstore.app.util.parseMoney
import com.provisionstore.app.util.quickAmounts
import com.provisionstore.app.util.toEntry
import com.provisionstore.app.util.unitChoices
import com.provisionstore.desktop.data.Customer
import com.provisionstore.desktop.data.Item
import com.provisionstore.desktop.data.PaymentMode
import kotlin.math.abs
import kotlin.math.roundToLong

/** Categories offered when adding an expense. */
val expenseCategories = listOf(
    "Rent", "Electricity", "Salary", "Transport", "Shop expenses", "Tea and snacks", "Other",
)

private val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal)

/**
 * Asks for a quantity. Kg and litre items take grams and millilitres, with quick buttons
 * for common amounts. [initial] is a stored quantity, or null for an empty box.
 */
@Composable
fun QuantityDialog(
    title: String,
    unit: String,
    pricePerUnit: Long,
    priceLabel: String = "Price",
    initial: Double?,
    confirmText: String,
    onConfirm: (Double) -> Unit,
    onRemove: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.toEntry(unit) ?: "") }
    val quantity = parseEntry(text, unit)?.takeIf { it > 0 }
    val quick = quickAmounts(unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (quick.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        quick.forEach { (label, amount) ->
                            FilterChip(
                                selected = quantity != null && abs(quantity - amount) < 0.0001,
                                onClick = { text = amount.toEntry(unit) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Quantity (${inputUnit(unit)})") },
                    singleLine = true,
                    keyboardOptions = decimalKeys,
                )
                if (quantity != null) {
                    Text(
                        "$priceLabel: ${(pricePerUnit * quantity).roundToLong().money()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = quantity != null, onClick = { quantity?.let(onConfirm) }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Choose how a bill is paid, and the customer for credit. Used by the Sell screen. */
@Composable
fun CheckoutDialog(
    total: Long,
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMode, Long?) -> Unit,
) {
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var customerId by remember { mutableStateOf<Long?>(null) }
    val canConfirm = mode != PaymentMode.CREDIT || customerId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Total ${total.money()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentChips(mode) { mode = it }
                if (mode == PaymentMode.CREDIT) {
                    CustomerChips(customers, customerId) { customerId = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(mode, if (mode == PaymentMode.CREDIT) customerId else null) },
            ) { Text("Save bill") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A day-book sale: total and how it was paid. */
@Composable
fun SaleDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onSave: (Long, PaymentMode, Long?) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var customerId by remember { mutableStateOf<Long?>(null) }
    val amount = parseMoney(amountText)?.takeIf { it > 0 }
    val canSave = amount != null && (mode != PaymentMode.CREDIT || customerId != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add sale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Total sale (₹)") },
                    singleLine = true,
                    keyboardOptions = decimalKeys,
                )
                PaymentChips(mode) { mode = it }
                if (mode == PaymentMode.CREDIT) {
                    CustomerChips(customers, customerId) { customerId = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                amount?.let { onSave(it, mode, if (mode == PaymentMode.CREDIT) customerId else null) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PurchaseDialog(onDismiss: () -> Unit, onSave: (Long, String, String) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = parseMoney(amountText)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add purchase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Total bill (₹)") },
                    singleLine = true,
                    keyboardOptions = decimalKeys,
                )
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = {
                amount?.let { onSave(it, supplier.trim(), note.trim()) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ExpenseDialog(onDismiss: () -> Unit, onSave: (String, Long, String) -> Unit) {
    var category by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = parseMoney(amountText)?.takeIf { it > 0 }
    val chosen = category

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    expenseCategories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = decimalKeys,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null && chosen != null, onClick = {
                if (amount != null && chosen != null) onSave(chosen, amount, note.trim())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PaymentDialog(customerName: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    val amount = parseMoney(amountText)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payment from $customerName") },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount (₹)") },
                singleLine = true,
                keyboardOptions = decimalKeys,
            )
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = { amount?.let(onSave) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CustomerDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), phone.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Add or edit an item. For kg and litre items the stock and warning levels are typed in g or ml. */
@Composable
fun ItemDialog(
    item: Item?,
    onDismiss: () -> Unit,
    onSave: (Item) -> Unit,
    onDelete: (Item) -> Unit,
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var unit by remember { mutableStateOf(item?.unit ?: "pc") }
    var sell by remember { mutableStateOf(item?.sellPrice?.moneyInput() ?: "") }
    var cost by remember { mutableStateOf(item?.costPrice?.moneyInput() ?: "") }
    var stock by remember { mutableStateOf(item?.let { it.stock.toEntry(it.unit) } ?: "") }
    var reorder by remember { mutableStateOf(item?.let { it.reorderLevel.toEntry(it.unit) } ?: "") }

    val sellPrice = parseMoney(sell)
    val valid = name.isNotBlank() && sellPrice != null && sellPrice > 0
    val choices = (unitChoices() + unit).distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add item" else "Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "For kg and litre items, type grams or ml. Example: 250 means 250 g.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    choices.forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) })
                    }
                }
                OutlinedTextField(
                    value = sell,
                    onValueChange = { sell = it },
                    label = { Text("Selling price (₹ per $unit)") },
                    singleLine = true,
                    keyboardOptions = decimalKeys,
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("Cost price (₹ per $unit)") },
                    singleLine = true,
                    keyboardOptions = decimalKeys,
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Stock on hand (${inputUnit(unit)})") },
                    singleLine = true,
                    keyboardOptions = decimalKeys,
                )
                OutlinedTextField(
                    value = reorder,
                    onValueChange = { reorder = it },
                    label = { Text("Warn when below (${inputUnit(unit)})") },
                    singleLine = true,
                    keyboardOptions = decimalKeys,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    Item(
                        id = item?.id ?: 0L,
                        name = name.trim(),
                        unit = unit.trim(),
                        sellPrice = sellPrice ?: 0L,
                        costPrice = parseMoney(cost) ?: 0L,
                        stock = parseEntry(stock, unit) ?: 0.0,
                        reorderLevel = parseEntry(reorder, unit) ?: 0.0,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (item != null) {
                    TextButton(onClick = { onDelete(item) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PaymentChips(selected: PaymentMode, onSelect: (PaymentMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaymentMode.entries.forEach { m ->
            FilterChip(selected = selected == m, onClick = { onSelect(m) }, label = { Text(m.name) })
        }
    }
}

@Composable
private fun CustomerChips(customers: List<Customer>, selected: Long?, onSelect: (Long) -> Unit) {
    Text("Customer", style = MaterialTheme.typography.labelLarge)
    if (customers.isEmpty()) Text("Add a customer in the Khata tab first.")
    Column(
        Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        customers.forEach { c ->
            FilterChip(
                selected = selected == c.id,
                onClick = { onSelect(c.id) },
                label = { Text("${c.name} (${c.balance.money()})") },
            )
        }
    }
}
