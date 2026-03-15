package com.kitewatch.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitewatch.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Append-only DAO for the `transactions` table (INV-10).
 * No @Update or @Delete methods — transactions are an immutable ledger.
 */
@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    /** Emits all transactions, newest first. Re-emits on any table change. */
    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** Emits transactions matching the given type, newest first. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE type = :type
        ORDER BY transaction_date DESC, id DESC
        """,
    )
    fun observeByType(type: String): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE stock_code = :stockCode
        ORDER BY transaction_date DESC, id DESC
        """,
    )
    suspend fun getByStockCode(stockCode: String): List<TransactionEntity>

    /** Sum of all positive amount_paisa values (inflows). Null when table is empty. */
    @Query("SELECT SUM(amount_paisa) FROM transactions WHERE amount_paisa > 0")
    suspend fun getTotalCredits(): Long?

    /** Sum of absolute values of all negative amount_paisa (outflows). Null when table is empty. */
    @Query("SELECT SUM(ABS(amount_paisa)) FROM transactions WHERE amount_paisa < 0")
    suspend fun getTotalDebits(): Long?

    // NO @Update — transactions are immutable after insertion (INV-10)
    // NO @Delete — transactions are never deleted (INV-10)

    /** Returns all transactions; used for full backup data assembly. */
    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    /** Deletes all rows; used during backup restore (INV-10 exception: restore is intentional). */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
