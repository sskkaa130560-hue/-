package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "suitcases")
data class Suitcase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Suitcase::class,
            parentColumns = ["id"],
            childColumns = ["suitcaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["suitcaseId"])]
)
data class SuitcaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val suitcaseId: Long,
    val categoryName: String,
    val name: String,
    val isPacked: Boolean = false,
    val displayOrder: Int = 0
)
