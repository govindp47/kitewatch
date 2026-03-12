package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.AccountBindingEntity

@Dao
interface AccountBindingDao {
    /** INSERT OR REPLACE — enforces single-row invariant via fixed PK = 1. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(binding: AccountBindingEntity)

    @Query("SELECT * FROM account_binding WHERE id = 1 LIMIT 1")
    suspend fun get(): AccountBindingEntity?

    @Query("DELETE FROM account_binding")
    suspend fun clear()
}
