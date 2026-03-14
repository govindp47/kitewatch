package com.kitewatch.network.drive

import android.content.SharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete data source for Google Drive backup file operations.
 *
 * All files are stored in the **appDataFolder** space — they are invisible
 * in the user's Drive file list and are deleted when the app is uninstalled.
 *
 * OAuth token is read at call time from [encryptedPrefs] using [KEY_GOOGLE_OAUTH_TOKEN],
 * so token refreshes are picked up without restarting the data source.
 *
 * Exceptions from the API client propagate to the caller; the backup use case
 * is responsible for Drive-failure → local-fallback logic.
 */
@Singleton
class GoogleDriveRemoteDataSource
    @Inject
    constructor(
        private val apiClient: GoogleDriveApiClient,
        private val encryptedPrefs: SharedPreferences,
    ) {
        /** Immutable view of the current bearer token header value. */
        private val bearerToken: String
            get() = "Bearer ${encryptedPrefs.getString(KEY_GOOGLE_OAUTH_TOKEN, "") ?: ""}"

        /**
         * Uploads [fileBytes] to Drive's appDataFolder with the given [fileName].
         * @return the Drive file ID assigned to the uploaded file.
         */
        suspend fun uploadBackup(
            fileName: String,
            fileBytes: ByteArray,
        ): String {
            val body = buildUploadBody(fileName, fileBytes)
            val response = apiClient.uploadFile(auth = bearerToken, body = body)
            return response.id
        }

        /**
         * Lists all `.kwbackup` files in appDataFolder, ordered newest first.
         */
        suspend fun listBackups(): List<DriveBackupEntry> {
            val response = apiClient.listFiles(auth = bearerToken)
            return response.files.map { file ->
                DriveBackupEntry(
                    fileId = file.id,
                    fileName = file.name ?: "",
                    fileSizeBytes = file.sizeStr?.toLongOrNull() ?: 0L,
                    createdAt = file.createdTime ?: "",
                )
            }
        }

        /**
         * Downloads the file identified by [fileId] and returns its complete bytes.
         */
        suspend fun downloadBackup(fileId: String): ByteArray {
            val responseBody = apiClient.downloadFile(auth = bearerToken, fileId = fileId)
            return responseBody.bytes()
        }

        /**
         * Permanently deletes the file identified by [fileId] from Drive.
         * Throws on HTTP error; Drive returns 204 on success.
         */
        suspend fun deleteBackup(fileId: String) {
            val response = apiClient.deleteFile(auth = bearerToken, fileId = fileId)
            if (!response.isSuccessful) {
                throw DriveApiException(
                    "deleteBackup failed: HTTP ${response.code()} for fileId=$fileId",
                )
            }
        }

        // ---------------------------------------------------------------------------
        // Internal helpers
        // ---------------------------------------------------------------------------

        /**
         * Builds a `multipart/related` body containing JSON metadata (Part 1) and
         * raw binary content (Part 2), as required by the Drive v3 multipart upload spec.
         *
         * OkHttp prohibits `Content-Type` in the per-part [Headers]; the content type
         * must be set on the [RequestBody] itself via [toRequestBody].
         */
        private fun buildUploadBody(
            fileName: String,
            fileBytes: ByteArray,
        ): MultipartBody {
            val metadata = """{"name":"$fileName","parents":["appDataFolder"]}"""
            return MultipartBody
                .Builder()
                .setType(MULTIPART_RELATED)
                .addPart(metadata.toRequestBody(JSON_MEDIA_TYPE))
                .addPart(fileBytes.toRequestBody(OCTET_STREAM_MEDIA_TYPE))
                .build()
        }

        companion object {
            /** Key for the Google OAuth token in EncryptedSharedPreferences. */
            const val KEY_GOOGLE_OAUTH_TOKEN = "google_oauth_token"

            private val MULTIPART_RELATED = "multipart/related".toMediaType()
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()
            private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
        }
    }

/**
 * Metadata for a single `.kwbackup` file stored in Google Drive.
 *
 * @param fileId       Drive file ID; used for download and delete operations.
 * @param fileName     Original file name (e.g. `kitewatch_ZD1234_20260315_100000.kwbackup`).
 * @param fileSizeBytes File size in bytes as reported by Drive.
 * @param createdAt    ISO-8601 creation timestamp.
 */
data class DriveBackupEntry(
    val fileId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val createdAt: String,
)

/** Thrown when a Drive API call returns an unexpected HTTP status code. */
class DriveApiException(
    message: String,
) : Exception(message)
