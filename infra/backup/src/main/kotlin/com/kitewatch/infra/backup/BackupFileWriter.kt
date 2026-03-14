package com.kitewatch.infra.backup

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

/**
 * Builds and parses the binary .kwbackup file format.
 *
 * Header layout (148 bytes total, big-endian):
 *  [0..3]   Magic bytes "KWBK"          4 bytes
 *  [4..7]   Format version (uint32)     4 bytes
 *  [8..11]  Schema version (uint32)     4 bytes
 *  [12..75] Created-at ISO-8601        64 bytes (space-padded)
 *  [76..107] Account ID               32 bytes (space-padded)
 *  [108..115] Payload size (uint64)    8 bytes
 *  [116..147] SHA-256 checksum        32 bytes
 *  [148..]  GZIP-compressed Protobuf payload
 */
object BackupFileWriter {
    const val MAGIC = "KWBK"
    const val FORMAT_VERSION = 1

    private const val MAGIC_SIZE = 4
    private const val FORMAT_VERSION_SIZE = 4
    private const val SCHEMA_VERSION_SIZE = 4
    private const val CREATED_AT_SIZE = 64
    private const val ACCOUNT_ID_SIZE = 32
    private const val PAYLOAD_SIZE_FIELD = 8
    private const val CHECKSUM_SIZE = 32

    const val HEADER_SIZE =
        MAGIC_SIZE + FORMAT_VERSION_SIZE + SCHEMA_VERSION_SIZE +
            CREATED_AT_SIZE + ACCOUNT_ID_SIZE + PAYLOAD_SIZE_FIELD + CHECKSUM_SIZE // 148

    /**
     * Builds the complete .kwbackup file bytes from a compressed payload.
     * The SHA-256 checksum is computed here over the compressed payload.
     */
    fun buildFile(
        schemaVersion: Int,
        accountId: String,
        compressedPayload: ByteArray,
    ): ByteArray {
        val checksum = BackupIntegrityUtil.sha256(compressedPayload)
        val createdAt = Instant.now().toString()

        val header =
            ByteBuffer
                .allocate(HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .apply {
                    put(MAGIC.toByteArray(Charsets.US_ASCII))
                    putInt(FORMAT_VERSION)
                    putInt(schemaVersion)
                    put(padOrTruncate(createdAt, CREATED_AT_SIZE))
                    put(padOrTruncate(accountId, ACCOUNT_ID_SIZE))
                    putLong(compressedPayload.size.toLong())
                    put(checksum)
                }.array()

        return header + compressedPayload
    }

    /**
     * Parses the fixed-size header from a .kwbackup file.
     * Returns null if the file is too short to contain a valid header.
     */
    fun parseHeader(fileBytes: ByteArray): ParsedHeader? {
        if (fileBytes.size < HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(fileBytes, 0, HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)

        val magic = ByteArray(MAGIC_SIZE).also { buf.get(it) }
        val formatVersion = buf.int
        val schemaVersion = buf.int
        val createdAt =
            ByteArray(CREATED_AT_SIZE)
                .also { buf.get(it) }
                .toString(Charsets.US_ASCII)
                .trimEnd(' ')
        val accountId =
            ByteArray(ACCOUNT_ID_SIZE)
                .also { buf.get(it) }
                .toString(Charsets.US_ASCII)
                .trimEnd(' ')
        val payloadSize = buf.long
        val checksum = ByteArray(CHECKSUM_SIZE).also { buf.get(it) }

        return ParsedHeader(
            magic = magic,
            formatVersion = formatVersion,
            schemaVersion = schemaVersion,
            createdAt = createdAt,
            accountId = accountId,
            payloadSize = payloadSize,
            checksum = checksum,
        )
    }

    /** Extracts the compressed payload from a .kwbackup file (everything after the header). */
    fun extractPayload(fileBytes: ByteArray): ByteArray = fileBytes.copyOfRange(HEADER_SIZE, fileBytes.size)

    // Space-pad if shorter; truncate if longer (ASCII encoding).
    private fun padOrTruncate(
        value: String,
        size: Int,
    ): ByteArray {
        val bytes = value.toByteArray(Charsets.US_ASCII)
        return if (bytes.size >= size) {
            bytes.copyOf(size)
        } else {
            bytes + ByteArray(size - bytes.size) { ' '.code.toByte() }
        }
    }

    data class ParsedHeader(
        val magic: ByteArray,
        val formatVersion: Int,
        val schemaVersion: Int,
        val createdAt: String,
        val accountId: String,
        val payloadSize: Long,
        val checksum: ByteArray,
    ) {
        fun isMagicValid(): Boolean = magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ParsedHeader) return false
            return magic.contentEquals(other.magic) &&
                formatVersion == other.formatVersion &&
                schemaVersion == other.schemaVersion &&
                createdAt == other.createdAt &&
                accountId == other.accountId &&
                payloadSize == other.payloadSize &&
                checksum.contentEquals(other.checksum)
        }

        override fun hashCode(): Int {
            var result = magic.contentHashCode()
            result = 31 * result + formatVersion
            result = 31 * result + schemaVersion
            result = 31 * result + createdAt.hashCode()
            result = 31 * result + accountId.hashCode()
            result = 31 * result + payloadSize.hashCode()
            result = 31 * result + checksum.contentHashCode()
            return result
        }
    }
}
