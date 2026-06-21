package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class SuitcaseWithStats(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val totalItems: Int,
    val packedItems: Int
)

@Dao
interface SuitcaseDao {
    @Query("SELECT * FROM suitcases ORDER BY createdAt DESC")
    fun getAllSuitcases(): Flow<List<Suitcase>>

    @Query("""
        SELECT 
            s.id as id, 
            s.name as name, 
            s.createdAt as createdAt,
            COUNT(i.id) as totalItems,
            SUM(CASE WHEN i.isPacked = 1 THEN 1 ELSE 0 END) as packedItems
        FROM suitcases s
        LEFT JOIN items i ON s.id = i.suitcaseId
        GROUP BY s.id
        ORDER BY s.createdAt DESC
    """)
    fun getAllSuitcasesWithStats(): Flow<List<SuitcaseWithStats>>

    @Query("SELECT * FROM suitcases WHERE id = :id")
    suspend fun getSuitcaseById(id: Long): Suitcase?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuitcase(suitcase: Suitcase): Long

    @Update
    suspend fun updateSuitcase(suitcase: Suitcase)

    @Delete
    suspend fun deleteSuitcase(suitcase: Suitcase)

    @Query("SELECT * FROM items WHERE suitcaseId = :suitcaseId ORDER BY id ASC")
    fun getItemsForSuitcase(suitcaseId: Long): Flow<List<SuitcaseItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<SuitcaseItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SuitcaseItem): Long

    @Update
    suspend fun updateItem(item: SuitcaseItem)

    @Delete
    suspend fun deleteItem(item: SuitcaseItem)

    @Query("DELETE FROM items WHERE suitcaseId = :suitcaseId")
    suspend fun deleteItemsForSuitcase(suitcaseId: Long)

    @Query("SELECT * FROM suitcases")
    suspend fun getAllSuitcasesSync(): List<Suitcase>

    @Query("SELECT * FROM items")
    suspend fun getAllItemsSync(): List<SuitcaseItem>

    @Query("SELECT * FROM items")
    fun getAllItemsFlow(): Flow<List<SuitcaseItem>>

    @Query("DELETE FROM suitcases")
    suspend fun clearAllSuitcases()

    @Query("DELETE FROM items")
    suspend fun clearAllItems()

    @Transaction
    suspend fun overwriteDatabase(suitcases: List<Suitcase>, items: List<SuitcaseItem>) {
        clearAllItems()
        clearAllSuitcases()
        for (s in suitcases) {
            insertSuitcase(s)
        }
        insertItems(items)
    }
}

