package com.haku.anya

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建一个简单的TextView来测试基本功能
        val textView = TextView(this)
        textView.text = "Anya 电子书阅读器\n\n应用启动成功！\n\n这是一个测试版本"
        textView.textSize = 18f
        textView.setPadding(50, 100, 50, 100)
        
        setContentView(textView)
    }
}
