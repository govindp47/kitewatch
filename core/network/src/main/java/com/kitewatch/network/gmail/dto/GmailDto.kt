package com.kitewatch.network.gmail.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Response from `messages.list` — a page of message stubs. */
@JsonClass(generateAdapter = true)
data class GmailMessageListDto(
    val messages: List<GmailMessageStubDto>? = null,
    val nextPageToken: String? = null,
    val resultSizeEstimate: Int = 0,
)

/** Minimal stub returned by `messages.list` — only id and threadId. */
@JsonClass(generateAdapter = true)
data class GmailMessageStubDto(
    val id: String,
    val threadId: String? = null,
)

/** Full message resource returned by `messages.get?format=full`. */
@JsonClass(generateAdapter = true)
data class GmailMessageDto(
    val id: String,
    val threadId: String? = null,
    val payload: GmailPayloadDto? = null,
    @Json(name = "internalDate") val internalDateMs: String? = null,
)

/** The MIME payload of a message, potentially with nested parts. */
@JsonClass(generateAdapter = true)
data class GmailPayloadDto(
    val mimeType: String? = null,
    val headers: List<GmailHeaderDto>? = null,
    val body: GmailBodyDto? = null,
    val parts: List<GmailPayloadDto>? = null,
)

/** A single RFC-2822 header name/value pair. */
@JsonClass(generateAdapter = true)
data class GmailHeaderDto(
    val name: String,
    val value: String,
)

/** Encoded body of a message part. [data] is base64url-encoded. */
@JsonClass(generateAdapter = true)
data class GmailBodyDto(
    val data: String? = null,
    val size: Int = 0,
)
