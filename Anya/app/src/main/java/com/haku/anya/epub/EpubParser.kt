package com.haku.anya.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.haku.anya.data.Book
import nl.siegmann.epublib.domain.MediaType
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EpubParser(private val context: Context) {
    
    fun parseEpub(filePath: String): Book? {
        return try {
            val epubReader = EpubReader()
            val book = epubReader.readEpub(File(filePath))
            
            val title = book.title ?: "未知标题"
            val author = book.metadata.authors.firstOrNull()?.toString() ?: "未知作者"
            val fileSize = File(filePath).length()
            
            // 提取封面
            val coverPath = extractCover(book, filePath)
            
            // 计算总页数（基于图片资源）
            val totalPages = countImageResources(book)
            
            Book(
                title = title,
                author = author,
                coverPath = coverPath,
                filePath = filePath,
                fileSize = fileSize,
                totalPages = totalPages
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun extractCover(epubBook: nl.siegmann.epublib.domain.Book, filePath: String): String {
        val coverResource = epubBook.coverImage
        if (coverResource != null) {
            try {
                val coverDir = File(context.filesDir, "covers")
                if (!coverDir.exists()) {
                    coverDir.mkdirs()
                }
                
                val fileName = "${File(filePath).nameWithoutExtension}_cover.jpg"
                val coverFile = File(coverDir, fileName)
                
                val inputStream = coverResource.inputStream
                val outputStream = FileOutputStream(coverFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                
                return coverFile.absolutePath
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return ""
    }
    
    private fun countImageResources(epubBook: nl.siegmann.epublib.domain.Book): Int {
        return epubBook.resources.all.values.count { resource ->
            resource.mediaType == MediaType.PNG || 
            resource.mediaType == MediaType.JPG || 
            resource.mediaType == MediaType.JPEG
        }
    }
    
    fun getPageImage(epubBook: nl.siegmann.epublib.domain.Book, pageNumber: Int): Bitmap? {
        val imageResources = epubBook.resources.all.values.filter { resource ->
            resource.mediaType == MediaType.PNG || 
            resource.mediaType == MediaType.JPG || 
            resource.mediaType == MediaType.JPEG
        }.sortedBy { it.href }
        
        return if (pageNumber < imageResources.size) {
            try {
                val resource = imageResources[pageNumber]
                val inputStream = resource.inputStream
                BitmapFactory.decodeStream(inputStream).also {
                    inputStream.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null
    }
}
