package com.haku.anya.ui.bookshelf.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.haku.anya.R
import com.haku.anya.data.Book
import java.io.File

class BooksAdapter(
    private val onBookClick: (Book) -> Unit,
    private val onBookLongClick: (Book) -> Unit
) : ListAdapter<Book, BooksAdapter.BookViewHolder>(BookDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.book_cover)
        private val titleText: TextView = itemView.findViewById(R.id.book_title)
        private val authorText: TextView = itemView.findViewById(R.id.book_author)
        private val progressText: TextView = itemView.findViewById(R.id.book_progress)
        private val favoriteIcon: ImageView = itemView.findViewById(R.id.favorite_icon)
        
        fun bind(book: Book) {
            titleText.text = book.title
            authorText.text = book.author
            
            // 显示阅读进度
            if (book.totalPages > 0) {
                val progress = (book.lastReadPage.toFloat() / book.totalPages * 100).toInt()
                progressText.text = "$progress%"
            } else {
                progressText.text = "0%"
            }
            
            // 显示收藏状态
            favoriteIcon.visibility = if (book.isFavorite) View.VISIBLE else View.GONE
            
            // 加载封面图片
            if (book.coverPath.isNotEmpty() && File(book.coverPath).exists()) {
                Glide.with(itemView.context)
                    .load(book.coverPath)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder)
                    .into(coverImage)
            } else {
                coverImage.setImageResource(R.drawable.ic_book_placeholder)
            }
            
            // 设置点击事件
            itemView.setOnClickListener {
                onBookClick(book)
            }
            
            itemView.setOnLongClickListener {
                onBookLongClick(book)
                true
            }
        }
    }
    
    private class BookDiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem == newItem
        }
    }
}
