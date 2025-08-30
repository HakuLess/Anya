package com.haku.anya.repository

import com.haku.anya.data.Book
import com.haku.anya.data.BookDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookRepository(private val bookDao: BookDao) {
    
    fun getAllBooks() = bookDao.getAllBooks()
    
    fun getBooksByCategory(categoryId: Long) = bookDao.getBooksByCategory(categoryId)
    
    fun getFavoriteBooks() = bookDao.getFavoriteBooks()
    
    suspend fun getBookById(bookId: Long): Book? = withContext(Dispatchers.IO) {
        bookDao.getBookById(bookId)
    }
    
    suspend fun insertBook(book: Book): Long = withContext(Dispatchers.IO) {
        bookDao.insertBook(book)
    }
    
    suspend fun updateBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.updateBook(book)
    }
    
    suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.deleteBook(book)
    }
    
    suspend fun updateReadingProgress(bookId: Long, page: Int) = withContext(Dispatchers.IO) {
        bookDao.updateReadingProgress(bookId, page)
    }
    
    suspend fun updateFavoriteStatus(bookId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        bookDao.updateFavoriteStatus(bookId, isFavorite)
    }
}
