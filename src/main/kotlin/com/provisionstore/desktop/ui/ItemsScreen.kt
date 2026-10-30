package com.provisionstore.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
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
import com.provisionstore.app.util.perUnit
import com.provisionstore.app.util.quickAmounts
import com.provisionstore.app.util.stockText
import com.provisionstore.desktop.data.Item
import com.provisionstore.desktop.data.Store

/** Items and their stock in one list. Low stock shows first. */
@Composable
fun ItemsScreen(store: Store, onMessage: (String) -> Unit) {
    val revision = store.revision
    val itemList = remember(revision) { store.items() }
    val lowCount = itemList.count { it.stock <= it.reorderLevel }
    val sorted = itemList.sortedBy { it.stock > it.reorderLevel }

    var editing by remember { mutableStateOf<Item?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingStockTo by remember { mutableStateOf<Item?>(null) }
    var confirmDelete by remember { mutableStateOf<Item?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Items and stock", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Add item")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (lowCount > 0) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                if (lowCount == 0) "All items are above their reorder level" else "$lowCount item(s) low on stock",
                color = if (lowCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (itemList.isEmpty()) {
            Text("No items yet. Tap Add item to start.")
        }

        LazyColumn(Modifier.weight(1f)) {
            items(sorted, key = { it.id }) { item ->
                val low = item.stock <= item.reorderLevel
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text("Sell ${item.sellPrice.perUnit(item.unit)}  ·  Cost ${item.costPrice.perUnit(item.unit)}")
                            Text(
                                "In stock ${item.stock.stockText(item.unit)}",
                                color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        OutlinedButton(onClick = { addingStockTo = item }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Add stock")
                        }
                        Spacer(Modifier.height(0.dp))
                        OutlinedButton(onClick = { editing = item }, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        ItemDialog(
            item = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { item ->
                store.saveItem(item)
                onMessage("Saved ${item.name}.")
                adding = false
                editing = null
            },
            onDelete = { item -> confirmDelete = item },
        )
    }

    addingStockTo?.let { item ->
        QuantityDialog(
            title = "Add stock: ${item.name}",
            unit = item.unit,
            pricePerUnit = item.costPrice,
            priceLabel = "Cost",
            initial = if (quickAmounts(item.unit).isEmpty()) 1.0 else null,
            confirmText = "Add",
            onConfirm = { qty ->
                store.addStock(item.id, qty)
                onMessage("Added ${qty.stockText(item.unit)} of ${item.name}.")
                addingStockTo = null
            },
            onDismiss = { addingStockTo = null },
        )
    }

    confirmDelete?.let { item ->
        ConfirmDialog(
            title = "Delete ${item.name}?",
            text = "The item is removed from the list. Past bills keep their record.",
            confirmText = "Delete",
            onConfirm = {
                store.deleteItem(item.id)
                confirmDelete = null
                editing = null
                adding = false
            },
            onDismiss = { confirmDelete = null },
        )
    }
}
