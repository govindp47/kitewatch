package com.kitewatch.data.mapper

import com.kitewatch.database.entity.PersistentAlertEntity
import com.kitewatch.domain.model.AlertResolution
import com.kitewatch.domain.model.AlertSeverity
import com.kitewatch.domain.model.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlertMapperTest {
    private val createdMillis = 1_700_000_000_000L
    private val resolvedMillis = 1_700_100_000_000L

    private val unresolvedEntity =
        PersistentAlertEntity(
            id = 1L,
            alertType = "GTT_MANUAL_OVERRIDE",
            severity = "WARNING",
            payload = """{"holdingId":42,"stockCode":"INFY"}""",
            acknowledged = 0,
            createdAt = createdMillis,
            resolvedAt = null,
            resolvedBy = null,
        )

    private val resolvedEntity =
        unresolvedEntity.copy(
            acknowledged = 1,
            resolvedAt = resolvedMillis,
            resolvedBy = "USER_ACK",
        )

    @Test
    fun `entity toDomain maps unresolved alert correctly`() {
        val domain = unresolvedEntity.toDomain()
        assertEquals(1L, domain.alertId)
        assertEquals(AlertType.GTT_MANUAL_OVERRIDE, domain.alertType)
        assertEquals(AlertSeverity.WARNING, domain.severity)
        assertEquals("""{"holdingId":42,"stockCode":"INFY"}""", domain.payload)
        assertFalse(domain.acknowledged)
        assertEquals(Instant.ofEpochMilli(createdMillis), domain.createdAt)
        assertNull(domain.resolvedAt)
        assertNull(domain.resolvedBy)
    }

    @Test
    fun `entity toDomain maps resolved alert correctly`() {
        val domain = resolvedEntity.toDomain()
        assertTrue(domain.acknowledged)
        assertEquals(Instant.ofEpochMilli(resolvedMillis), domain.resolvedAt)
        assertEquals(AlertResolution.USER_ACK, domain.resolvedBy)
    }

    @Test
    fun `round-trip unresolved alert preserves all fields`() {
        val roundTripped = unresolvedEntity.toDomain().toEntity()
        assertEquals(unresolvedEntity.id, roundTripped.id)
        assertEquals(unresolvedEntity.alertType, roundTripped.alertType)
        assertEquals(unresolvedEntity.severity, roundTripped.severity)
        assertEquals(unresolvedEntity.payload, roundTripped.payload)
        assertEquals(unresolvedEntity.acknowledged, roundTripped.acknowledged)
        assertEquals(unresolvedEntity.createdAt, roundTripped.createdAt)
        assertNull(roundTripped.resolvedAt)
        assertNull(roundTripped.resolvedBy)
    }

    @Test
    fun `round-trip resolved alert preserves resolvedAt and resolvedBy`() {
        val roundTripped = resolvedEntity.toDomain().toEntity()
        assertEquals(resolvedMillis, roundTripped.resolvedAt)
        assertEquals("USER_ACK", roundTripped.resolvedBy)
        assertEquals(1, roundTripped.acknowledged)
    }

    @Test
    fun `all alert types parse without error`() {
        val types = AlertType.entries
        types.forEach { type ->
            val entity = unresolvedEntity.copy(alertType = type.name)
            assertEquals(type, entity.toDomain().alertType)
        }
    }

    @Test
    fun `all severity values parse without error`() {
        listOf("CRITICAL", "WARNING", "INFO").forEach { severity ->
            val entity = unresolvedEntity.copy(severity = severity)
            assertEquals(AlertSeverity.valueOf(severity), entity.toDomain().severity)
        }
    }

    @Test
    fun `all resolution values round-trip correctly`() {
        AlertResolution.entries.forEach { resolution ->
            val domain = unresolvedEntity.toDomain().copy(resolvedBy = resolution)
            assertEquals(resolution.name, domain.toEntity().resolvedBy)
        }
    }

    @Test
    fun `AUTO_RESOLVED resolution maps correctly`() {
        val entity = resolvedEntity.copy(resolvedBy = "AUTO_RESOLVED")
        assertEquals(AlertResolution.AUTO_RESOLVED, entity.toDomain().resolvedBy)
    }

    @Test
    fun `SUPERSEDED resolution maps correctly`() {
        val entity = resolvedEntity.copy(resolvedBy = "SUPERSEDED")
        assertEquals(AlertResolution.SUPERSEDED, entity.toDomain().resolvedBy)
    }
}
