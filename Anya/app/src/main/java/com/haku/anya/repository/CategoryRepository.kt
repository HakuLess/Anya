package com.haku.anya.repository

import com.haku.anya.data.Category
import com.haku.anya.data.CategoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CategoryRepository(private val categoryDao: CategoryDao) {
    
    fun getAllCategories() = categoryDao.getAllCategories()
    
    suspend fun getCategoryById(categoryId: Long): Category? = withContext(Dispatchers.IO) {
        categoryDao.getCategoryById(categoryId)
    }
    
    suspend fun insertCategory(category: Category): Long = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(category)
    }
    
    suspend fun updateCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.updateCategory(category)
    }
    
    suspend fun deleteCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.deleteCategory(category)
    }
    
    suspend fun updateSortOrder(categoryId: Long, sortOrder: Int) = withContext(Dispatchers.IO) {
        categoryDao.updateSortOrder(categoryId, sortOrder)
    }
}
