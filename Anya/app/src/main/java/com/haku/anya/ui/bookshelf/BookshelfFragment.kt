package com.haku.anya.ui.bookshelf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
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
import com.haku.anya.ui.bookshelf.adapter.BooksAdapter
import com.haku.anya.ui.bookshelf.adapter.CategoriesAdapter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BookshelfFragment : Fragment() {
    
    private var _binding: FragmentBookshelfBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: BookshelfViewModel by viewModels()
    private lateinit var booksAdapter: BooksAdapter
    private lateinit var categoriesAdapter: CategoriesAdapter
    
    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showImportOptionsDialog()
        } else {
            Snackbar.make(binding.root, "需要存储权限来添加书籍", Snackbar.LENGTH_LONG).show()
        }
    }
    
    // 文件选择器
    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { addBookFromUri(it) }
    }
    
    // 文件夹选择器
    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 持久化访问权限
            uri?.let { 
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                scanFolderFromUri(it)
            }
        } else {
            uri?.let { scanFolderFromUri(it) }
        }
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
        
        // 观察扫描状态
        lifecycleScope.launch {
            viewModel.scanningState.collect { state ->
                when (state) {
                    is BookshelfViewModel.ScanningState.Idle -> {
                        hideProgressDialog()
                    }
                    is BookshelfViewModel.ScanningState.Scanning -> {
                        showProgressDialog(state.progress, state.foundCount)
                    }
                    is BookshelfViewModel.ScanningState.Completed -> {
                        hideProgressDialog()
                        Snackbar.make(
                            binding.root,
                            "扫描完成，共添加 ${state.totalFound} 本书籍",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    is BookshelfViewModel.ScanningState.Error -> {
                        hideProgressDialog()
                        Snackbar.make(
                            binding.root,
                            "扫描出错: ${state.message}",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.fabAddBook.setOnClickListener {
            checkPermissionAndShowImportOptions()
        }
        
        binding.fabAddCategory.setOnClickListener {
            showAddCategoryDialog()
        }
    }
    
    private fun checkPermissionAndShowImportOptions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用存储访问框架(SAF)，不需要请求权限
            showImportOptionsDialog()
        } else {
            // Android 10及以下版本请求READ_EXTERNAL_STORAGE权限
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    showImportOptionsDialog()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }
    
    private fun showImportOptionsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("导入电子书")
            .setItems(arrayOf("选择单个EPUB文件", "扫描文件夹")) { _, which ->
                when (which) {
                    0 -> openFilePicker()
                    1 -> openFolderPicker()
                }
            }
            .show()
    }
    
    private fun openFilePicker() {
        pickFileLauncher.launch("application/epub+zip")
    }
    
    private fun openFolderPicker() {
        pickFolderLauncher.launch(null)
    }
    
    private fun addBookFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                showProgressDialog(0, 1)
                val epubParser = EpubParser(requireContext())
                val book = epubParser.parseEpubFromUri(uri)
                hideProgressDialog()
                
                book?.let {
                    viewModel.addBook(it)
                    Snackbar.make(binding.root, "书籍添加成功", Snackbar.LENGTH_SHORT).show()
                } ?: run {
                    Snackbar.make(binding.root, "无法解析EPUB文件", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                hideProgressDialog()
                Snackbar.make(binding.root, "添加书籍失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun scanFolderFromUri(uri: Uri) {
        viewModel.scanFolderFromUri(uri)
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
    
    // 进度对话框
    private var progressDialog: androidx.appcompat.app.AlertDialog? = null
    
    private fun showProgressDialog(current: Int, total: Int) {
        if (progressDialog == null) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
            progressDialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()
            progressDialog?.show()
        }
        
        // 更新进度
        val progressBar = progressDialog?.findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val progressText = progressDialog?.findViewById<android.widget.TextView>(R.id.progressText)
        
        progressBar?.max = total
        progressBar?.progress = current
        progressText?.text = "正在扫描: $current / $total"
    }
    
    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}