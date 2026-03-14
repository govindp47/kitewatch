package com.kitewatch.network.drive

import com.kitewatch.network.drive.dto.DriveFileDto
import com.kitewatch.network.drive.dto.DriveFileListDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Google Drive REST API v3.
 *
 * Base URL: https://www.googleapis.com/
 *
 * All operations target the **appDataFolder** space — backup files are stored
 * in the app's hidden application data folder, invisible in the user's Drive UI.
 *
 * The `Authorization` header carries `Bearer {google_oauth_token}` and must be
 * supplied by the caller on every request (no interceptor — Drive auth is
 * independent of the Kite Connect auth chain).
 */
interface GoogleDriveApiClient {
    /**
     * Uploads a file using multipart upload (metadata + binary payload in one request).
     * The [body] must be a `multipart/related` body with:
     *  - Part 1: `application/json` metadata `{"name":"...","parents":["appDataFolder"]}`
     *  - Part 2: `application/octet-stream` raw file bytes
     */
    @POST("upload/drive/v3/files?uploadType=multipart")
    suspend fun uploadFile(
        @Header("Authorization") auth: String,
        @retrofit2.http.Body body: MultipartBody,
    ): DriveFileDto

    /**
     * Lists files in the given space.
     * Call with [spaces] = "appDataFolder" and [fields] = "files(id,name,size,createdTime)".
     */
    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") auth: String,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("fields") fields: String = "files(id,name,size,createdTime)",
        @Query("orderBy") orderBy: String = "createdTime desc",
        @Query("q") query: String = "name contains '.kwbackup'",
    ): DriveFileListDto

    /**
     * Downloads a file by ID, returning the raw response body.
     * The `alt=media` query parameter instructs Drive to return binary content
     * rather than the JSON file metadata.
     */
    @GET("drive/v3/files/{fileId}")
    suspend fun downloadFile(
        @Header("Authorization") auth: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media",
    ): ResponseBody

    /**
     * Permanently deletes a file. Drive returns HTTP 204 No Content on success.
     */
    @DELETE("drive/v3/files/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") auth: String,
        @Path("fileId") fileId: String,
    ): Response<Unit>
}
