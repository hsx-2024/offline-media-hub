package com.offline.mediahub.storage

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.offline.mediahub.model.MediaFile
import com.offline.mediahub.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * USB存储管理器
 * 负责检测、连接和访问USB大容量存储设备
 * 
 * 针对低端设备优化：
 * 1. 使用DocumentFile而非直接File操作，兼容性更好
 * 2. 异步加载文件列表，避免阻塞UI
 * 3. 分页加载，减少内存占用
 */
class UsbStorageManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UsbStorageManager"
        private const val PAGE_SIZE = 50  // 分页大小，优化低端设备内存
    }
    
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    /**
     * 检测已连接的USB设备
     */
    fun getConnectedUsbDevices(): List<UsbDevice> {
        val devices = usbManager.deviceList.values.filter { device ->
            // 过滤出大容量存储设备
            isMassStorageDevice(device)
        }
        Log.d(TAG, "检测到 ${devices.size} 个USB存储设备")
        return devices.toList()
    }
    
    /**
     * 判断是否为大容量存储设备
     */
    private fun isMassStorageDevice(device: UsbDevice): Boolean {
        // 检查设备接口
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                return true
            }
        }
        return false
    }
    
    /**
     * 请求USB设备访问权限
     */
    fun requestPermission(device: UsbDevice): Boolean {
        return if (usbManager.hasPermission(device)) {
            true
        } else {
            usbManager.requestPermission(device, null)
            false
        }
    }
    
    /**
     * 检查是否已有权限
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }
    
    /**
     * 获取USB设备根目录
     * Android 9+ 使用Storage Access Framework
     */
    fun getUsbRootUri(): Uri? {
        // 通过SAF获取USB存储根目录
        // 用户需要手动选择USB设备
        return null  // 实际使用时通过Intent让用户选择
    }
    
    /**
     * 异步列出目录内容
     * 使用协程避免阻塞主线程
     */
    suspend fun listFiles(
        rootUri: Uri,
        path: String = ""
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<MediaFile>()
        
        try {
            val documentFile = if (path.isEmpty()) {
                DocumentFile.fromTreeUri(context, rootUri)
            } else {
                // 根据路径查找子目录
                findDocumentFile(rootUri, path)
            }
            
            documentFile?.listFiles()?.forEachIndexed { index, doc ->
                val mediaFile = createMediaFile(doc, index.toLong())
                files.add(mediaFile)
            }
            
            // 排序：文件夹优先，然后按名称排序
            files.sortedWith(compareByDescending<MediaFile> { it.isDirectory }
                .thenBy { it.name.lowercase() })
            
        } catch (e: Exception) {
            Log.e(TAG, "列出文件失败: ${e.message}")
        }
        
        files
    }
    
    /**
     * 分页加载文件列表
     * 优化低端设备内存使用
     */
    suspend fun listFilesPaged(
        rootUri: Uri,
        path: String = "",
        page: Int = 0
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val allFiles = listFiles(rootUri, path)
        val startIndex = page * PAGE_SIZE
        val endIndex = minOf(startIndex + PAGE_SIZE, allFiles.size)
        
        if (startIndex < allFiles.size) {
            allFiles.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }
    
    /**
     * 搜索文件
     * 递归搜索匹配的文件名
     */
    suspend fun searchFiles(
        rootUri: Uri,
        keyword: String,
        maxResults: Int = 100
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MediaFile>()
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
        
        rootDoc?.let { searchRecursive(it, keyword.lowercase(), results, maxResults) }
        
        results
    }
    
    /**
     * 递归搜索
     */
    private fun searchRecursive(
        documentFile: DocumentFile,
        keyword: String,
        results: MutableList<MediaFile>,
        maxResults: Int
    ) {
        if (results.size >= maxResults) return
        
        documentFile.listFiles().forEach { doc ->
            if (results.size >= maxResults) return
            
            if (doc.name?.lowercase()?.contains(keyword) == true) {
                results.add(createMediaFile(doc, results.size.toLong()))
            }
            
            // 递归搜索子目录
            if (doc.isDirectory) {
                searchRecursive(doc, keyword, results, maxResults)
            }
        }
    }
    
    /**
     * 根据路径查找DocumentFile
     */
    private fun findDocumentFile(rootUri: Uri, path: String): DocumentFile? {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        
        if (path.isEmpty()) return rootDoc
        
        var currentDoc = rootDoc
        val segments = path.split("/")
        
        for (segment in segments) {
            if (segment.isEmpty()) continue
            currentDoc = currentDoc.findFile(segment) ?: return null
        }
        
        return currentDoc
    }
    
    /**
     * 创建MediaFile对象
     */
    private fun createMediaFile(doc: DocumentFile, id: Long): MediaFile {
        val name = doc.name ?: "Unknown"
        val extension = name.substringAfterLast('.', "")
        val mediaType = if (doc.isDirectory) {
            MediaType.FOLDER
        } else {
            MediaType.fromExtension(extension)
        }
        
        return MediaFile(
            id = id,
            name = name,
            path = doc.uri.path ?: "",
            uri = doc.uri,
            size = doc.length(),
            lastModified = doc.lastModified(),
            mediaType = mediaType,
            isDirectory = doc.isDirectory,
            mimeType = doc.type
        )
    }
    
    /**
     * 打开文件输入流
     */
    fun openInputStream(uri: Uri) = context.contentResolver.openInputStream(uri)
    
    /**
     * 获取文件MIME类型
     */
    fun getMimeType(uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }
}
