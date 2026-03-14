package com.kitewatch.data.mapper

import com.kitewatch.database.entity.GttRecordEntity
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.model.Paisa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GttRecordMapperTest {
    private val baseEntity =
        GttRecordEntity(
            id = 1L,
            zerodhaGttId = 12345L,
            stockCode = "INFY",
            triggerPricePaisa = 175000L,
            quantity = 10,
            status = "ACTIVE",
            isAppManaged = 1,
            appCalculatedPrice = 175000L,
            manualOverrideDetected = 0,
            holdingId = 42L,
            lastSyncedAt = 1_700_000_000_000L,
            isArchived = 0,
            createdAt = 1_699_000_000_000L,
            updatedAt = 1_700_000_000_000L,
        )

    @Test
    fun `entity toDomain maps basic fields correctly`() {
        val domain = baseEntity.toDomain()
        assertEquals(1L, domain.gttId)
        assertEquals("12345", domain.zerodhaGttId)
        assertEquals("INFY", domain.stockCode)
        assertEquals(Paisa(175000L), domain.triggerPrice)
        assertEquals(10, domain.quantity)
        assertEquals(GttStatus.ACTIVE, domain.status)
        assertTrue(domain.isAppManaged)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), domain.lastSyncedAt)
    }

    @Test
    fun `null zerodhaGttId preserved`() {
        val domain = baseEntity.copy(zerodhaGttId = null).toDomain()
        assertNull(domain.zerodhaGttId)
    }

    @Test
    fun `null lastSyncedAt preserved`() {
        val domain = baseEntity.copy(lastSyncedAt = null).toDomain()
        assertNull(domain.lastSyncedAt)
    }

    @Test
    fun `isArchived=1 maps to ARCHIVED status regardless of status field`() {
        val domain = baseEntity.copy(isArchived = 1, status = "ACTIVE").toDomain()
        assertEquals(GttStatus.ARCHIVED, domain.status)
    }

    @Test
    fun `manualOverrideDetected=1 maps to MANUAL_OVERRIDE_DETECTED`() {
        val domain = baseEntity.copy(manualOverrideDetected = 1).toDomain()
        assertEquals(GttStatus.MANUAL_OVERRIDE_DETECTED, domain.status)
    }

    @Test
    fun `PENDING_CREATION status maps correctly`() {
        val domain = baseEntity.copy(status = "PENDING_CREATION").toDomain()
        assertEquals(GttStatus.PENDING_CREATION, domain.status)
    }

    @Test
    fun `TRIGGERED status maps correctly`() {
        val domain = baseEntity.copy(status = "TRIGGERED").toDomain()
        assertEquals(GttStatus.TRIGGERED, domain.status)
    }

    @Test
    fun `CANCELLED entity status maps to ARCHIVED domain status`() {
        val domain = baseEntity.copy(status = "CANCELLED").toDomain()
        assertEquals(GttStatus.ARCHIVED, domain.status)
    }

    @Test
    fun `isAppManaged=0 maps to false`() {
        val domain = baseEntity.copy(isAppManaged = 0).toDomain()
        assertFalse(domain.isAppManaged)
    }

    @Test
    fun `MANUAL_OVERRIDE_DETECTED domain sets manualOverrideDetected=1 in entity`() {
        val domain = baseEntity.toDomain().copy(status = GttStatus.MANUAL_OVERRIDE_DETECTED)
        val entity = domain.toEntity()
        assertEquals(1, entity.manualOverrideDetected)
        assertEquals(0, entity.isArchived)
    }

    @Test
    fun `ARCHIVED domain sets isArchived=1 in entity`() {
        val domain = baseEntity.toDomain().copy(status = GttStatus.ARCHIVED)
        val entity = domain.toEntity()
        assertEquals(1, entity.isArchived)
        assertEquals(0, entity.manualOverrideDetected)
    }

    @Test
    fun `round-trip ACTIVE preserves core fields`() {
        val roundTripped = baseEntity.toDomain().toEntity()
        assertEquals(baseEntity.id, roundTripped.id)
        assertEquals(baseEntity.zerodhaGttId, roundTripped.zerodhaGttId)
        assertEquals(baseEntity.stockCode, roundTripped.stockCode)
        assertEquals(baseEntity.triggerPricePaisa, roundTripped.triggerPricePaisa)
        assertEquals(baseEntity.quantity, roundTripped.quantity)
        assertEquals(baseEntity.isAppManaged, roundTripped.isAppManaged)
        assertEquals(baseEntity.lastSyncedAt, roundTripped.lastSyncedAt)
    }
}
