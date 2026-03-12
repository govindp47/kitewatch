package com.kitewatch.database.converter

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Room TypeConverters for java.time types used across entity columns.
 *
 * LocalDate  ↔  TEXT  (ISO-8601: "YYYY-MM-DD")
 * Instant    ↔  INTEGER  (epoch milliseconds UTC)
 *
 * Annotated @ProvidedTypeConverter — the instance is supplied via
 * AppDatabase.Builder.addTypeConverter() so it can be swapped in tests.
 *
 * Design note: all _at columns in entities already store Long (epoch millis)
 * directly. These converters are available for any column that requires the
 * java.time type surface at the Kotlin/DAO level instead of raw primitives.
 */
@ProvidedTypeConverter
class RoomTypeConverters {
    // -------------------------------------------------------------------------
    // LocalDate ↔ String
    // -------------------------------------------------------------------------

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.format(DateTimeFormatter.ISO_LOCAL_DATE)

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }

    // -------------------------------------------------------------------------
    // Instant ↔ Long (epoch millis)
    // -------------------------------------------------------------------------

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}
