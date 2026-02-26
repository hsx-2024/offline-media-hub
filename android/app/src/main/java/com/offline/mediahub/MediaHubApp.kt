package com.offline.mediahub

import android.app.Application
import android.os.StrictMode
import com.offline.mediahub.util.MemoryUtils

/**
 * 应用程序入口类
 * 负责全局初始化和内存优化配置
 */
class MediaHubApp : Application() {
    
    companion object {
        lateinit var instance: MediaHubApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 针对低端设备的性能优化配置
        configureForLowEndDevice()
        
        // 初始化内存监控
        MemoryUtils.init(this)
    }
    
    /**
     * 低端设备优化配置
     * 减少内存占用，提升流畅度
     */
    private fun configureForLowEndDevice() {
        // 放宽主线程磁盘操作限制，避免USB读取时崩溃
        // USB存储访问必须允许主线程IO
        val policy = StrictMode.ThreadPolicy.Builder()
            .permitDiskReads()
            .permitDiskWrites()
            .penaltyLog()
            .build()
        StrictMode.setThreadPolicy(policy)
    }
}
