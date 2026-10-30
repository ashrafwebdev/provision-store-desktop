package com.provisionstore.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.provisionstore.desktop.data.Store
import java.io.ByteArrayOutputStream
import java.io.File
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc

/** Characters per line on a 58 mm roll with the printer's default font. */
private const val RECEIPT_COLUMNS = 32

/** Saved receipt printer name and the raw ESC/POS job sender. */
object ReceiptPrinter {
    private val settingsFile = File(Store.defaultFile().parentFile, "receipt-printer.txt")

    fun savedName(): String? = runCatching { settingsFile.readText().trim().ifBlank { null } }.getOrNull()

    fun save(name: String) {
        runCatching { settingsFile.writeText(name) }
    }

    fun services(): List<PrintService> =
        PrintServiceLookup.lookupPrintServices(DocFlavor.BYTE_ARRAY.AUTOSENSE, null).toList()

    fun send(service: PrintService, lines: List<String>) {
        val doc = SimpleDoc(receiptBytes(lines), DocFlavor.BYTE_ARRAY.AUTOSENSE, null)
        service.createPrintJob().print(doc, null)
    }
}

/**
 * Turns text lines into ESC/POS bytes: wraps long lines to the roll width, keeps only
 * printable ASCII (the printer has no rupee sign), feeds paper and cuts if a cutter exists.
 */
fun receiptBytes(lines: List<String>): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0x1B, 0x40)) // initialise the printer
    lines.forEach { line ->
        val clean = line.replace("₹", "Rs ").map { if (it.code in 32..126) it else '?' }.joinToString("")
        val wrapped = if (clean.isEmpty()) listOf("") else clean.chunked(RECEIPT_COLUMNS)
        wrapped.forEach { part ->
            out.write(part.toByteArray(Charsets.US_ASCII))
            out.write(0x0A)
        }
    }
    out.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A)) // feed so the last line clears the cutter
    out.write(byteArrayOf(0x1D, 0x56, 0x00)) // full cut, ignored by printers without a cutter
    return out.toByteArray()
}

/**
 * "Print receipt" button for the thermal roll printer. Asks once for the printer, then remembers it.
 * "Printer" lets the user pick a different one.
 */
@Composable
fun ReceiptPrintButton(lines: () -> List<String>, onMessage: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var printer by remember { mutableStateOf(ReceiptPrinter.savedName()) }

    fun sendReceipt(name: String) {
        val service = ReceiptPrinter.services().firstOrNull { it.name == name }
        if (service == null) {
            onMessage("Printer \"$name\" is not connected. Choose another one.")
            choosing = true
            return
        }
        runCatching { ReceiptPrinter.send(service, lines()) }
            .onSuccess { onMessage("Receipt sent to $name.") }
            .onFailure { onMessage("Receipt failed: ${it.message}") }
    }

    OutlinedButton(onClick = {
        val saved = printer
        if (saved == null) choosing = true else sendReceipt(saved)
    }) {
        Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
        Text("Print receipt")
    }
    TextButton(onClick = { choosing = true }) { Text("Printer") }

    if (choosing) {
        PrinterChooser(
            onPick = { name ->
                ReceiptPrinter.save(name)
                printer = name
                choosing = false
                sendReceipt(name)
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun PrinterChooser(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val names = remember { ReceiptPrinter.services().map { it.name } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receipt printer") },
        text = {
            if (names.isEmpty()) {
                Text("No printers found. Connect the roll printer and install it in Windows, then try again.")
            } else {
                Column {
                    Text("Pick the roll printer. If Windows asks for a driver, Generic / Text Only works for most of them.")
                    names.forEach { name ->
                        TextButton(onClick = { onPick(name) }) { Text(name) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
