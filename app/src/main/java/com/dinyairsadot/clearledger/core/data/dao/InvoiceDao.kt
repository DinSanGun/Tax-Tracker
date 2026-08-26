package com.dinyairsadot.clearledger.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dinyairsadot.clearledger.core.data.entities.InvoiceEntity

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices")
    suspend fun getAll(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE categoryId = :categoryId ORDER BY dueDateEpochDay DESC, id DESC")
    suspend fun getByCategoryId(categoryId: Long): List<InvoiceEntity>
    
    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: Long): InvoiceEntity?

    /** Number of invoices currently persisting [attachmentUri] on their `attachmentUri` column. */
    @Query("SELECT COUNT(*) FROM invoices WHERE attachmentUri = :attachmentUri")
    suspend fun countByAttachmentUri(attachmentUri: String): Int

    /** Number of invoices currently referencing the managed attachment file [attachmentFileName]. */
    @Query("SELECT COUNT(*) FROM invoices WHERE attachmentFileName = :attachmentFileName")
    suspend fun countByAttachmentFileName(attachmentFileName: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: InvoiceEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(invoices: List<InvoiceEntity>)
    
    @Update
    suspend fun update(invoice: InvoiceEntity)
    
    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM invoices WHERE categoryId = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long)

    @Query("DELETE FROM invoices")
    suspend fun deleteAll()
}
