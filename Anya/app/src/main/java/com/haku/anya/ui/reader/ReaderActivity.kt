package com.haku.anya.ui.reader

import android.graphics.Bitmap
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.haku.anya.data.AppDatabase
import com.haku.anya.data.Book
import com.haku.anya.epub.EpubParser
import com.haku.anya.repository.BookRepository
import com.haku.anya.ui.reader.adapter.PageAdapter
import com.haku.anya.ui.reader.animation.PageTurnAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.epub.EpubReader
import java.io.File

class ReaderActivity : AppCompatActivity() {
    
    private lateinit var binding: com.haku.anya.ui.reader.databinding.ActivityReaderBinding
    private lateinit var pageAdapter: PageAdapter
    private lateinit var epubParser: EpubParser
    private lateinit var bookRepository: BookRepository
    
    private var currentBook: Book? = null
    private var epubBook: nl.siegmann.epublib.domain.Book? = null
    private var currentPage = 0
    private var totalPages = 0
    
    private lateinit var gestureDetector: GestureDetector
    private var isPageTurning = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = com.haku.anya.ui.reader.databinding.ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        loadBook()
        setupGestureDetection()
    }
    
    private fun setupViews() {
        // 设置ViewPager2
        pageAdapter = PageAdapter()
        binding.viewPager.adapter = pageAdapter
        
        // 设置页面切换监听
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updatePageInfo()
                saveReadingProgress()
            }
        })
        
        // 设置工具栏点击事件
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        binding.btnPrevious.setOnClickListener {
            if (currentPage > 0) {
                binding.viewPager.currentItem = currentPage - 1
            }
        }
        
        binding.btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                binding.viewPager.currentItem = currentPage + 1
            }
        }
        
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }
    
    private fun loadBook() {
        val bookId = intent.getLongExtra("book_id", -1)
        if (bookId == -1) {
            finish()
            return
        }
        
        lifecycleScope.launch {
            try {
                val database = AppDatabase.getDatabase(this@ReaderActivity)
                bookRepository = BookRepository(database.bookDao())
                
                currentBook = bookRepository.getBookById(bookId)
                currentBook?.let { book ->
                    // 加载EPUB文件
                    epubBook = EpubReader().readEpub(File(book.filePath))
                    totalPages = epubBook?.resources?.all?.values?.count { resource ->
                        resource.mediaType == nl.siegmann.epublib.domain.MediaType.PNG ||
                        resource.mediaType == nl.siegmann.epublib.domain.MediaType.JPG ||
                        resource.mediaType == nl.siegmann.epublib.domain.MediaType.JPEG
                    } ?: 0
                    
                    // 设置当前页
                    currentPage = book.lastReadPage.coerceAtMost(totalPages - 1)
                    
                    // 加载页面
                    loadPages()
                    
                    // 更新UI
                    updatePageInfo()
                    binding.viewPager.currentItem = currentPage
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 显示错误信息
            }
        }
    }
    
    private fun loadPages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pages = mutableListOf<Bitmap>()
            
            epubBook?.let { book ->
                for (i in 0 until totalPages) {
                    val bitmap = getPageBitmap(book, i)
                    bitmap?.let { pages.add(it) }
                }
            }
            
            withContext(Dispatchers.Main) {
                pageAdapter.submitList(pages)
            }
        }
    }
    
    private fun getPageBitmap(epubBook: nl.siegmann.epublib.domain.Book, pageNumber: Int): Bitmap? {
        return try {
            val imageResources = epubBook.resources.all.values.filter { resource ->
                resource.mediaType == nl.siegmann.epublib.domain.MediaType.PNG ||
                resource.mediaType == nl.siegmann.epublib.domain.MediaType.JPG ||
                resource.mediaType == nl.siegmann.epublib.domain.MediaType.JPEG
            }.sortedBy { it.href }
            
            if (pageNumber < imageResources.size) {
                val resource = imageResources[pageNumber]
                val inputStream = resource.inputStream
                android.graphics.BitmapFactory.decodeStream(inputStream).also {
                    inputStream.close()
                }
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun setupGestureDetection() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (isPageTurning) return false
                
                val diffX = e2.x - (e1?.x ?: 0f)
                val diffY = e2.y - (e1?.y ?: 0f)
                
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && 
                    kotlin.math.abs(diffX) > 100) {
                    if (diffX > 0 && currentPage > 0) {
                        // 向右滑动，上一页
                        turnPage(false)
                        return true
                    } else if (diffX < 0 && currentPage < totalPages - 1) {
                        // 向左滑动，下一页
                        turnPage(true)
                        return true
                    }
                }
                return false
            }
        })
        
        binding.viewPager.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }
    
    private fun turnPage(isNext: Boolean) {
        if (isPageTurning) return
        
        isPageTurning = true
        
        val animation = if (isNext) {
            PageTurnAnimation.createNextPageAnimation()
        } else {
            PageTurnAnimation.createPreviousPageAnimation()
        }
        
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                if (isNext && currentPage < totalPages - 1) {
                    binding.viewPager.currentItem = currentPage + 1
                } else if (!isNext && currentPage > 0) {
                    binding.viewPager.currentItem = currentPage - 1
                }
                isPageTurning = false
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        
        binding.viewPager.startAnimation(animation)
    }
    
    private fun updatePageInfo() {
        binding.tvPageInfo.text = "${currentPage + 1} / $totalPages"
        binding.tvBookTitle.text = currentBook?.title ?: ""
    }
    
    private fun saveReadingProgress() {
        currentBook?.let { book ->
            lifecycleScope.launch {
                bookRepository.updateReadingProgress(book.id, currentPage)
            }
        }
    }
    
    private fun showSettingsDialog() {
        // 实现阅读设置对话框
        // 包括字体大小、背景颜色、翻页模式等
    }
    
    override fun onDestroy() {
        super.onDestroy()
        saveReadingProgress()
    }
}
