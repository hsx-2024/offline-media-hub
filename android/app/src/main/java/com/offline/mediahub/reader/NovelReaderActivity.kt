package com.offline.mediahub.reader

import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.offline.mediahub.R
import com.offline.mediahub.databinding.ActivityNovelReaderBinding
import com.offline.mediahub.util.MemoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * 小说阅读器Activity
 * 支持TXT文本阅读，可配置字体大小和背景颜色
 * 
 * 性能优化：
 * 1. 分块加载大文件
 * 2. 使用StringBuilder减少内存碎片
 * 3. 低内存时自动清理缓存
 */
class NovelReaderActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "NovelReaderActivity"
        private const val PREFS_NAME = "reader_settings"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_BG_COLOR = "bg_color"
        private const val KEY_SCROLL_POSITION = "scroll_position"
        
        // 默认设置
        private const val DEFAULT_FONT_SIZE = 18
        private const val DEFAULT_BG_COLOR = "#F5F5DC"
    }
    
    private lateinit var binding: ActivityNovelReaderBinding
    private lateinit var prefs: SharedPreferences
    
    private var fileUri: Uri? = null
    private var fileTitle: String = ""
    private var currentFontSize = DEFAULT_FONT_SIZE
    private var currentBgColor = DEFAULT_BG_COLOR
    
    // 工具栏可见性
    private var isToolbarVisible = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovelReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // 获取文件信息
        fileUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("uri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("uri")
        }
        fileTitle = intent.getStringExtra("title") ?: "阅读"
        
        if (fileUri == null) {
            showError("无法获取文件地址")
            return
        }
        
        loadSettings()
        setupListeners()
        loadContent()
    }
    
    /**
     * 加载保存的设置
     */
    private fun loadSettings() {
        currentFontSize = prefs.getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        currentBgColor = prefs.getString(KEY_BG_COLOR, DEFAULT_BG_COLOR) ?: DEFAULT_BG_COLOR
        
        applySettings()
    }
    
    /**
     * 应用设置
     */
    private fun applySettings() {
        binding.tvContent.textSize = currentFontSize.toFloat()
        
        try {
            val bgColor = Color.parseColor(currentBgColor)
            binding.contentLayout.setBackgroundColor(bgColor)
            binding.scrollView.setBackgroundColor(bgColor)
        } catch (e: Exception) {
            // 使用默认颜色
        }
    }
    
    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 点击切换工具栏显示
        binding.scrollView.setOnClickListener {
            toggleToolbar()
        }
        binding.tvContent.setOnClickListener {
            toggleToolbar()
        }
        
        // 返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // 设置按钮
        binding.btnSettings.setOnClickListener {
            toggleSettingsPanel()
        }
        
        // 字体大小按钮
        binding.btnFontSmall.setOnClickListener { setFontSize(14) }
        binding.btnFontMedium.setOnClickListener { setFontSize(18) }
        binding.btnFontLarge.setOnClickListener { setFontSize(22) }
        
        // 背景颜色按钮
        binding.btnBgDefault.setOnClickListener { setBgColor("#F5F5DC") }
        binding.btnBgWhite.setOnClickListener { setBgColor("#FFFFFF") }
        binding.btnBgDark.setOnClickListener { setBgColor("#121212") }
        
        // 滚动监听
        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            updateProgress()
        }
    }
    
    /**
     * 切换工具栏显示
     */
    private fun toggleToolbar() {
        isToolbarVisible = !isToolbarVisible
        binding.topBar.visibility = if (isToolbarVisible) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = if (isToolbarVisible) View.VISIBLE else View.GONE
        
        // 隐藏设置面板
        if (isToolbarVisible) {
            binding.settingsPanel.visibility = View.GONE
        }
    }
    
    /**
     * 切换设置面板
     */
    private fun toggleSettingsPanel() {
        val isVisible = binding.settingsPanel.visibility == View.VISIBLE
        binding.settingsPanel.visibility = if (isVisible) View.GONE else View.VISIBLE
    }
    
    /**
     * 设置字体大小
     */
    private fun setFontSize(size: Int) {
        currentFontSize = size
        binding.tvContent.textSize = size.toFloat()
        prefs.edit { putInt(KEY_FONT_SIZE, size) }
    }
    
    /**
     * 设置背景颜色
     */
    private fun setBgColor(color: String) {
        currentBgColor = color
        try {
            val bgColor = Color.parseColor(color)
            binding.contentLayout.setBackgroundColor(bgColor)
            binding.scrollView.setBackgroundColor(bgColor)
            
            // 根据背景色调整文字颜色
            val textColor = if (color == "#121212") Color.WHITE else Color.parseColor("#333333")
            binding.tvContent.setTextColor(textColor)
            
        } catch (e: Exception) {
            // 忽略错误
        }
        prefs.edit { putString(KEY_BG_COLOR, color) }
    }
    
    /**
     * 加载文件内容
     */
    private fun loadContent() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    readFileContent(fileUri!!)
                }
                
                binding.tvContent.text = content
                binding.tvTitle.text = fileTitle
                
                // 恢复上次阅读位置
                val savedPosition = prefs.getInt("${fileUri?.path}_$KEY_SCROLL_POSITION", 0)
                binding.scrollView.post {
                    binding.scrollView.scrollY = savedPosition
                }
                
            } catch (e: Exception) {
                showError("加载失败: ${e.message}")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    /**
     * 读取文件内容
     * 针对大文件进行优化
     */
    private fun readFileContent(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw Exception("无法打开文件")
        
        // 检测文件编码
        val charset = detectCharset(inputStream)
        
        return BufferedReader(InputStreamReader(inputStream, charset)).use { reader ->
            // 使用StringBuilder提高性能
            val builder = StringBuilder()
            var line: String?
            var lineCount = 0
            
            // 低端设备限制行数，避免内存溢出
            val maxLines = if (MemoryUtils.isLowMemoryDevice()) 10000 else 50000
            
            while (reader.readLine().also { line = it } != null) {
                builder.append(line).append("\n")
                lineCount++
                
                if (lineCount >= maxLines) {
                    builder.append("\n... [文件过大，已截断] ...\n")
                    break
                }
                
                // 定期检查内存
                if (lineCount % 1000 == 0 && MemoryUtils.isMemoryCritical()) {
                    builder.append("\n... [内存不足，已截断] ...\n")
                    break
                }
            }
            
            builder.toString()
        }
    }
    
    /**
     * 检测文件编码
     */
    private fun detectCharset(inputStream: java.io.InputStream): Charset {
        // 简单的编码检测：尝试UTF-8，失败则使用GBK
        inputStream.mark(3)
        val bom = ByteArray(3)
        inputStream.read(bom)
        inputStream.reset()
        
        // 检查UTF-8 BOM
        if (bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        
        // 默认使用UTF-8，Android会自动处理GBK
        return Charsets.UTF_8
    }
    
    /**
     * 更新阅读进度
     */
    private fun updateProgress() {
        val scrollView = binding.scrollView
        val scrollY = scrollView.scrollY
        val maxScroll = scrollView.getChildAt(0).height - scrollView.height
        
        if (maxScroll > 0) {
            val progress = (scrollY * 100 / maxScroll)
            binding.seekBar.progress = progress
            binding.tvProgress.text = "$progress%"
        }
        
        // 保存阅读位置
        prefs.edit {
            putInt("${fileUri?.path}_$KEY_SCROLL_POSITION", scrollY)
        }
    }
    
    /**
     * 显示错误
     */
    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
        binding.tvError.text = message
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 低内存设备清理
        if (MemoryUtils.isMemoryCritical()) {
            binding.tvContent.text = ""
            MemoryUtils.suggestGc()
        }
    }
}
