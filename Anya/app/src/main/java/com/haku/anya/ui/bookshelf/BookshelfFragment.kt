package com.haku.anya.ui.bookshelf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.haku.anya.R
import com.haku.anya.databinding.FragmentBookshelfBinding
import com.haku.anya.epub.EpubParser
import com.haku.anya.ui.reader.ReaderActivity
import kotlinx.coroutines.launch

class BookshelfFragment : Fragment() {
    
    private var _binding: FragmentBookshelfBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: BookshelfViewModel by viewModels()
    private lateinit var booksAdapter: BooksAdapter
    private lateinit var categoriesAdapter: CategoriesAdapter
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openFilePicker()
        } else {
            Snackbar.make(binding.root, "需要存储权限来添加书籍", Snackbar.LENGTH_LONG).show()
        }
    }
    
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { addBookFromUri(it) }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookshelfBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerViews()
        setupObservers()
        setupClickListeners()
        
        viewModel.loadBooks()
        viewModel.loadCategories()
    }
    
    private fun setupRecyclerViews() {
        // 设置分类RecyclerView
        categoriesAdapter = CategoriesAdapter { category ->
            viewModel.setCurrentCategory(category.id)
        }
        binding.categoriesRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = categoriesAdapter
        }
        
        // 设置书籍RecyclerView
        booksAdapter = BooksAdapter(
            onBookClick = { book ->
                openBook(book)
            },
            onBookLongClick = { book ->
                showBookOptionsDialog(book)
            }
        )
        binding.booksRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = booksAdapter
        }
    }
    
    private fun setupObservers() {
        viewModel.books.observe(viewLifecycleOwner) { books ->
            booksAdapter.submitList(books)
            binding.emptyState.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE
        }
        
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            categoriesAdapter.submitList(categories)
        }
        
        viewModel.currentCategory.observe(viewLifecycleOwner) { categoryId ->
            viewModel.loadBooksByCategory(categoryId)
        }
    }
    
    private fun setupClickListeners() {
        binding.fabAddBook.setOnClickListener {
            checkPermissionAndOpenFilePicker()
        }
        
        binding.fabAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }
    }
    
    private fun checkPermissionAndOpenFilePicker() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                openFilePicker()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun openFilePicker() {
        pickFileLauncher.launch("*/*")
    }
    
    private fun addBookFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                // 这里需要将URI转换为文件路径，实际应用中可能需要更复杂的处理
                val filePath = uri.path ?: return@launch
                if (filePath.endsWith(".epub", true)) {
                    val epubParser = EpubParser(requireContext())
                    val book = epubParser.parseEpub(filePath)
                    book?.let {
                        viewModel.addBook(it)
                        Snackbar.make(binding.root, "书籍添加成功", Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    Snackbar.make(binding.root, "只支持EPUB格式", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "添加书籍失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun openBook(book: com.haku.anya.data.Book) {
        val intent = Intent(requireContext(), ReaderActivity::class.java).apply {
            putExtra("book_id", book.id)
        }
        startActivity(intent)
    }
    
    private fun showBookOptionsDialog(book: com.haku.anya.data.Book) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(book.title)
            .setItems(arrayOf("编辑", "删除", "收藏")) { _, which ->
                when (which) {
                    0 -> showEditBookDialog(book)
                    1 -> showDeleteBookDialog(book)
                    2 -> toggleFavorite(book)
                }
            }
            .show()
    }
    
    private fun showEditBookDialog(book: com.haku.anya.data.Book) {
        // 实现编辑书籍对话框
        Snackbar.make(binding.root, "编辑功能开发中", Snackbar.LENGTH_SHORT).show()
    }
    
    private fun showDeleteBookDialog(book: com.haku.anya.data.Book) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除书籍")
            .setMessage("确定要删除《${book.title}》吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteBook(book)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun toggleFavorite(book: com.haku.anya.data.Book) {
        viewModel.toggleFavorite(book)
    }
    
    private fun showAddCategoryDialog() {
        // 实现添加分类对话框
        Snackbar.make(binding.root, "添加分类功能开发中", Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
