package com.haku.anya.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false
)
