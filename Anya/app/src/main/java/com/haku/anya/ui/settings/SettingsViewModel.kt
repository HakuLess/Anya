package com.haku.anya.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {
    
    private val _darkMode = MutableLiveData<Boolean>(false)
    val darkMode: LiveData<Boolean> = _darkMode
    
    private val _autoScroll = MutableLiveData<Boolean>(false)
    val autoScroll: LiveData<Boolean> = _autoScroll
    
    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        // 这里可以保存到SharedPreferences
    }
    
    fun setAutoScroll(enabled: Boolean) {
        _autoScroll.value = enabled
        // 这里可以保存到SharedPreferences
    }
}
