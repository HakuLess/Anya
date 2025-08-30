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
import java.io.File

// TODO: 重新实现ReaderActivity，当前暂时禁用
class ReaderActivity : AppCompatActivity() {
    
    // private lateinit var binding: com.haku.anya.ui.reader.databinding.ActivityReaderBinding
    private lateinit var pageAdapter: PageAdapter
    private lateinit var epubParser: EpubParser
    private lateinit var bookRepository: BookRepository
    
    private var currentBook: Book? = null
    private var epubBook: Any? = null
    private var currentPage = 0
    private var totalPages = 0
    
    private lateinit var gestureDetector: GestureDetector
    private var isPageTurning = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // binding = com.haku.anya.ui.reader.databinding.ActivityReaderBinding.inflate(layoutInflater)
        // setContentView(binding.root)
        
        // setupViews()
        // loadBook()
        // setupGestureDetection()
    }
    
    // 暂时注释掉所有方法
    /*
    private fun setupViews() {
        // TODO: 重新实现
    }
    
    private fun loadBook() {
        // TODO: 重新实现
    }
    
    private fun loadPages() {
        // TODO: 重新实现
    }
    
    private fun getPageBitmap(book: Any, pageNumber: Int): Bitmap? {
        // TODO: 重新实现
        return null
    }
    
    private fun updatePageInfo() {
        // TODO: 重新实现
    }
    
    private fun saveReadingProgress() {
        // TODO: 重新实现
    }
    
    private fun setupGestureDetection() {
        // TODO: 重新实现
    }
    
    private fun showSettingsDialog() {
        // TODO: 重新实现
    }
    
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // TODO: 重新实现
        return super.onTouchEvent(event)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // TODO: 重新实现
    }
    */
}
