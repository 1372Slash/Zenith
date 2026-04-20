package com.etrisad.zenith.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = ShieldEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packageName"])]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val content: String,
    val isDone: Boolean = false,
    val order: Int = 0
)
