package com.haku.anya.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.haku.anya.data.Book
import com.haku.anya.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件夹扫描器
 * 用于扫描文件夹中的EPUB文件
 */
class FolderScanner(private val context: Context) {

    private val epubParser = EpubParser(context)

    /**
     * 扫描结果
     * @param progress 扫描进度（0-100）
     * @param currentFile 当前扫描的文件
     * @param foundBooks 已找到的书籍列表
     * @param isComplete 是否扫描完成
     */
    data class ScanResult(
        val progress: Int,
        val currentFile: String,
        val foundBooks: List<Book>,
        val isComplete: Boolean = false
    )

    /**
     * 扫描文件夹
     * @param folderPath 文件夹路径
     * @return 扫描结果流
     */
    fun scanFolder(folderPath: String): Flow<ScanResult> = flow {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            emit(ScanResult(100, "", emptyList(), true))
            return@flow
        }

        val allFiles = getAllEpubFiles(folder)
        val totalFiles = allFiles.size
        if (totalFiles == 0) {
            emit(ScanResult(100, "", emptyList(), true))
            return@flow
        }

        val foundBooks = mutableListOf<Book>()
        var processedFiles = 0

        for (file in allFiles) {
            val progress = ((processedFiles.toFloat() / totalFiles) * 100).toInt()
            emit(ScanResult(progress, file.name, foundBooks.toList()))

            try {
                val book = withContext(Dispatchers.IO) {
                    epubParser.parseEpub(file.absolutePath)
                }
                book?.let { foundBooks.add(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            processedFiles++
        }

        emit(ScanResult(100, "", foundBooks.toList(), true))
    }

    /**
     * 从URI扫描文件夹
     * @param folderUri 文件夹URI
     * @return 扫描结果流
     */
    fun scanFolderFromUri(folderUri: Uri): Flow<ScanResult> = flow {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: run {
                emit(ScanResult(100, "", emptyList(), true))
                return@flow
            }

        val allFiles = getAllEpubFilesFromDocumentFile(folder)
        val totalFiles = allFiles.size
        if (totalFiles == 0) {
            emit(ScanResult(100, "", emptyList(), true))
            return@flow
        }

        val foundBooks = mutableListOf<Book>()
        var processedFiles = 0

        for (file in allFiles) {
            val progress = ((processedFiles.toFloat() / totalFiles) * 100).toInt()
            emit(ScanResult(progress, file.name ?: "未知文件", foundBooks.toList()))

            try {
                val book = withContext(Dispatchers.IO) {
                    epubParser.parseEpubFromUri(file.uri)
                }
                book?.let { foundBooks.add(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            processedFiles++
        }

        emit(ScanResult(100, "", foundBooks.toList(), true))
    }

    /**
     * 获取所有EPUB文件
     * @param folder 文件夹
     * @return EPUB文件列表
     */
    private fun getAllEpubFiles(folder: File): List<File> {
        val result = mutableListOf<File>()
        val files = folder.listFiles() ?: return result

        for (file in files) {
            if (file.isDirectory) {
                result.addAll(getAllEpubFiles(file))
            } else if (file.name.endsWith(".epub", ignoreCase = true)) {
                result.add(file)
            }
        }

        return result
    }

    /**
     * 获取所有EPUB文件（从DocumentFile）
     * @param folder DocumentFile文件夹
     * @return DocumentFile列表
     */
    private fun getAllEpubFilesFromDocumentFile(folder: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val files = folder.listFiles() ?: return result

        for (file in files) {
            if (file.isDirectory) {
                result.addAll(getAllEpubFilesFromDocumentFile(file))
            } else if (file.name?.endsWith(".epub", ignoreCase = true) == true) {
                result.add(file)
            }
        }

        return result
    }
}