package com.kitewatch.network.drive.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Drive API v3 file resource — subset of fields returned by files.list and files.create.
 * [sizeStr] is a string in the Drive API response even though it represents a Long.
 */
@JsonClass(generateAdapter = true)
data class DriveFileDto(
    val id: String,
    val name: String? = null,
    @Json(name = "size") val sizeStr: String? = null,
    val createdTime: String? = null,
)

/** Response envelope for files.list. */
@JsonClass(generateAdapter = true)
data class DriveFileListDto(
    val files: List<DriveFileDto> = emptyList(),
)
