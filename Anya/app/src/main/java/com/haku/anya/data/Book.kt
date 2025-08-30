package com.haku.anya.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val coverPath: String,
    val filePath: String,
    val fileSize: Long,
    val lastReadPage: Int = 0,
    val totalPages: Int = 0,
    val categoryId: Long = 1, // 默认分类
    val addedTime: Long = System.currentTimeMillis(),
    val lastReadTime: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
