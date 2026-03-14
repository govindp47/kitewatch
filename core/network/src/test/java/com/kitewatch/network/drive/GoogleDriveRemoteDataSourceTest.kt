package com.kitewatch.network.drive

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class GoogleDriveRemoteDataSourceTest {
    private val mockWebServer = MockWebServer()
    private val mockPrefs: SharedPreferences = mockk()

    private lateinit var dataSource: GoogleDriveRemoteDataSource

    @Before
    fun setUp() {
        mockWebServer.start()

        every {
            mockPrefs.getString(GoogleDriveRemoteDataSource.KEY_GOOGLE_OAUTH_TOKEN, "")
        } returns "test-oauth-token"

        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(mockWebServer.url("/"))
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

        val apiClient = retrofit.create(GoogleDriveApiClient::class.java)
        dataSource = GoogleDriveRemoteDataSource(apiClient, mockPrefs)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // -----------------------------------------------------------------------
    // uploadBackup
    // -----------------------------------------------------------------------

    @Test
    fun `uploadBackup returns DriveFileId from response`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"id":"drive-abc123","name":"test.kwbackup","size":"512"}"""),
            )

            val fileId = dataSource.uploadBackup("test.kwbackup", ByteArray(512) { 0 })

            assertEquals("drive-abc123", fileId)
        }

    @Test
    fun `uploadBackup sends Authorization header with Bearer prefix`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"id":"id1","name":"test.kwbackup"}"""),
            )

            dataSource.uploadBackup("test.kwbackup", ByteArray(16))

            val recorded = mockWebServer.takeRequest()
            assertEquals("Bearer test-oauth-token", recorded.getHeader("Authorization"))
        }

    @Test
    fun `uploadBackup sends multipart body`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"id":"id1","name":"test.kwbackup"}"""),
            )

            dataSource.uploadBackup("test.kwbackup", ByteArray(8) { 0x42 })

            val recorded = mockWebServer.takeRequest()
            val contentType = recorded.getHeader("Content-Type") ?: ""
            assertTrue("Expected multipart/related", contentType.contains("multipart/related"))
        }

    // -----------------------------------------------------------------------
    // listBackups
    // -----------------------------------------------------------------------

    @Test
    fun `listBackups returns parsed DriveBackupEntry list`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "files": [
                            {
                              "id": "id1",
                              "name": "kitewatch_ZD1234_20260315_100000.kwbackup",
                              "size": "2048",
                              "createdTime": "2026-03-15T10:00:00.000Z"
                            },
                            {
                              "id": "id2",
                              "name": "kitewatch_ZD1234_20260314_090000.kwbackup",
                              "size": "1024",
                              "createdTime": "2026-03-14T09:00:00.000Z"
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            )

            val entries = dataSource.listBackups()

            assertEquals(2, entries.size)

            val first = entries[0]
            assertEquals("id1", first.fileId)
            assertEquals("kitewatch_ZD1234_20260315_100000.kwbackup", first.fileName)
            assertEquals(2048L, first.fileSizeBytes)
            assertEquals("2026-03-15T10:00:00.000Z", first.createdAt)

            val second = entries[1]
            assertEquals("id2", second.fileId)
            assertEquals(1024L, second.fileSizeBytes)
        }

    @Test
    fun `listBackups returns empty list when Drive has no backup files`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"files":[]}"""),
            )

            val entries = dataSource.listBackups()

            assertTrue(entries.isEmpty())
        }

    @Test
    fun `listBackups sends Authorization header`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"files":[]}"""),
            )

            dataSource.listBackups()

            val recorded = mockWebServer.takeRequest()
            assertEquals("Bearer test-oauth-token", recorded.getHeader("Authorization"))
        }

    // -----------------------------------------------------------------------
    // downloadBackup
    // -----------------------------------------------------------------------

    @Test
    fun `downloadBackup returns complete file bytes`() =
        runTest {
            val expectedBytes = ByteArray(256) { it.toByte() }
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(expectedBytes)),
            )

            val actualBytes = dataSource.downloadBackup("file-id-xyz")

            assertArrayEquals(expectedBytes, actualBytes)
        }

    @Test
    fun `downloadBackup sends Authorization header`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(ByteArray(4))),
            )

            dataSource.downloadBackup("file-id-xyz")

            val recorded = mockWebServer.takeRequest()
            assertEquals("Bearer test-oauth-token", recorded.getHeader("Authorization"))
        }

    @Test
    fun `downloadBackup includes alt=media query parameter`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(ByteArray(4))),
            )

            dataSource.downloadBackup("file-id-xyz")

            val recorded = mockWebServer.takeRequest()
            val path = recorded.path ?: ""
            assertTrue("alt=media must be present", path.contains("alt=media"))
        }

    // -----------------------------------------------------------------------
    // deleteBackup
    // -----------------------------------------------------------------------

    @Test
    fun `deleteBackup succeeds on HTTP 204`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(204))

            // Should not throw
            dataSource.deleteBackup("file-to-delete")

            val recorded = mockWebServer.takeRequest()
            assertEquals("Bearer test-oauth-token", recorded.getHeader("Authorization"))
        }

    @Test
    fun `deleteBackup throws DriveApiException on non-2xx response`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(404))

            var threw = false
            try {
                dataSource.deleteBackup("missing-file-id")
            } catch (e: DriveApiException) {
                threw = true
                assertTrue(e.message?.contains("404") == true)
            }
            assertTrue("Expected DriveApiException", threw)
        }
}
