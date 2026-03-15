package com.kitewatch.infra.auth

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates or retrieves the AES256_GCM [MasterKey] from the Android Keystore.
 *
 * The MasterKey is held in memory only — it is never serialized or written to storage.
 * [MasterKey.Builder.setUserAuthenticationRequired] is intentionally omitted (defaults
 * to false) so that background [androidx.work.WorkManager] operations can access the
 * Keystore without a live biometric challenge.
 *
 * Also derives [databasePassphrase]: a 256-bit random passphrase generated once on first
 * launch, stored in a dedicated [EncryptedSharedPreferences] file under [KEY_DB_PASSPHRASE],
 * and retrieved on subsequent launches. The passphrase is protected by the Android
 * Keystore-backed [masterKey].
 */
@Singleton
class MasterKeyProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val masterKey: MasterKey by lazy {
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }

        /**
         * Returns the SQLCipher database passphrase (32 random bytes).
         *
         * On first launch the passphrase is generated via [SecureRandom], base64-encoded,
         * and stored in a dedicated [EncryptedSharedPreferences] file. On subsequent
         * launches it is decoded and returned from storage. The backing preferences file
         * is encrypted with the Android Keystore-backed [masterKey].
         *
         * This property is lazy to ensure [masterKey] is initialised before the
         * [EncryptedSharedPreferences] that wraps it is opened.
         */
        val databasePassphrase: ByteArray by lazy {
            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    PASSPHRASE_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )

            val stored = prefs.getString(KEY_DB_PASSPHRASE, null)
            if (stored != null) {
                Base64.decode(stored, Base64.NO_WRAP)
            } else {
                val passphrase = ByteArray(PASSPHRASE_LENGTH).also { SecureRandom().nextBytes(it) }
                prefs
                    .edit()
                    .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
                    .apply()
                passphrase
            }
        }

        companion object {
            private const val PASSPHRASE_PREFS_FILE = "kitewatch_db_key"
            private const val KEY_DB_PASSPHRASE = "db_passphrase_b64"
            private const val PASSPHRASE_LENGTH = 32
        }
    }
