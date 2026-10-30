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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.provisionstore.app.util.money
import com.provisionstore.app.util.parseEntry
import com.provisionstore.app.util.perUnit
import com.provisionstore.app.util.stockText
import com.provisionstore.desktop.data.CartLine
import com.provisionstore.desktop.data.Item
import com.provisionstore.desktop.data.Store

@Composable
fun SellScreen(store: Store, onMessage: (String) -> Unit) {
    val revision = store.revision
    val itemList = remember(revision) { store.items() }
    val customers = remember(revision) { store.customers() }
    val cart = remember { mutableStateListOf<CartLine>() }
    var search by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Item?>(null) }
    var editing by remember { mutableStateOf<CartLine?>(null) }
    var checkingOut by remember { mutableStateOf(false) }

    val total = cart.sumOf { it.lineTotal }
    val shown = itemList.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }

    // A quantity of 0 or less removes the line from the bill.
    fun setQty(item: Item, qty: Double) {
        val index = cart.indexOfFirst { it.item.id == item.id }
        if (qty <= 0) {
            if (index >= 0) cart.removeAt(index)
        } else if (index >= 0) {
            cart[index] = CartLine(item, qty)
        } else {
            cart.add(CartLine(item, qty))
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("Sell", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search item") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            if (itemList.isEmpty()) {
                Text("No items yet. Add them in the Items tab.")
            }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { item ->
                    Card(
                        onClick = { picked = item },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "In stock ${item.stock.stockText(item.unit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(item.sellPrice.perUnit(item.unit))
                        }
                    }
                }
            }
        }

        Card(Modifier.width(380.dp).fillMaxHeight()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Bill", style = MaterialTheme.typography.titleLarge)
                if (cart.isEmpty()) {
                    Text("Click an item to add it.", modifier = Modifier.padding(top = 8.dp))
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(cart, key = { it.item.id }) { line ->
                        Row(
                            Modifier.fillMaxWidth().clickable { editing = line }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.item.name)
                                Text(
                                    "${line.qty.stockText(line.item.unit)} x ${line.item.sellPrice.perUnit(line.item.unit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(line.lineTotal.money())
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Total ${total.money()}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { checkingOut = true },
                    enabled = cart.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Charge")
                }
            }
        }
    }

    picked?.let { item ->
        val alreadyInBill = cart.find { it.item.id == item.id }?.qty ?: 0.0
        QuantityDialog(
            title = item.name,
            unit = item.unit,
            pricePerUnit = item.sellPrice,
            initial = if (item.unit == "kg" || item.unit == "litre") null else 1.0,
            confirmText = "Add",
            onConfirm = { qty ->
                setQty(item, alreadyInBill + qty)
                picked = null
            },
            onDismiss = { picked = null },
        )
    }

    editing?.let { line ->
        QuantityDialog(
            title = line.item.name,
            unit = line.item.unit,
            pricePerUnit = line.item.sellPrice,
            initial = line.qty,
            confirmText = "Update",
            onConfirm = { qty ->
                setQty(line.item, qty)
                editing = null
            },
            onRemove = {
                setQty(line.item, 0.0)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (checkingOut) {
        CheckoutDialog(
            total = total,
            customers = customers,
            onDismiss = { checkingOut = false },
            onConfirm = { mode, customerId ->
                val saleId = store.checkout(cart.toList(), mode, customerId)
                cart.clear()
                checkingOut = false
                onMessage("Bill #$saleId saved. Stock updated.")
            },
        )
    }
}
