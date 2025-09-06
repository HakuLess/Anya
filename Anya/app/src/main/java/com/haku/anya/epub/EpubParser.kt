package com.haku.anya.epub

import android.content.Context
import android.net.Uri
import android.util.Log
import com.haku.anya.data.Book
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * EPUB解析器
 * 支持结构解析和内容提取
 */
class EpubParser(private val context: Context) {
    companion object {
        private const val TAG = "EpubParser"
        private val SUPPORTED_IMAGE_EXT = setOf(".jpg", ".jpeg", ".png", ".gif")
    }
    
    data class EpubEntry(
        val name: String,
        val size: Long,
        val isDirectory: Boolean
    )
    
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
            Log.e(TAG, "parseEpubFromUri error", e)
            null
        }
    }

    /**
     * 获取EPUB文件内容
     */
    /**
     * 解析EPUB文件结构
     */
    suspend fun parseEpubStructure(filePath: String): List<EpubEntry> = 
        withContext(Dispatchers.IO) {
            try {
                val zipFile = ZipFile(filePath)
                val entries = mutableListOf<EpubEntry>()
                
                zipFile.entries().iterator().forEach { entry ->
                    entries.add(
                        EpubEntry(
                            name = entry.name,
                            size = entry.size,
                            isDirectory = entry.isDirectory
                        )
                    )
                }
                
                entries.sortedBy { it.name }
            } catch (e: Exception) {
                Log.e(TAG, "parseEpubStructure error", e)
                emptyList()
            }
        }

    /**
     * 提取EPUB资源文件并保持原始目录结构
     */
    suspend fun extractEpubResources(filePath: String): String = withContext(Dispatchers.IO) {
        try {
            val epubFile = File(filePath)
            if (!epubFile.exists()) {
                Log.e(TAG, "EPUB file not found: $filePath")
                return@withContext ""
            }

            val outputDir = File(context.getExternalFilesDir(null), "epub_resources/${epubFile.nameWithoutExtension}")
            Log.d(TAG, "Preparing to extract to: ${outputDir.absolutePath}")
            
            // 清空并重建目录
            if (outputDir.exists()) {
                Log.d(TAG, "Cleaning existing directory")
                outputDir.deleteRecursively()
            }
            
            if (!outputDir.mkdirs()) {
                Log.e(TAG, "Failed to create output directory")
                return@withContext ""
            }

            // 解压整个EPUB文件
            var extractedCount = 0
            ZipFile(filePath).use { zipFile ->
                for (entry in zipFile.entries()) {
                    if (!entry.isDirectory) {
                        val outputFile = File(outputDir, entry.name)
                        try {
                            outputFile.parentFile?.mkdirs()
                            
                            zipFile.getInputStream(entry)?.use { input ->
                                FileOutputStream(outputFile).use { output ->
                                    input.copyTo(output)
                                    extractedCount++
                                    Log.d(TAG, "Extracted: ${entry.name} to ${outputFile.absolutePath}")
                                    
                                    // 验证文件是否可读
                                    if (!outputFile.canRead()) {
                                        Log.e(TAG, "Extracted file is not readable: ${outputFile.absolutePath}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to extract ${entry.name}", e)
                        }
                    }
                }
            }

            Log.d(TAG, "Extracted $extractedCount files to ${outputDir.absolutePath}")
            if (extractedCount == 0) {
                Log.e(TAG, "No files were extracted")
                return@withContext ""
            }

            // 验证关键目录是否存在
            val imageDir = File(outputDir, "image")
            if (!imageDir.exists()) {
                Log.e(TAG, "Image directory not found in extracted resources")
            }

            return@withContext outputDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "extractEpubResources error", e)
            ""
        }
    }

    /**
     * 获取EPUB条目内容
     */
    suspend fun getEntryContent(filePath: String, entryName: String): String =
        withContext(Dispatchers.IO) {
            try {
                val resourceDir = File(context.getExternalFilesDir(null), 
                    "epub_resources/${File(filePath).nameWithoutExtension}")
                val entryFile = File(resourceDir, entryName)
                
                if (entryFile.exists()) {
                    entryFile.readText(Charsets.UTF_8)
                } else {
                    Log.e(TAG, "Entry file not found: ${entryFile.absolutePath}")
                    ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "getEntryContent error", e)
                ""
            }
        }

    /**
     * 提取图片文件到缓存目录
     */
    suspend fun extractImage(filePath: String, imageEntry: String): String =
        withContext(Dispatchers.IO) {
            try {
                // 创建专用的图片缓存目录
                val imageCacheDir = File(context.cacheDir, "epub_images")
                if (!imageCacheDir.exists()) {
                    imageCacheDir.mkdirs()
                }
                
                // 保留原始文件名但确保唯一性
                val originalName = imageEntry.substringAfterLast('/')
                val fileName = "${System.currentTimeMillis()}_$originalName"
                val outputFile = File(imageCacheDir, fileName)
                
                Log.d(TAG, "Extracting image: $imageEntry to ${outputFile.absolutePath}")
                
                // 确保父目录存在
                outputFile.parentFile?.mkdirs()
                
                // 提取图片
                ZipFile(filePath).use { zipFile ->
                    zipFile.getEntry(imageEntry)?.let { entry ->
                        if (entry.size > 0) {
                            zipFile.getInputStream(entry)?.use { input ->
                                FileOutputStream(outputFile).use { output ->
                                    input.copyTo(output)
                                    Log.d(TAG, "Successfully extracted ${entry.size} bytes to ${outputFile.absolutePath}")
                                }
                            }
                        } else {
                            Log.e(TAG, "EPUB entry is empty: $imageEntry")
                            return@withContext ""
                        }
                    } ?: run {
                        Log.e(TAG, "EPUB entry not found: $imageEntry")
                        return@withContext ""
                    }
                }
                
                if (outputFile.exists() && outputFile.length() > 0) {
                    outputFile.absolutePath
                } else {
                    Log.e(TAG, "Extracted image file is empty or not created")
                    ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "extractImage error for entry: $imageEntry", e)
                ""
            }
        }
}