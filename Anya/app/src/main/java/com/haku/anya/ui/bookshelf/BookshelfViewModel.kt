package com.haku.anya.ui.bookshelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.haku.anya.data.AppDatabase
import com.haku.anya.data.Book
import com.haku.anya.data.Category
import com.haku.anya.repository.BookRepository
import com.haku.anya.repository.CategoryRepository
import kotlinx.coroutines.launch

class BookshelfViewModel(application: Application) : AndroidViewModel(application) {
    
    private val bookRepository: BookRepository
    private val categoryRepository: CategoryRepository
    
    private val _books = MutableLiveData<List<Book>>()
    val books: LiveData<List<Book>> = _books
    
    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories
    
    private val _currentCategory = MutableLiveData<Long>(1L)
    val currentCategory: LiveData<Long> = _currentCategory
    
    init {
        val database = AppDatabase.getDatabase(application)
        bookRepository = BookRepository(database.bookDao())
        categoryRepository = CategoryRepository(database.categoryDao())
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
}
