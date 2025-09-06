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
        
        // 3. 按顺序加载内容
        epubStructure.forEachIndexed { index, entry ->
            try {
                when {
                    entry.path.endsWith(".html") || entry.path.endsWith(".xhtml") -> {
                        val content = epubParser.getEntryContent(filePath, entry.path)
                        pages.add(PageContent(
                            type = "text",
                            content = content,
                            pageNum = index + 1,
                            originalOrder = entry.index, // 使用EPUB条目原始index
                            isFirstPage = index == 0,
                            isLastPage = index == epubStructure.size - 1,
                            title = epubParser.getPageTitle(content)
                        ))
                    }
                    entry.isImage -> {
                        val imageFile = File(resourceDir, entry.path)
                        if (imageFile.exists()) {
                            pages.add(PageContent(
                            type = "image", 
                            content = imageFile.absolutePath,
                            pageNum = index + 1,
                            originalOrder = index,
                            isFirstPage = index == 0,
                            isLastPage = index == epubStructure.size - 1,
                            title = File(entry.path).nameWithoutExtension
                        ))
                        } else {
                            Log.e("ReaderActivity", "Image file not found: ${imageFile.absolutePath}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ReaderActivity", "Error loading entry: ${entry.path}", e)
            }
        }
        
        // 4. 按pageNum排序确保顺序正确
        pages.sortBy { it.pageNum }
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