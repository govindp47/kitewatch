package com.kitewatch.infra.auth

import android.content.Context
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates or retrieves the AES256_GCM [MasterKey] from the Android Keystore.
 *
 * The MasterKey is held in memory only — it is never serialized or written to storage.
 * [MasterKey.Builder.setUserAuthenticationRequired] is intentionally omitted (defaults
 * to false) so that background [androidx.work.WorkManager] operations can access the
 * Keystore without a live biometric challenge.
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
    }
