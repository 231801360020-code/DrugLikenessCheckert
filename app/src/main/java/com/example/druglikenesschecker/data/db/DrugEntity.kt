package com.example.druglikenesschecker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checked_drugs")
data class DrugEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val cid: Long,
    val formula: String,
    val iupacName: String,
    val smiles: String,
    val imageUrl: String,
    val mw: Double,
    val logP: Double,
    val hbd: Int,
    val hba: Int,
    val tpsa: Double,
    val lipinskiPasses: Boolean,
    val veberPasses: Boolean,
    val oralBioavailability: String,
    val bbbPenetration: Boolean,
    val hergInhibition: Boolean,
    val predictionsSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)
