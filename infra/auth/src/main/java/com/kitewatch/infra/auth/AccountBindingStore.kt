package com.kitewatch.infra.auth

import android.content.SharedPreferences
import com.kitewatch.domain.model.AccountBinding
import com.kitewatch.domain.repository.AccountBindingRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists [AccountBinding] fields as individual entries in [EncryptedSharedPreferences].
 *
 * Uses the same encrypted prefs file as [CredentialStore] — injected unqualified.
 */
@Singleton
class AccountBindingStore
    @Inject
    constructor(
        private val prefs: SharedPreferences,
    ) : AccountBindingRepository {
        override suspend fun bind(binding: AccountBinding): Boolean {
            if (prefs.contains(KEY_BOUND_USER_ID)) return false
            prefs
                .edit()
                .putString(KEY_BOUND_USER_ID, binding.userId)
                .putString(KEY_BOUND_USER_NAME, binding.userName)
                .putString(KEY_BOUND_API_KEY, binding.apiKey)
                .putLong(KEY_BOUND_AT_EPOCH_SECONDS, binding.boundAt.epochSecond)
                .apply()
            return true
        }

        override suspend fun getBinding(): AccountBinding? {
            val userId = prefs.getString(KEY_BOUND_USER_ID, null) ?: return null
            return AccountBinding(
                userId = userId,
                userName = prefs.getString(KEY_BOUND_USER_NAME, "") ?: "",
                apiKey = prefs.getString(KEY_BOUND_API_KEY, "") ?: "",
                boundAt = Instant.ofEpochSecond(prefs.getLong(KEY_BOUND_AT_EPOCH_SECONDS, 0L)),
            )
        }

        override suspend fun isBound(): Boolean = prefs.contains(KEY_BOUND_USER_ID)

        override suspend fun clear() {
            prefs
                .edit()
                .remove(KEY_BOUND_USER_ID)
                .remove(KEY_BOUND_USER_NAME)
                .remove(KEY_BOUND_API_KEY)
                .remove(KEY_BOUND_AT_EPOCH_SECONDS)
                .apply()
        }

        companion object {
            internal const val KEY_BOUND_USER_ID = "bound_user_id"
            internal const val KEY_BOUND_USER_NAME = "bound_user_name"
            internal const val KEY_BOUND_API_KEY = "bound_api_key"
            internal const val KEY_BOUND_AT_EPOCH_SECONDS = "bound_at_epoch_seconds"
        }
    }
