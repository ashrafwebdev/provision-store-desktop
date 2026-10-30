package com.provisionstore.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import kotlin.math.roundToLong

// Money is stored in paise and quantities in kg, litre or pc, exactly as on the phone.
// The tables match the Android app, so a phone backup file opens here as well.

enum class PaymentMode { CASH, UPI, CREDIT }

data class Item(
    val id: Long,
    val name: String,
    val unit: String,
    val sellPrice: Long,
    val costPrice: Long,
    val stock: Double,
    val reorderLevel: Double,
)

data class Customer(val id: Long, val name: String, val phone: String, val balance: Long)

data class SaleRow(
    val id: Long,
    val createdAt: Long,
    val total: Long,
    val paymentMode: String,
    val customerId: Long?,
)

data class SaleLineRow(val saleId: Long, val itemName: String, val qty: Double, val lineTotal: Long)

data class LedgerRow(val id: Long, val createdAt: Long, val amount: Long, val note: String)

data class PurchaseRow(val id: Long, val createdAt: Long, val amount: Long, val supplier: String, val note: String)

data class ExpenseRow(val id: Long, val createdAt: Long, val category: String, val amount: Long, val note: String)

data class CartLine(val item: Item, val qty: Double) {
    val lineTotal: Long get() = (item.sellPrice * qty).roundToLong()
}

/**
 * The shop's database, stored in a local SQLite file.
 * Every write bumps [revision], so screens that read [revision] redraw.
 */
class Store(val file: File) : AutoCloseable {

    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")

    var revision by mutableIntStateOf(0)
        private set

    init {
        conn.createStatement().use { st -> SCHEMA.forEach { st.execute(it) } }
    }

    override fun close() {
        conn.close()
    }

    // ---- Items and stock ----

    fun items(): List<Item> = query(
        "SELECT id, name, unit, sellPrice, costPrice, stock, reorderLevel FROM items ORDER BY name"
    ) { rs ->
        Item(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5), rs.getDouble(6), rs.getDouble(7))
    }

    fun saveItem(item: Item) = write {
        if (item.id == 0L) {
            exec(
                "INSERT INTO items (name, unit, sellPrice, costPrice, stock, reorderLevel) VALUES (?, ?, ?, ?, ?, ?)",
                item.name, item.unit, item.sellPrice, item.costPrice, item.stock, item.reorderLevel,
            )
        } else {
            exec(
                "UPDATE items SET name = ?, unit = ?, sellPrice = ?, costPrice = ?, stock = ?, reorderLevel = ? WHERE id = ?",
                item.name, item.unit, item.sellPrice, item.costPrice, item.stock, item.reorderLevel, item.id,
            )
        }
    }

    fun deleteItem(id: Long) = write { exec("DELETE FROM items WHERE id = ?", id) }

    fun addStock(id: Long, qty: Double) = write { exec("UPDATE items SET stock = stock + ? WHERE id = ?", qty, id) }

    // ---- Customers and khata ----

    fun customers(): List<Customer> = query(
        "SELECT c.id, c.name, c.phone, COALESCE(SUM(l.amount), 0) FROM customers c " +
            "LEFT JOIN ledger l ON l.customerId = c.id GROUP BY c.id ORDER BY c.name"
    ) { rs -> Customer(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)) }

    fun addCustomer(name: String, phone: String) = write {
        exec("INSERT INTO customers (name, phone) VALUES (?, ?)", name, phone)
    }

    fun ledgerFor(customerId: Long): List<LedgerRow> = query(
        "SELECT id, createdAt, amount, note FROM ledger WHERE customerId = ? ORDER BY createdAt DESC",
        customerId,
    ) { rs -> LedgerRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4)) }

    fun recordPayment(customerId: Long, amount: Long, at: Long) = write {
        exec(
            "INSERT INTO ledger (customerId, createdAt, amount, note) VALUES (?, ?, ?, ?)",
            customerId, at, -amount, "Payment received",
        )
    }

    // ---- Sales ----

    /** Saves a bill from the Sell screen, reduces stock, and adds credit to the khata. */
    fun checkout(cart: List<CartLine>, mode: PaymentMode, customerId: Long?): Long = write {
        val now = System.currentTimeMillis()
        val total = cart.sumOf { it.lineTotal }
        val saleId = exec(
            "INSERT INTO sales (createdAt, total, paymentMode, customerId) VALUES (?, ?, ?, ?)",
            now, total, mode.name, customerId,
        )
        cart.forEach { line ->
            exec(
                "INSERT INTO sale_lines (saleId, itemId, itemName, qty, unitPrice, lineTotal) VALUES (?, ?, ?, ?, ?, ?)",
                saleId, line.item.id, line.item.name, line.qty, line.item.sellPrice, line.lineTotal,
            )
            exec("UPDATE items SET stock = stock - ? WHERE id = ?", line.qty, line.item.id)
        }
        if (mode == PaymentMode.CREDIT && customerId != null) {
            exec(
                "INSERT INTO ledger (customerId, createdAt, amount, note) VALUES (?, ?, ?, ?)",
                customerId, now, total, "Bill #$saleId",
            )
        }
        saleId
    }

    /** A day-book sale with only a total. Credit sales also go into the khata. */
    fun quickSale(amount: Long, mode: PaymentMode, customerId: Long?, at: Long) = write {
        val saleId = exec(
            "INSERT INTO sales (createdAt, total, paymentMode, customerId) VALUES (?, ?, ?, ?)",
            at, amount, mode.name, customerId,
        )
        if (mode == PaymentMode.CREDIT && customerId != null) {
            exec(
                "INSERT INTO ledger (customerId, createdAt, amount, note) VALUES (?, ?, ?, ?)",
                customerId, at, amount, "Credit sale #$saleId",
            )
        }
    }

    /** Removes a day-book sale and, for credit sales, its khata entry. */
    fun deleteQuickSale(saleId: Long) = write {
        exec("DELETE FROM ledger WHERE note = ?", "Credit sale #$saleId")
        exec("DELETE FROM sales WHERE id = ?", saleId)
    }

    fun sales(from: Long, to: Long): List<SaleRow> = query(
        "SELECT id, createdAt, total, paymentMode, customerId FROM sales " +
            "WHERE createdAt BETWEEN ? AND ? ORDER BY createdAt DESC",
        from, to,
    ) { rs ->
        val id = rs.getLong(1)
        val createdAt = rs.getLong(2)
        val total = rs.getLong(3)
        val mode = rs.getString(4)
        val customer = rs.getLong(5)
        SaleRow(id, createdAt, total, mode, if (rs.wasNull()) null else customer)
    }

    fun saleLines(from: Long, to: Long): List<SaleLineRow> = query(
        "SELECT sl.saleId, sl.itemName, sl.qty, sl.lineTotal FROM sale_lines sl " +
            "JOIN sales s ON s.id = sl.saleId WHERE s.createdAt BETWEEN ? AND ?",
        from, to,
    ) { rs -> SaleLineRow(rs.getLong(1), rs.getString(2), rs.getDouble(3), rs.getLong(4)) }

    fun payments(from: Long, to: Long): List<LedgerRow> = query(
        "SELECT id, createdAt, amount, note FROM ledger WHERE amount < 0 AND createdAt BETWEEN ? AND ?",
        from, to,
    ) { rs -> LedgerRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4)) }

    // ---- Purchases and expenses ----

    fun addPurchase(amount: Long, supplier: String, note: String, at: Long) = write {
        exec(
            "INSERT INTO purchases (createdAt, amount, supplier, note) VALUES (?, ?, ?, ?)",
            at, amount, supplier, note,
        )
    }

    fun deletePurchase(id: Long) = write { exec("DELETE FROM purchases WHERE id = ?", id) }

    fun purchases(from: Long, to: Long): List<PurchaseRow> = query(
        "SELECT id, createdAt, amount, supplier, note FROM purchases WHERE createdAt BETWEEN ? AND ? ORDER BY createdAt DESC",
        from, to,
    ) { rs -> PurchaseRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5)) }

    fun addExpense(category: String, amount: Long, note: String, at: Long) = write {
        exec(
            "INSERT INTO expenses (createdAt, category, amount, note) VALUES (?, ?, ?, ?)",
            at, category, amount, note,
        )
    }

    fun deleteExpense(id: Long) = write { exec("DELETE FROM expenses WHERE id = ?", id) }

    fun expenses(from: Long, to: Long): List<ExpenseRow> = query(
        "SELECT id, createdAt, category, amount, note FROM expenses WHERE createdAt BETWEEN ? AND ? ORDER BY createdAt DESC",
        from, to,
    ) { rs -> ExpenseRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5)) }

    // ---- Backup ----

    /** Copies the database file. Safe to call while the app is open: no transaction is pending. */
    fun backupTo(target: File) {
        file.copyTo(target, overwrite = true)
    }

    /** Keeps one copy per day in [folder] and removes the oldest beyond [keep]. */
    fun dailyBackup(folder: File, keep: Int = 14) {
        folder.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val target = File(folder, "provision-$stamp.db")
        if (!target.exists()) file.copyTo(target)
        folder.listFiles { f -> f.name.startsWith("provision-") && f.name.endsWith(".db") }
            ?.sortedByDescending { it.name }
            ?.drop(keep)
            ?.forEach { it.delete() }
    }

    // ---- Helpers ----

    private fun <T> write(block: () -> T): T {
        conn.autoCommit = false
        try {
            val result = block()
            conn.commit()
            revision++
            return result
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(map(rs)) }
            }
        }

    /** Runs an insert, update or delete and returns the new row id (0 when there is none). */
    private fun exec(sql: String, vararg args: Any?): Long =
        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            bind(ps, args)
            ps.executeUpdate()
            ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 0L }
        }

    private fun bind(ps: PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, value ->
            if (value == null) ps.setNull(i + 1, Types.NULL) else ps.setObject(i + 1, value)
        }
    }

    companion object {
        /** The same tables the Android app creates. */
        private val SCHEMA = listOf(
            """CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, unit TEXT NOT NULL, sellPrice INTEGER NOT NULL, costPrice INTEGER NOT NULL,
                stock REAL NOT NULL, reorderLevel REAL NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, phone TEXT NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS sales (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                createdAt INTEGER NOT NULL, total INTEGER NOT NULL, paymentMode TEXT NOT NULL, customerId INTEGER)""",
            """CREATE TABLE IF NOT EXISTS sale_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                saleId INTEGER NOT NULL, itemId INTEGER NOT NULL, itemName TEXT NOT NULL, qty REAL NOT NULL,
                unitPrice INTEGER NOT NULL, lineTotal INTEGER NOT NULL)""",
            "CREATE INDEX IF NOT EXISTS index_sale_lines_saleId ON sale_lines(saleId)",
            """CREATE TABLE IF NOT EXISTS ledger (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                customerId INTEGER NOT NULL, createdAt INTEGER NOT NULL, amount INTEGER NOT NULL, note TEXT NOT NULL)""",
            "CREATE INDEX IF NOT EXISTS index_ledger_customerId ON ledger(customerId)",
            """CREATE TABLE IF NOT EXISTS purchases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                createdAt INTEGER NOT NULL, amount INTEGER NOT NULL, supplier TEXT NOT NULL, note TEXT NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                createdAt INTEGER NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL, note TEXT NOT NULL)""",
        )

        /** Where the shop's data lives on Windows. It survives installing new versions. */
        fun defaultFile(): File {
            val base = System.getenv("APPDATA") ?: System.getProperty("user.home")
            return File(base, "ProvisionStore/provision.db").also { it.parentFile.mkdirs() }
        }
    }
}
