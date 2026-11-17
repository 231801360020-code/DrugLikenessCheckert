package com.example.druglikenesschecker.data.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DrugDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrug(drug: DrugEntity): Long

    @Delete
    suspend fun deleteDrug(drug: DrugEntity)

    @Query("DELETE FROM checked_drugs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM checked_drugs ORDER BY timestamp DESC")
    fun getAllDrugs(): LiveData<List<DrugEntity>>

    @Query("SELECT * FROM checked_drugs WHERE id = :id")
    suspend fun getDrugById(id: Long): DrugEntity?
}
