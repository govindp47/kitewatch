package com.kitewatch.infra.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for [CredentialStore].
 *
 * Requires a physical device or emulator running API 26+ (Android Keystore).
 */
@RunWith(AndroidJUnit4::class)
class CredentialStoreTest {
    private lateinit var credentialStore: CredentialStore
    private val testPrefsName = "test_kitewatch_secrets"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        val prefs =
            EncryptedSharedPreferences.create(
                context,
                testPrefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        credentialStore = CredentialStore(prefs)
        // Start each test with a clean slate.
        credentialStore.clearAll()
    }

    @After
    fun tearDown() {
        credentialStore.clearAll()
    }

    // -------------------------------------------------------------------------
    // Save and retrieve
    // -------------------------------------------------------------------------

    @Test
    fun saveAndRetrieveAccessToken() {
        credentialStore.saveAccessToken("test_access_token_value")
        assertEquals("test_access_token_value", credentialStore.getAccessToken())
    }

    @Test
    fun saveAndRetrieveApiKey() {
        credentialStore.saveApiKey("my_api_key")
        assertEquals("my_api_key", credentialStore.getApiKey())
    }

    @Test
    fun saveAndRetrieveUserId() {
        credentialStore.saveUserId("ZU1234")
        assertEquals("ZU1234", credentialStore.getUserId())
    }

    @Test
    fun saveAndRetrieveGoogleOAuthToken() {
        credentialStore.saveGoogleOAuthToken("ya29.google_token")
        assertEquals("ya29.google_token", credentialStore.getGoogleOAuthToken())
    }

    // -------------------------------------------------------------------------
    // clearAccessToken
    // -------------------------------------------------------------------------

    @Test
    fun clearAccessTokenRemovesOnlyAccessToken() {
        credentialStore.saveAccessToken("token_to_clear")
        credentialStore.saveApiKey("key_that_stays")

        credentialStore.clearAccessToken()

        assertNull(credentialStore.getAccessToken())
        assertEquals("key_that_stays", credentialStore.getApiKey())
    }

    // -------------------------------------------------------------------------
    // clearAll
    // -------------------------------------------------------------------------

    @Test
    fun clearAllRemovesAllKeys() {
        credentialStore.saveAccessToken("token")
        credentialStore.saveApiKey("key")
        credentialStore.saveUserId("ZU9999")
        credentialStore.saveGoogleOAuthToken("g_token")

        credentialStore.clearAll()

        assertNull(credentialStore.getAccessToken())
        assertNull(credentialStore.getApiKey())
        assertNull(credentialStore.getUserId())
        assertNull(credentialStore.getGoogleOAuthToken())
    }

    // -------------------------------------------------------------------------
    // Missing keys return null
    // -------------------------------------------------------------------------

    @Test
    fun getMissingAccessTokenReturnsNull() {
        assertNull(credentialStore.getAccessToken())
    }

    @Test
    fun getMissingApiKeyReturnsNull() {
        assertNull(credentialStore.getApiKey())
    }

    // -------------------------------------------------------------------------
    // No plaintext in the on-disk preferences file
    // -------------------------------------------------------------------------

    @Test
    fun plaintextPrefsFileContainsNoCleartextToken() {
        val sensitiveValue = "plaintext_secret_12345"
        credentialStore.saveAccessToken(sensitiveValue)

        // EncryptedSharedPreferences writes XML with encrypted ciphertext only.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsFile =
            File(
                context.filesDir.parent,
                "shared_prefs/$testPrefsName.xml",
            )

        // The file must exist (prefs were persisted).
        assert(prefsFile.exists()) { "Preferences file not found at ${prefsFile.absolutePath}" }

        val fileContent = prefsFile.readText()
        assertFalse(
            "Raw secret found in plaintext preferences file — EncryptedSharedPreferences is not encrypting",
            fileContent.contains(sensitiveValue),
        )
    }
}
