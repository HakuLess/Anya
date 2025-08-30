package com.haku.anya.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haku.anya.data.Book
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    
    private val _searchResults = MutableLiveData<List<Book>>()
    val searchResults: LiveData<List<Book>> = _searchResults
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    fun searchBooks(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 这里实现实际的搜索逻辑
                // 暂时返回空列表
                _searchResults.value = emptyList()
            } catch (e: Exception) {
                // 处理错误
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
