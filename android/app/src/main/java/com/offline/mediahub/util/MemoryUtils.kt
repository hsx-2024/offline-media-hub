package com.offline.mediahub.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache

/**
 * 内存管理工具类
 * 针对低端设备进行内存优化
 */
object MemoryUtils {
    
    private const val TAG = "MemoryUtils"
    
    private lateinit var appContext: Context
    private var memoryClass: Int = 0
    private var isLowMemoryDevice: Boolean = false
    
    // 图片缓存
    private var imageCache: LruCache<String, Bitmap>? = null
    
    /**
     * 初始化内存管理
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        memoryClass = activityManager.memoryClass
        isLowMemoryDevice = activityManager.isLowRamDevice
        
        Log.d(TAG, "设备内存级别: ${memoryClass}MB, 低内存设备: $isLowMemoryDevice")
        
        // 初始化图片缓存
        initImageCache()
    }
    
    /**
     * 初始化图片缓存
     * 根据设备内存动态调整缓存大小
     */
    private fun initImageCache() {
        // 计算可用内存的1/8作为图片缓存
        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val cacheSize = if (isLowMemoryDevice) {
            // 低端设备使用更小的缓存
            maxMemory / 16
        } else {
            maxMemory / 8
        }.toInt()
        
        imageCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
        
        Log.d(TAG, "图片缓存大小: ${cacheSize}KB")
    }
    
    /**
     * 添加图片到缓存
     */
    fun addBitmapToCache(key: String, bitmap: Bitmap) {
        synchronized(this) {
            imageCache?.put(key, bitmap)
        }
    }
    
    /**
     * 从缓存获取图片
     */
    fun getBitmapFromCache(key: String): Bitmap? {
        return synchronized(this) {
            imageCache?.get(key)
        }
    }
    
    /**
     * 清空图片缓存
     */
    fun clearImageCache() {
        synchronized(this) {
            imageCache?.evictAll()
        }
        Log.d(TAG, "图片缓存已清空")
    }
    
    /**
     * 检查是否为低内存设备
     */
    fun isLowMemoryDevice(): Boolean = isLowMemoryDevice
    
    /**
     * 获取可用内存（MB）
     */
    fun getAvailableMemory(): Long {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        return (maxMemory - usedMemory) / (1024 * 1024)
    }
    
    /**
     * 检查内存是否紧张
     */
    fun isMemoryCritical(): Boolean {
        return getAvailableMemory() < 20  // 少于20MB视为内存紧张
    }
    
    /**
     * 建议进行垃圾回收
     * 仅在内存紧张时调用
     */
    fun suggestGc() {
        if (isMemoryCritical()) {
            System.gc()
            Log.d(TAG, "建议执行GC")
        }
    }
    
    /**
     * 获取内存使用报告
     */
    fun getMemoryReport(): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        
        return """
            内存报告:
            - 最大可用: ${maxMemory}MB
            - 已分配: ${totalMemory}MB
            - 已使用: ${usedMemory}MB
            - 空闲: ${freeMemory}MB
            - 低内存设备: $isLowMemoryDevice
        """.trimIndent()
    }
}
