package com.offline.mediahub

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.offline.mediahub.databinding.ActivityMainBinding
import com.offline.mediahub.model.MediaFile
import com.offline.mediahub.model.MediaType
import com.offline.mediahub.player.VideoPlayerActivity
import com.offline.mediahub.reader.NovelReaderActivity
import com.offline.mediahub.ui.adapter.FileAdapter
import com.offline.mediahub.ui.viewmodel.MainViewModel
import com.offline.mediahub.util.MemoryUtils
import kotlinx.coroutines.launch

/**
 * 主Activity
 * 文件浏览器界面
 * 
 * 性能优化措施：
 * 1. 使用ViewBinding
 * 2. 使用RecyclerView + DiffUtil
 * 3. 异步加载文件列表
 * 4. 内存紧张时释放缓存
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        const val ACTION_USB_PERMISSION = "com.offline.mediahub.USB_PERMISSION"
    }
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var fileAdapter: FileAdapter
    
    private val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }
    
    // USB权限广播接收器
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { openUsbStorage() }
                        } else {
                            Toast.makeText(context, R.string.usb_permission_required, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    checkUsbDevices()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Toast.makeText(context, "USB设备已断开", Toast.LENGTH_SHORT).show()
                    binding.tvStatus.text = getString(R.string.usb_not_found)
                }
            }
        }
    }
    
    // SAF文件选择器
    private val openDocumentTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 持久化URI权限
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.setUsbRoot(it)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        setupObservers()
        setupListeners()
        registerUsbReceiver()
        
        // 检查USB设备
        checkUsbDevices()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        
        // 内存清理
        if (MemoryUtils.isMemoryCritical()) {
            MemoryUtils.clearImageCache()
        }
    }
    
    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            onItemClick = { file -> onFileClick(file) },
            onItemLongClick = { file -> onFileLongClick(file) }
        )
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fileAdapter
            // 性能优化：固定大小，避免重复测量
            setHasFixedSize(true)
            // 性能优化：回收视图池大小
            setItemViewCacheSize(20)
        }
    }
    
    /**
     * 设置数据观察
     */
    private fun setupObservers() {
        viewModel.files.observe(this) { files ->
            fileAdapter.submitList(files)
            binding.emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        viewModel.currentPath.observe(this) { path ->
            binding.tvTitle.text = if (path.isEmpty()) {
                getString(R.string.app_name)
            } else {
                path.substringAfterLast("/")
            }
            binding.btnBack.visibility = if (path.isEmpty()) View.GONE else View.VISIBLE
        }
        
        viewModel.usbStatus.observe(this) { status ->
            binding.tvStatus.text = status
        }
    }
    
    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            if (!viewModel.goBack()) {
                finish()
            }
        }
        
        // 搜索按钮
        binding.btnSearch.setOnClickListener {
            showSearchDialog()
        }
        
        // 分类过滤
        binding.chipAll.setOnClickListener { viewModel.setFilter(null) }
        binding.chipNovel.setOnClickListener { viewModel.setFilter(MediaType.NOVEL) }
        binding.chipAudio.setOnClickListener { viewModel.setFilter(MediaType.AUDIO) }
        binding.chipVideo.setOnClickListener { viewModel.setFilter(MediaType.VIDEO) }
    }
    
    /**
     * 注册USB广播接收器
     */
    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        
        registerReceiver(usbReceiver, filter)
    }
    
    /**
     * 检查USB设备
     */
    private fun checkUsbDevices() {
        val devices = usbManager.deviceList.values
        
        if (devices.isEmpty()) {
            // 没有检测到USB设备，提示用户通过SAF选择
            showOpenUsbDialog()
        } else {
            // 检查是否有大容量存储设备
            val storageDevice = devices.firstOrNull { device ->
                isMassStorageDevice(device)
            }
            
            if (storageDevice != null) {
                requestUsbPermission(storageDevice)
            } else {
                showOpenUsbDialog()
            }
        }
    }
    
    /**
     * 判断是否为大容量存储设备
     */
    private fun isMassStorageDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_MASS_STORAGE) {
                return true
            }
        }
        return false
    }
    
    /**
     * 请求USB权限
     */
    private fun requestUsbPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            openUsbStorage()
        } else {
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }
    
    /**
     * 打开USB存储
     * 使用SAF让用户选择USB根目录
     */
    private fun openUsbStorage() {
        // Android 9+ 推荐使用SAF访问USB存储
        openDocumentTree.launch(null)
    }
    
    /**
     * 显示打开USB对话框
     */
    private fun showOpenUsbDialog() {
        AlertDialog.Builder(this)
            .setTitle("选择存储设备")
            .setMessage("请选择要访问的USB存储设备或外部存储")
            .setPositiveButton("选择") { _, _ ->
                openDocumentTree.launch(null)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 文件点击事件
     */
    private fun onFileClick(file: MediaFile) {
        when {
            file.isDirectory -> {
                viewModel.enterDirectory(file)
            }
            file.mediaType == MediaType.VIDEO -> {
                openVideoPlayer(file)
            }
            file.mediaType == MediaType.AUDIO -> {
                openAudioPlayer(file)
            }
            file.mediaType == MediaType.NOVEL -> {
                openNovelReader(file)
            }
            else -> {
                Toast.makeText(this, "不支持的文件类型", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 文件长按事件
     */
    private fun onFileLongClick(file: MediaFile) {
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(arrayOf("查看详情", "分享", "删除")) { _, which ->
                when (which) {
                    0 -> showFileDetails(file)
                    1 -> shareFile(file)
                    2 -> Toast.makeText(this, "删除功能开发中", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    
    /**
     * 显示文件详情
     */
    private fun showFileDetails(file: MediaFile) {
        val message = """
            文件名: ${file.name}
            大小: ${file.getFormattedSize()}
            类型: ${file.mediaType}
            路径: ${file.path}
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("文件详情")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 分享文件
     */
    private fun shareFile(file: MediaFile) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, file.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享文件"))
    }
    
    /**
     * 打开视频播放器
     */
    private fun openVideoPlayer(file: MediaFile) {
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra("uri", file.uri)
            putExtra("title", file.getDisplayName())
        }
        startActivity(intent)
    }
    
    /**
     * 打开音频播放器
     */
    private fun openAudioPlayer(file: MediaFile) {
        // TODO: 实现音频播放器
        Toast.makeText(this, "音频播放器开发中", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 打开小说阅读器
     */
    private fun openNovelReader(file: MediaFile) {
        val intent = Intent(this, NovelReaderActivity::class.java).apply {
            putExtra("uri", file.uri)
            putExtra("title", file.getDisplayName())
        }
        startActivity(intent)
    }
    
    /**
     * 显示搜索对话框
     */
    private fun showSearchDialog() {
        val input = android.widget.EditText(this)
        input.hint = "输入搜索关键词"
        
        AlertDialog.Builder(this)
            .setTitle("搜索文件")
            .setView(input)
            .setPositiveButton("搜索") { _, _ ->
                viewModel.searchFiles(input.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onBackPressed() {
        if (!viewModel.goBack()) {
            super.onBackPressed()
        }
    }
}
