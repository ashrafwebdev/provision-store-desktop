package com.provisionstore.desktop.data

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreTest {

    private lateinit var dir: File
    private lateinit var store: Store

    @BeforeTest
    fun setUp() {
        dir = kotlin.io.path.createTempDirectory("store-test").toFile()
        store = Store(File(dir, "provision.db"))
    }

    @AfterTest
    fun tearDown() {
        store.close()
        dir.deleteRecursively()
    }

    private fun addRice(stock: Double = 10.0): Item {
        store.saveItem(
            Item(0, "Rice", "kg", sellPrice = 6000, costPrice = 4500, stock = stock, reorderLevel = 2.0)
        )
        return store.items().first { it.name == "Rice" }
    }

    @Test
    fun billReducesStockAndSavesLines() {
        val rice = addRice()

        val saleId = store.checkout(listOf(CartLine(rice, 0.25)), PaymentMode.CASH, null)

        assertEquals(9.75, store.items().first().stock, 1e-9)
        val lines = store.saleLines(0, Long.MAX_VALUE)
        assertEquals(1, lines.size)
        assertEquals(1500L, lines.first().lineTotal)
        assertTrue(store.sales(0, Long.MAX_VALUE).any { it.id == saleId })
    }

    @Test
    fun creditBillAddsToKhataAndPaymentReducesIt() {
        val rice = addRice()
        store.addCustomer("Ravi", "9999999999")
        val ravi = store.customers().first()

        store.checkout(listOf(CartLine(rice, 1.0)), PaymentMode.CREDIT, ravi.id)
        assertEquals(6000L, store.customers().first().balance)

        store.recordPayment(ravi.id, 2000, System.currentTimeMillis())
        assertEquals(4000L, store.customers().first().balance)
    }

    @Test
    fun deletingCreditDaySaleRemovesKhataEntry() {
        store.addCustomer("Asha", "")
        val asha = store.customers().first()
        val now = System.currentTimeMillis()

        store.quickSale(7500, PaymentMode.CREDIT, asha.id, now)
        assertEquals(7500L, store.customers().first().balance)

        val saleId = store.sales(0, Long.MAX_VALUE).first().id
        store.deleteQuickSale(saleId)

        assertEquals(0L, store.customers().first().balance)
        assertTrue(store.sales(0, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun purchasesAndExpensesAreListedForTheDay() {
        val now = System.currentTimeMillis()
        store.addPurchase(50000, "Wholesaler", "", now)
        store.addExpense("Rent", 800000, "", now)

        assertEquals(50000L, store.purchases(0, Long.MAX_VALUE).first().amount)
        assertEquals("Rent", store.expenses(0, Long.MAX_VALUE).first().category)
    }

    @Test
    fun writesBumpRevision() {
        val before = store.revision
        store.addCustomer("Meena", "")
        assertTrue(store.revision > before)
    }
}
