package com.haku.anya.ui.reader

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.haku.anya.data.AppDatabase
import com.haku.anya.data.Book
import com.haku.anya.databinding.ActivityReaderBinding
import com.haku.anya.databinding.ItemPageBinding
import com.haku.anya.epub.EpubParser
import com.haku.anya.repository.BookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ReaderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReaderBinding
    private lateinit var epubParser: EpubParser
    private var currentBook: Book? = null
    private val pages = mutableListOf<PageContent>()

    // 页面内容数据类
    data class PageContent(
        val type: String, // "text" or "image"
        val content: String, // HTML内容或图片路径
        val pageNum: Int,
        val originalOrder: Int, // 原始EPUB中的顺序
        val extractedPageNumber: Int, // 从标题中提取的页码
        val isFirstPage: Boolean,
        val isLastPage: Boolean,
        val title: String? // 页面标题(如果有)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化
        val bookId = intent.getLongExtra("book_id", -1)
        if (bookId == -1L) finish()

        epubParser = EpubParser(this)
        val db = AppDatabase.getDatabase(applicationContext)
        val bookRepository = BookRepository(db.bookDao())

        // 加载书籍和页面
        CoroutineScope(Dispatchers.Main).launch {
            currentBook = bookRepository.getBookById(bookId)
            currentBook?.let { book ->
                loadBookPages(book.filePath)
                setupViewPager()
            }
        }
    }

    private suspend fun loadBookPages(filePath: String) {
        pages.clear()
        
        // 1. 提取所有EPUB资源
        val resourceDir = epubParser.extractEpubResources(filePath)
        if (resourceDir.isEmpty()) {
            Log.e("ReaderActivity", "Failed to extract EPUB resources")
            return
        }

        // 2. 解析EPUB文件结构(已按index排序)
        val epubStructure = epubParser.parseEpubStructure(filePath)
        
        // 3. 先收集HTML目录下的所有HTML/XHTML页面
        val tempPages = mutableListOf<PageContent>()
        
        // 存储封面页面内容
        var coverContent: String? = null
        var coverPath: String? = null
        
        // 尝试获取封面图片路径
        val opfPath = epubParser.getOpfPath(filePath)
        val originalCoverPath = epubParser.extractCoverImage(filePath, opfPath)
        if (originalCoverPath.isNotEmpty()) {
            coverPath = originalCoverPath
        }
        
        epubStructure.forEachIndexed { index, entry ->
            try {
                // 只处理HTML目录下的HTML/XHTML文件
                if ((entry.path.endsWith(".html") || entry.path.endsWith(".xhtml")) && 
                    entry.path.contains("/html/") || entry.path.startsWith("html/")) {
                    
                    val content = epubParser.getEntryContent(filePath, entry.path)
                    val title = epubParser.getPageTitle(content)
                    val extractedPageNum = epubParser.extractPageNumber(title)
                    
                    // 尝试从文件名中提取数字作为备选排序依据
                    val fileNameNumber = extractNumberFromFileName(entry.path)
                    
                    tempPages.add(PageContent(
                        type = "text",
                        content = content,
                        pageNum = index + 1, // 临时页码，后续会重新排序
                        originalOrder = entry.index, // 保留原始EPUB条目index
                        extractedPageNumber = if (extractedPageNum > 0) extractedPageNum else fileNameNumber,
                        isFirstPage = false, // 暂时设为false，排序后重新计算
                        isLastPage = false, // 暂时设为false，排序后重新计算
                        title = title
                    ))
                } else if (entry.isImage && coverPath != null && 
                           (entry.path == coverPath || entry.path.contains(coverPath))) {
                    // 如果是封面图片，保存但不添加到普通页面列表
                    val imageFile = File(resourceDir, entry.path)
                    if (imageFile.exists()) {
                        val coverHtml = generateCoverHtml(imageFile.absolutePath)
                        coverContent = coverHtml
                    }
                }
            } catch (e: Exception) {
                Log.e("ReaderActivity", "Error loading entry: ${entry.path}", e)
            }
        }
        
        // 4. 按提取的页码排序，如果页码无法提取则按原始顺序
        val sortedPages = tempPages.sortedWith(compareBy({
            // 对于有有效页码的页面，按页码排序
            if (it.extractedPageNumber > 0) it.extractedPageNumber else Int.MAX_VALUE
        }, {
            // 对于没有有效页码的页面，按原始顺序排序
            it.originalOrder
        }))
        
        // 5. 重新计算页码和是否为第一页/最后一页，并添加封面(如果有)
        if (coverContent != null) {
            // 添加封面作为首页
            pages.add(PageContent(
                type = "text",
                content = coverContent!!,
                pageNum = 1,
                originalOrder = -1, // 封面不是原始EPUB中的页面
                extractedPageNumber = 0, // 封面页码为0
                isFirstPage = true,
                isLastPage = sortedPages.isEmpty(),
                title = "cover"
            ))
        }
        
        // 添加排序后的HTML页面
        sortedPages.forEachIndexed { index, page ->
            val pageIndex = if (coverContent != null) index + 2 else index + 1
            pages.add(page.copy(
                pageNum = pageIndex,
                isFirstPage = coverContent == null && index == 0,
                isLastPage = index == sortedPages.size - 1
            ))
        }
        
        // 6. 确保书籍对象中的总页数与实际加载的页数一致
        currentBook?.let {
            if (pages.size > 0 && it.totalPages != pages.size) {
                // 更新书籍的总页数
                val db = AppDatabase.getDatabase(applicationContext)
                val bookRepository = BookRepository(db.bookDao())
                val updatedBook = it.copy(totalPages = pages.size)
                bookRepository.updateBook(updatedBook)
                currentBook = updatedBook
            }
        }
    }
    
    /**
     * 从文件名中提取数字
     */
    private fun extractNumberFromFileName(filePath: String): Int {
        try {
            // 匹配文件名中的数字序列
            val numberRegex = Regex("(\\d+)")
            val match = numberRegex.find(filePath)
            return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            Log.e("ReaderActivity", "Failed to extract number from file name: $filePath", e)
            return -1
        }
    }
    
    /**
     * 生成显示封面图片的HTML内容
     */
    private fun generateCoverHtml(imagePath: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>cover</title>
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    height: 100vh;
                    background-color: #f5f5f5;
                }
                img {
                    max-width: 100%;
                    max-height: 100%;
                    object-fit: contain;
                }
            </style>
        </head>
        <body>
            <img src="file://$imagePath" alt="封面">
        </body>
        </html>
        """
    }

    private fun setupViewPager() {
        // 预先计算资源目录路径
        val resourceDir = currentBook?.filePath?.let { 
            File(getExternalFilesDir(null), 
                "epub_resources/${File(it).nameWithoutExtension}").absolutePath
        } ?: ""
        
        binding.viewPager.adapter = PageAdapter(resourceDir)
        binding.viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        
        // 页面切换回调
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator(position)
            }
        })
        
        // 初始页面指示器
        updatePageIndicator(0)
    }

    private fun updatePageIndicator(currentPosition: Int) {
        val page = pages.getOrNull(currentPosition)
        binding.pageIndicator.text = buildString {
            append("${currentPosition + 1}/${pages.size}")
            page?.title?.takeIf { it.isNotEmpty() }?.let { 
                append(" - $it")
            }
        }
    }

    // 页面适配器
    inner class PageAdapter(private val resourceDir: String) : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {
        inner class PageViewHolder(val binding: ItemPageBinding) : 
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val binding = ItemPageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return PageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val page = pages[position]
            when (page.type) {
                "text" -> {
                    holder.binding.webView.visibility = View.VISIBLE
                    holder.binding.imageView.visibility = View.GONE
                    
                    // 配置WebView
                    holder.binding.webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                    }
                    
                    // 添加详细日志记录资源目录
                    Log.d("WebViewConfig", "Resource directory: $resourceDir")
                    Log.d("WebViewConfig", "Page content length: ${page.content.length}")

                    // 设置自定义WebViewClient处理资源加载
                    holder.binding.webView.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val url = request.url.toString()
                            Log.d("WebView", "Loading resource: $url")
                            
                            try {
                                // 处理相对路径资源
                                if (url.startsWith("../")) {
                                    val epubDir = currentBook?.filePath?.let { 
                                        File(it).parentFile?.absolutePath 
                                    } ?: ""
                                    val fullPath = "file://$epubDir/${url.substringAfter("../")}"
                                    Log.d("WebView", "Resolved relative path to: $fullPath")
                                    
                                    // 检查文件是否存在
                                    val file = File(fullPath.removePrefix("file://"))
                                    if (file.exists()) {
                                        Log.d("WebView", "File exists: ${file.absolutePath}")
                                        return WebResourceResponse(
                                            getMimeType(file.name),
                                            "UTF-8",
                                            file.inputStream()
                                        )
                                    } else {
                                        Log.e("WebView", "File not found: ${file.absolutePath}")
                                    }
                                }
                                
                                // 处理绝对路径资源
                                if (url.startsWith("file://")) {
                                    val file = File(url.removePrefix("file://"))
                                    if (file.exists()) {
                                        Log.d("WebView", "Loading file: ${file.absolutePath}")
                                        return WebResourceResponse(
                                            getMimeType(file.name),
                                            "UTF-8",
                                            file.inputStream()
                                        )
                                    } else {
                                        Log.e("WebView", "File not found: ${file.absolutePath}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("WebView", "Error loading resource: $url", e)
                            }
                            
                            return super.shouldInterceptRequest(view, request)
                        }
                        
                        private fun getMimeType(fileName: String): String {
                            val ext = fileName.substringAfterLast('.').lowercase()
                            return when (ext) {
                                "jpg", "jpeg" -> "image/jpeg"
                                "png" -> "image/png" 
                                "gif" -> "image/gif"
                                "css" -> "text/css"
                                "js" -> "application/javascript"
                                "html", "htm", "xhtml" -> "text/html"
                                else -> {
                                    Log.w("MimeType", "Unknown file extension: $ext")
                                    "*/*"
                                }
                            }
                        }
                    }

                    // 使用预先传入的资源目录路径
                    try {
                        // 确保路径正确编码
                        // 确保路径包含完整的EPUB文件夹名称
                    val epubFolderName = currentBook?.filePath?.let { 
                        File(it).nameWithoutExtension 
                    } ?: ""
                    val fullResourceDir = "$resourceDir/$epubFolderName"
                    val encodedPath = "file://${fullResourceDir.replace(" ", "%20")}/"
                    Log.d("PathFix", "Full resource path: $encodedPath")
                        Log.d("WebViewLoad", "Loading with baseURL: $encodedPath")
                        
                        holder.binding.webView.loadDataWithBaseURL(
                            encodedPath,
                            page.content,
                            "text/html", 
                            "UTF-8",
                            null
                        )
                    } catch (e: Exception) {
                        Log.e("WebViewLoad", "Failed to load content", e)
                        holder.binding.webView.loadData(
                            "<html><body>加载失败: ${e.localizedMessage}</body></html>",
                            "text/html",
                            "UTF-8"
                        )
                    }
                    
                    Log.d("ReaderActivity", "WebView baseURL: file://$resourceDir/")
                }
                "image" -> {
                    holder.binding.webView.visibility = View.GONE
                    holder.binding.imageView.visibility = View.VISIBLE
                    val imageFile = File(page.content)
                    if (imageFile.exists()) {
                        Log.d("ReaderActivity", "Loading image from: ${imageFile.absolutePath}")
                        Glide.with(this@ReaderActivity)
                            .load(imageFile)
                            .into(holder.binding.imageView)
                    } else {
                        Log.e("ReaderActivity", "Image file not found: ${imageFile.absolutePath}")
                    }
                }
            }
        }

        override fun getItemCount() = pages.size
    }
}