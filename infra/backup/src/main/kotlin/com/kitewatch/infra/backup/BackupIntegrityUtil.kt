package com.kitewatch.infra.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Pure utility functions for backup file integrity and compression.
 * No I/O or state — every function is a pure transformation.
 */
object BackupIntegrityUtil {
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /**
     * Constant-time comparison via [MessageDigest.isEqual] — prevents timing attacks.
     * Do NOT replace with == or contentEquals.
     */
    fun verifyChecksum(
        payload: ByteArray,
        expected: ByteArray,
    ): Boolean = MessageDigest.isEqual(sha256(payload), expected)

    fun gzipCompress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }

    fun gzipDecompress(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
