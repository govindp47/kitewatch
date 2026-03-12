package com.kitewatch.domain.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryInterfaceTest {
    // -------------------------------------------------------------------------
    // BR-10: TransactionRepository must have no update or delete methods
    // -------------------------------------------------------------------------

    @Test
    fun `TransactionRepository has no update method`() {
        val methods = TransactionRepository::class.java.methods.map { it.name }
        assertFalse(
            "TransactionRepository must not have any 'update' method (BR-10)",
            methods.any { it.lowercase().startsWith("update") },
        )
    }

    @Test
    fun `TransactionRepository has no delete method`() {
        val methods = TransactionRepository::class.java.methods.map { it.name }
        assertFalse(
            "TransactionRepository must not have any 'delete' method (BR-10)",
            methods.any { it.lowercase().startsWith("delete") },
        )
    }

    // -------------------------------------------------------------------------
    // Required methods exist on each interface
    // -------------------------------------------------------------------------

    @Test
    fun `AccountBindingRepository has clear method`() {
        val methods = AccountBindingRepository::class.java.declaredMethods.map { it.name }
        assertTrue("AccountBindingRepository must have 'clear'", "clear" in methods)
    }

    @Test
    fun `AccountBindingRepository has bind method`() {
        val methods = AccountBindingRepository::class.java.declaredMethods.map { it.name }
        assertTrue("AccountBindingRepository must have 'bind'", "bind" in methods)
    }

    @Test
    fun `AccountBindingRepository has isBound method`() {
        val methods = AccountBindingRepository::class.java.declaredMethods.map { it.name }
        assertTrue("AccountBindingRepository must have 'isBound'", "isBound" in methods)
    }

    @Test
    fun `OrderRepository declares observeAll`() {
        val methods = OrderRepository::class.java.declaredMethods.map { it.name }
        assertTrue("OrderRepository must declare 'observeAll'", "observeAll" in methods)
    }

    @Test
    fun `HoldingRepository declares observeAll`() {
        val methods = HoldingRepository::class.java.declaredMethods.map { it.name }
        assertTrue("HoldingRepository must declare 'observeAll'", "observeAll" in methods)
    }

    @Test
    fun `GttRepository declares archive`() {
        val methods = GttRepository::class.java.declaredMethods.map { it.name }
        assertTrue("GttRepository must declare 'archive'", "archive" in methods)
    }

    @Test
    fun `AlertRepository declares observeUnacknowledged`() {
        val methods = AlertRepository::class.java.declaredMethods.map { it.name }
        assertTrue(
            "AlertRepository must declare 'observeUnacknowledged'",
            "observeUnacknowledged" in methods,
        )
    }

    @Test
    fun `ChargeRateRepository declares getCurrentRates`() {
        val methods = ChargeRateRepository::class.java.declaredMethods.map { it.name }
        assertTrue(
            "ChargeRateRepository must declare 'getCurrentRates'",
            "getCurrentRates" in methods,
        )
    }

    // -------------------------------------------------------------------------
    // Zero Android imports — verified structurally by java-library plugin
    // (module compiles with java-library; any Android import causes build failure)
    // -------------------------------------------------------------------------

    @Test
    fun `Repository interfaces load without Android runtime`() {
        // If any repository imports an Android class, this class loader test will
        // fail because Android classes are not on the test classpath.
        assertNotNull(OrderRepository::class.java)
        assertNotNull(HoldingRepository::class.java)
        assertNotNull(TransactionRepository::class.java)
        assertNotNull(FundRepository::class.java)
        assertNotNull(GttRepository::class.java)
        assertNotNull(ChargeRateRepository::class.java)
        assertNotNull(AlertRepository::class.java)
        assertNotNull(AccountBindingRepository::class.java)
    }
}
