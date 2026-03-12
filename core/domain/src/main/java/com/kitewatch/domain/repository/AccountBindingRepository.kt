package com.kitewatch.domain.repository

import com.kitewatch.domain.model.AccountBinding

interface AccountBindingRepository {
    /** BR-01: returns false if an account is already bound; does not overwrite. */
    suspend fun bind(binding: AccountBinding): Boolean

    suspend fun getBinding(): AccountBinding?

    suspend fun isBound(): Boolean

    suspend fun clear()
}
