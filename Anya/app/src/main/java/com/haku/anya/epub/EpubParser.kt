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
        private val SUPPORTED_IMAGE_EXT = setOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg")
        private const val MAX_IMAGE_SIZE_MB = 5L // 5MB限制
        
        private fun isImageFile(path: String): Boolean {
            return SUPPORTED_IMAGE_EXT.any { path.endsWith(it, ignoreCase = true) }
        }
    }
    
    data class EpubEntry(
        val path: String,
        val index: Int,
        val isImage: Boolean
    )
    
    suspend fun parseEpub(filePath: String): Book? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null
            
            // 解析OPF获取详细信息
            val opfPath = getOpfPath(filePath)
            val title = parseBookTitle(filePath, opfPath)
            val author = parseBookAuthor(filePath, opfPath)
            val totalPages = calculateTotalPages(filePath, opfPath)
            val coverPath = extractCoverImage(filePath, opfPath)
            
            Book(
                title = title.ifEmpty { file.nameWithoutExtension },
                author = author.ifEmpty { "未知作者" },
                coverPath = coverPath,
                filePath = filePath,
                fileSize = file.length(),
                totalPages = totalPages
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseEpub error", e)
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
            
            // 解析OPF获取详细信息
            val opfPath = getOpfPath(destFile.absolutePath)
            val title = parseBookTitle(destFile.absolutePath, opfPath)
            val author = parseBookAuthor(destFile.absolutePath, opfPath)
            val totalPages = calculateTotalPages(destFile.absolutePath, opfPath)
            val coverPath = extractCoverImage(destFile.absolutePath, opfPath)
            
            Book(
                title = title.ifEmpty { fileName.substringBeforeLast(".") },
                author = author.ifEmpty { "未知作者" },
                coverPath = coverPath,
                filePath = destFile.absolutePath,
                fileSize = destFile.length(),
                totalPages = totalPages
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
                val opfPath = getOpfPath(filePath)
                val spineOrder = parseOpfSpine(filePath, opfPath)
                val manifest = parseOpfManifest(filePath, opfPath)
                
                val entries = mutableListOf<EpubEntry>()
                var readingIndex = 0
                
                ZipFile(filePath).use { zipFile ->
                    // 按spine顺序创建条目
                    spineOrder.forEach { id ->
                        manifest[id]?.let { href ->
                            val entryPath = if (href.startsWith("/")) href.drop(1) 
                                else File(opfPath).parent?.let { "$it/$href" } ?: href
                            
                            zipFile.getEntry(entryPath)?.let { entry ->
                                entries.add(
                                    EpubEntry(
                                        path = entry.name,
                                        index = readingIndex++,
                                        isImage = isImageFile(entry.name)
                                    )
                                )
                            }
                        }
                    }
                    
                    // 添加不在spine中的其他资源文件
                    zipFile.entries().iterator().forEach { entry ->
                        if (!entries.any { it.path == entry.name }) {
                            if (isImageFile(entry.name)) {
                                entries.add(
                                    EpubEntry(
                                        path = entry.name,
                                        index = readingIndex++,
                                        isImage = true
                                    )
                                )
                            }
                        }
                    }
                }
                
                entries.sortedBy { it.index }
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
     * 从HTML内容中提取页面标题
     */
    fun getPageTitle(htmlContent: String): String? {
        return try {
            val titleStart = htmlContent.indexOf("<title>") + 7
            val titleEnd = htmlContent.indexOf("</title>")
            if (titleStart >= 7 && titleEnd > titleStart) {
                htmlContent.substring(titleStart, titleEnd).trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract title", e)
            null
        }
    }

    /**
     * 从页面标题中提取页码
     * 支持格式如: "第 19 頁"、"Page 19"等
     */
    fun extractPageNumber(title: String?): Int {
        if (title.isNullOrEmpty()) return -1
        
        try {
            // 查找中文格式: 第 19 頁 或 第19页
            val chinesePattern = Regex("第\\s*(\\d+)\\s*[頁页]")
            val chineseMatch = chinesePattern.find(title)
            if (chineseMatch != null) {
                return chineseMatch.groupValues[1].toIntOrNull() ?: -1
            }
            
            // 查找英文格式: Page 19
            val englishPattern = Regex("Page\\s*(\\d+)", RegexOption.IGNORE_CASE)
            val englishMatch = englishPattern.find(title)
            if (englishMatch != null) {
                return englishMatch.groupValues[1].toIntOrNull() ?: -1
            }
            
            // 尝试直接从标题中提取数字
            val numberPattern = Regex("(\\d+)")
            val numberMatch = numberPattern.find(title)
            if (numberMatch != null) {
                return numberMatch.groupValues[1].toIntOrNull() ?: -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract page number from title: $title", e)
        }
        
        return -1
    }

    /**
     * 解析OPF文件获取阅读顺序(spine)
     */
    private suspend fun parseOpfSpine(filePath: String, opfPath: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                ZipFile(filePath).use { zipFile ->
                    zipFile.getEntry(opfPath)?.let { entry ->
                        zipFile.getInputStream(entry).use { input ->
                            val opfContent = input.readBytes().toString(Charsets.UTF_8)
                            val spineStart = opfContent.indexOf("<spine")
                            val spineEnd = opfContent.indexOf("</spine>")
                            if (spineStart >= 0 && spineEnd > spineStart) {
                                opfContent.substring(spineStart, spineEnd)
                                    .split("<itemref")
                                    .filter { it.contains("idref=") }
                                    .map { it.substringAfter("idref=\"").substringBefore("\"") }
                            } else {
                                emptyList()
                            }
                        }
                    } ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "parseOpfSpine error", e)
                emptyList()
            }
        }

    /**
     * 解析OPF文件获取manifest映射
     */
    private suspend fun parseOpfManifest(filePath: String, opfPath: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            try {
                ZipFile(filePath).use { zipFile ->
                    zipFile.getEntry(opfPath)?.let { entry ->
                        zipFile.getInputStream(entry).use { input ->
                            val opfContent = input.readBytes().toString(Charsets.UTF_8)
                            val manifestStart = opfContent.indexOf("<manifest")
                            val manifestEnd = opfContent.indexOf("</manifest>")
                            if (manifestStart >= 0 && manifestEnd > manifestStart) {
                                opfContent.substring(manifestStart, manifestEnd)
                                    .split("<item")
                                    .filter { it.contains("href=") }
                                    .associate {
                                        val id = it.substringAfter("id=\"").substringBefore("\"")
                                        val href = it.substringAfter("href=\"").substringBefore("\"")
                                        id to href
                                    }
                            } else {
                                emptyMap()
                            }
                        }
                    } ?: emptyMap()
                }
            } catch (e: Exception) {
                Log.e(TAG, "parseOpfManifest error", e)
                emptyMap()
            }
        }

    /**
     * 获取OPF文件路径
     */
    public suspend fun getOpfPath(filePath: String): String =
        withContext(Dispatchers.IO) {
            try {
                ZipFile(filePath).use { zipFile ->
                    zipFile.getEntry("META-INF/container.xml")?.let { entry ->
                        zipFile.getInputStream(entry).use { input ->
                            val container = input.readBytes().toString(Charsets.UTF_8)
                            container.substringAfter("<rootfile")
                                .substringAfter("full-path=\"")
                                .substringBefore("\"")
                        }
                    } ?: "content.opf"
                }
            } catch (e: Exception) {
                Log.e(TAG, "getOpfPath error", e)
                "content.opf"
            }
        }

    /**
     * 解析书籍标题从OPF文件
     */
    private suspend fun parseBookTitle(filePath: String, opfPath: String): String = withContext(Dispatchers.IO) {
        try {
            ZipFile(filePath).use { zipFile ->
                zipFile.getEntry(opfPath)?.let { entry ->
                    zipFile.getInputStream(entry).use { input ->
                        val opfContent = input.readBytes().toString(Charsets.UTF_8)
                        
                        // 查找dc:title元素
                        val titleRegex = Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.IGNORE_CASE)
                        val match = titleRegex.find(opfContent)
                        match?.groupValues?.get(1)?.trim() ?: ""
                    }
                } ?: ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseBookTitle error", e)
            ""
        }
    }

    /**
     * 解析书籍作者从OPF文件
     */
    private suspend fun parseBookAuthor(filePath: String, opfPath: String): String = withContext(Dispatchers.IO) {
        try {
            ZipFile(filePath).use { zipFile ->
                zipFile.getEntry(opfPath)?.let { entry ->
                    zipFile.getInputStream(entry).use { input ->
                        val opfContent = input.readBytes().toString(Charsets.UTF_8)
                        
                        // 查找dc:creator元素
                        val authorRegex = Regex("<dc:creator[^>]*>(.*?)</dc:creator>", RegexOption.IGNORE_CASE)
                        val match = authorRegex.find(opfContent)
                        match?.groupValues?.get(1)?.trim() ?: ""
                    }
                } ?: ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseBookAuthor error", e)
            ""
        }
    }

    /**
     * 计算书籍总页数
     */
    private suspend fun calculateTotalPages(filePath: String, opfPath: String): Int = withContext(Dispatchers.IO) {
        try {
            // 1. 解析spine获取阅读顺序
            val spineOrder = parseOpfSpine(filePath, opfPath)
            val manifest = parseOpfManifest(filePath, opfPath)
            
            // 2. 统计HTML/XHTML文件的数量作为页数基础
            var htmlCount = 0
            
            spineOrder.forEach { id ->
                manifest[id]?.let { href ->
                    if (href.endsWith(".html", ignoreCase = true) || href.endsWith(".xhtml", ignoreCase = true)) {
                        htmlCount++
                    }
                }
            }
            
            // 3. 如果spine为空，回退到直接计算所有HTML文件数量
            if (htmlCount == 0) {
                ZipFile(filePath).use { zipFile ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (!entry.isDirectory && 
                            (name.endsWith(".html", ignoreCase = true) || 
                             name.endsWith(".xhtml", ignoreCase = true))) {
                            htmlCount++
                        }
                    }
                }
            }
            
            // 4. 至少返回1页
            maxOf(htmlCount, 1)
        } catch (e: Exception) {
            Log.e(TAG, "calculateTotalPages error", e)
            1
        }
    }

    /**
     * 提取封面图片
     */
    public suspend fun extractCoverImage(filePath: String, opfPath: String): String = withContext(Dispatchers.IO) {
        try {
            ZipFile(filePath).use { zipFile ->
                zipFile.getEntry(opfPath)?.let { entry ->
                    zipFile.getInputStream(entry).use { input ->
                        val opfContent = input.readBytes().toString(Charsets.UTF_8)
                        
                        // 尝试查找cover-image属性
                        val coverRegex = Regex("""cover-image[^>]*href="([^"]*)"""", RegexOption.IGNORE_CASE)
                        var coverPath = coverRegex.find(opfContent)?.groupValues?.get(1) ?: ""
                        
                        if (coverPath.isEmpty()) {
                            // 尝试查找cover项
                            val coverItemRegex = Regex("""<item[^>]*id=['"]?cover['"]?[^>]*href="([^"]*)"""", RegexOption.IGNORE_CASE)
                            coverPath = coverItemRegex.find(opfContent)?.groupValues?.get(1) ?: ""
                        }
                        
                        if (coverPath.isNotEmpty()) {
                            // 修复路径
                            val fixedPath = if (coverPath.startsWith('/')) coverPath.drop(1) 
                                else File(opfPath).parent?.let { "$it/$coverPath" } ?: coverPath
                            
                            // 检查文件是否存在
                            if (zipFile.getEntry(fixedPath) != null) {
                                return@withContext extractImage(filePath, fixedPath)
                            }
                        }
                        
                        // 如果没有找到封面，尝试返回第一个图片文件
                        val entries = zipFile.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            if (!e.isDirectory && isImageFile(e.name)) {
                                return@withContext extractImage(filePath, e.name)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractCoverImage error", e)
        }
        ""
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
                        // 检查图片大小限制
                        if (entry.size > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
                            Log.w(TAG, "Image too large (${entry.size} bytes), skipping: $imageEntry")
                            return@withContext ""
                        }
                        
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