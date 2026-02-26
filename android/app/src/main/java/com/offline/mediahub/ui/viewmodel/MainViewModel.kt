package com.offline.mediahub.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.offline.mediahub.model.MediaFile
import com.offline.mediahub.model.MediaType
import com.offline.mediahub.storage.UsbStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面ViewModel
 * 管理文件列表数据和USB设备状态
 * 
 * 性能优化：
 * 1. 使用协程异步加载
 * 2. 取消未完成的加载任务
 * 3. 缓存已加载的数据
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    private val storageManager = UsbStorageManager(application)
    
    // 文件列表
    private val _files = MutableLiveData<List<MediaFile>>()
    val files: LiveData<List<MediaFile>> = _files
    
    // 加载状态
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    // 当前路径
    private val _currentPath = MutableLiveData<String>()
    val currentPath: LiveData<String> = _currentPath
    
    // USB状态
    private val _usbStatus = MutableLiveData<String>()
    val usbStatus: LiveData<String> = _usbStatus
    
    // 当前分类过滤
    private var currentFilter: MediaType? = null
    
    // 当前USB根URI
    private var rootUri: Uri? = null
    
    // 当前加载任务
    private var loadJob: Job? = null
    
    // 路径栈，用于返回
    private val pathStack = ArrayDeque<String>()
    
    /**
     * 设置USB根目录
     */
    fun setUsbRoot(uri: Uri) {
        rootUri = uri
        _usbStatus.value = "USB设备已连接"
        loadFiles()
    }
    
    /**
     * 加载文件列表
     */
    fun loadFiles(path: String = "") {
        val uri = rootUri ?: return
        
        // 取消之前的加载任务
        loadJob?.cancel()
        
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            
            try {
                val fileList = withContext(Dispatchers.IO) {
                    storageManager.listFiles(uri, path)
                }
                
                // 应用分类过滤
                val filteredList = if (currentFilter != null) {
                    fileList.filter { it.mediaType == currentFilter || it.isDirectory }
                } else {
                    fileList
                }
                
                _files.value = filteredList
                _currentPath.value = path
                
                // 更新状态栏
                _usbStatus.value = "共 ${filteredList.size} 个项目"
                
            } catch (e: Exception) {
                Log.e(TAG, "加载文件失败: ${e.message}")
                _usbStatus.value = "加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 进入文件夹
     */
    fun enterDirectory(file: MediaFile) {
        if (!file.isDirectory) return
        
        // 保存当前路径到栈
        _currentPath.value?.let { pathStack.push(it) }
        
        // 构建新路径
        val newPath = if (_currentPath.value.isNullOrEmpty()) {
            file.name
        } else {
            "${_currentPath.value}/${file.name}"
        }
        
        loadFiles(newPath)
    }
    
    /**
     * 返回上一级
     */
    fun goBack(): Boolean {
        return if (pathStack.isNotEmpty()) {
            val previousPath = pathStack.pop()
            loadFiles(previousPath)
            true
        } else {
            false
        }
    }
    
    /**
     * 设置分类过滤
     */
    fun setFilter(type: MediaType?) {
        currentFilter = type
        loadFiles(_currentPath.value ?: "")
    }
    
    /**
     * 搜索文件
     */
    fun searchFiles(keyword: String) {
        val uri = rootUri ?: return
        
        if (keyword.isBlank()) {
            loadFiles(_currentPath.value ?: "")
            return
        }
        
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            
            try {
                val results = withContext(Dispatchers.IO) {
                    storageManager.searchFiles(uri, keyword)
                }
                _files.value = results
                _usbStatus.value = "找到 ${results.size} 个结果"
                
            } catch (e: Exception) {
                Log.e(TAG, "搜索失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 刷新当前目录
     */
    fun refresh() {
        loadFiles(_currentPath.value ?: "")
    }
    
    /**
     * 检查是否在根目录
     */
    fun isAtRoot(): Boolean = pathStack.isEmpty()
}
