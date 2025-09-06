package com.haku.anya.ui.bookshelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.haku.anya.data.AppDatabase
import com.haku.anya.data.Book
import com.haku.anya.data.Category
import com.haku.anya.repository.BookRepository
import com.haku.anya.repository.CategoryRepository
import com.haku.anya.util.FolderScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BookshelfViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bookRepository: BookRepository
    private val categoryRepository: CategoryRepository
    private val folderScanner: FolderScanner
    
    private val _books = MutableLiveData<List<Book>>(emptyList())
    val books: LiveData<List<Book>> = _books
    
    private val _categories = MutableLiveData<List<Category>>(emptyList())
    val categories: LiveData<List<Category>> = _categories
    
    private val _currentCategory = MutableLiveData<Long>(1L)
    val currentCategory: LiveData<Long> = _currentCategory
    
    // 扫描状态
    private val _scanningState = MutableStateFlow<ScanningState>(ScanningState.Idle)
    val scanningState: StateFlow<ScanningState> = _scanningState
    
    init {
        val database = AppDatabase.getDatabase(application)
        bookRepository = BookRepository(database.bookDao())
        categoryRepository = CategoryRepository(database.categoryDao())
        folderScanner = FolderScanner(application.applicationContext)
    }
    
    fun loadBooks() {
        viewModelScope.launch {
            bookRepository.getAllBooks().observeForever { books ->
                _books.value = books
            }
        }
    }
    
    fun loadBooksByCategory(categoryId: Long) {
        viewModelScope.launch {
            bookRepository.getBooksByCategory(categoryId).observeForever { books ->
                _books.value = books
            }
        }
    }
    
    fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().observeForever { categories ->
                _categories.value = categories
            }
        }
    }
    
    fun setCurrentCategory(categoryId: Long) {
        _currentCategory.value = categoryId
    }
    
    fun addBook(book: Book) {
        viewModelScope.launch {
            bookRepository.insertBook(book)
        }
    }
    
    fun deleteBook(book: Book) {
        viewModelScope.launch {
            bookRepository.deleteBook(book)
        }
    }
    
    fun toggleFavorite(book: Book) {
        viewModelScope.launch {
            bookRepository.updateFavoriteStatus(book.id, !book.isFavorite)
        }
    }
    
    fun addCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.insertCategory(category)
        }
    }
    
    fun updateCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.updateCategory(category)
        }
    }
    
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
        }
    }
    
    /**
     * 扫描文件夹
     * @param folderPath 文件夹路径
     */
    fun scanFolder(folderPath: String) {
        viewModelScope.launch {
            folderScanner.scanFolder(folderPath).collect { result ->
                _scanningState.value = ScanningState.Scanning(
                    result.progress,
                    result.foundBooks.size
                )
                
                // 添加找到的书籍
                result.foundBooks.forEach { book ->
                    addBook(book)
                }
                
                if (result.isComplete) {
                    _scanningState.value = ScanningState.Completed(result.foundBooks.size)
                }
            }
        }
    }
    
    /**
     * 扫描文件夹（通过URI）
     * @param folderUri 文件夹URI
     */
    fun scanFolderFromUri(folderUri: Uri) {
        viewModelScope.launch {
            folderScanner.scanFolderFromUri(folderUri).collect { result ->
                _scanningState.value = ScanningState.Scanning(
                    result.progress,
                    result.foundBooks.size
                )
                
                // 添加找到的书籍
                result.foundBooks.forEach { book ->
                    addBook(book)
                }
                
                if (result.isComplete) {
                    _scanningState.value = ScanningState.Completed(result.foundBooks.size)
                }
            }
        }
    }
    
    /**
     * 扫描状态密封类
     */
    sealed class ScanningState {
        /**
         * 空闲状态
         */
        object Idle : ScanningState()
        
        /**
         * 扫描中
         * @param progress 进度百分比
         * @param foundCount 已找到的书籍数量
         */
        data class Scanning(val progress: Int, val foundCount: Int) : ScanningState()
        
        /**
         * 扫描完成
         * @param totalFound 总找到的书籍数量
         */
        data class Completed(val totalFound: Int) : ScanningState()
        
        /**
         * 扫描错误
         * @param message 错误信息
         */
        data class Error(val message: String) : ScanningState()
    }
}