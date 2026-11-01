package com.hudhud.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :templateId LIMIT 1")
    fun observeById(templateId: Long): Flow<TemplateEntity?>

    @Query("SELECT * FROM templates WHERE id = :templateId LIMIT 1")
    suspend fun findById(templateId: Long): TemplateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity): Int

    @Query("DELETE FROM templates WHERE id = :templateId")
    suspend fun delete(templateId: Long)

    @Query("SELECT * FROM templates ORDER BY updatedAt DESC, id DESC")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM templates WHERE sourceType = 'USER'")
    suspend fun countUserTemplates(): Int

    @Query("SELECT COUNT(*) FROM templates WHERE sourceType = 'BUILT_IN'")
    suspend fun countBuiltInTemplates(): Int

    @Query("SELECT * FROM templates WHERE sourceType = :sourceType ORDER BY updatedAt DESC, id DESC")
    fun observeBySourceType(sourceType: String): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE sourceType = :sourceType ORDER BY updatedAt DESC, id DESC")
    suspend fun getBySourceType(sourceType: String): List<TemplateEntity>
}
