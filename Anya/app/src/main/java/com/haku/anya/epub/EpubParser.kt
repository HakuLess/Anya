package com.haku.anya.epub

import android.content.Context
import android.net.Uri
import com.haku.anya.data.Book
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 简化版EPUB解析器
 * 仅提取基本信息，不依赖第三方库
 */
class EpubParser(private val context: Context) {
    
    suspend fun parseEpub(filePath: String): Book? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null
            
            // 简化实现：仅提取文件名和大小
            Book(
                title = file.nameWithoutExtension,
                author = "未知作者",
                coverPath = "",
                filePath = filePath,
                fileSize = file.length(),
                totalPages = 1 // 简化处理
            )
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun parseEpubFromUri(uri: Uri): Book? = withContext(Dispatchers.IO) {
        try {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndex("_display_name"))
                else null
            } ?: uri.path?.substringAfterLast('/') ?: "unknown.epub"
            
            // 复制文件到应用目录
            val destFile = File(context.getExternalFilesDir(null), "books/$fileName")
            destFile.parentFile?.mkdirs()
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            Book(
                title = fileName.substringBeforeLast("."),
                author = "未知作者",
                coverPath = "",
                filePath = destFile.absolutePath,
                fileSize = destFile.length(),
                totalPages = 1 // 简化处理
            )
        } catch (e: Exception) {
            null
        }
    }
}