package com.etrisad.zenith.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.etrisad.zenith.data.local.entity.ShieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldDao {
    @Query("SELECT * FROM shields")
    fun getAllShields(): Flow<List<ShieldEntity>>

    @Query("SELECT * FROM shields WHERE packageName = :packageName")
    suspend fun getShieldByPackageName(packageName: String): ShieldEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShield(shield: ShieldEntity)

    @Update
    suspend fun updateShield(shield: ShieldEntity)

    @Delete
    suspend fun deleteShield(shield: ShieldEntity)
    
    @Query("SELECT EXISTS(SELECT 1 FROM shields WHERE packageName = :packageName LIMIT 1)")
    fun isAppShielded(packageName: String): Flow<Boolean>

    // Todo related queries
    @Query("SELECT * FROM todos WHERE packageName = :packageName ORDER BY `order` ASC")
    fun getTodosForApp(packageName: String): Flow<List<com.etrisad.zenith.data.local.entity.TodoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: com.etrisad.zenith.data.local.entity.TodoEntity)

    @Update
    suspend fun updateTodo(todo: com.etrisad.zenith.data.local.entity.TodoEntity)

    @Delete
    suspend fun deleteTodo(todo: com.etrisad.zenith.data.local.entity.TodoEntity)

    @Query("DELETE FROM todos WHERE packageName = :packageName")
    suspend fun deleteTodosForApp(packageName: String)
}
