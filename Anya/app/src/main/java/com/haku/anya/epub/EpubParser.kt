package com.haku.anya.epub

import android.content.Context
import android.graphics.Bitmap
import com.haku.anya.data.Book
import java.io.File

// TODO: 重新实现EPUB解析功能，当前暂时禁用
class EpubParser(private val context: Context) {
    
    fun parseEpub(filePath: String): Book? {
        // 临时实现，返回基本信息
        return try {
            val file = File(filePath)
            val title = file.nameWithoutExtension
            val author = "未知作者"
            val fileSize = file.length()
            
            Book(
                title = title,
                author = author,
                coverPath = "",
                filePath = filePath,
                fileSize = fileSize,
                totalPages = 0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun getPageImage(epubBook: Any, pageNumber: Int): Bitmap? {
        // 临时实现，返回null
        return null
    }
}
