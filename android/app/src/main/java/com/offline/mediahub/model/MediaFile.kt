package com.offline.mediahub.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 媒体文件数据模型
 * 使用Parcelize支持Intent传递
 */
@Parcelize
data class MediaFile(
    val id: Long,
    val name: String,
    val path: String,
    val uri: Uri,
    val size: Long,
    val lastModified: Long,
    val mediaType: MediaType,
    val isDirectory: Boolean,
    val mimeType: String? = null
) : Parcelable {
    
    /**
     * 获取格式化的文件大小
     */
    fun getFormattedSize(): String {
        if (isDirectory) return ""
        
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }
    
    /**
     * 获取文件扩展名
     */
    fun getExtension(): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(dotIndex + 1) else ""
    }
    
    /**
     * 是否为压缩文件
     */
    fun isCompressed(): Boolean {
        return getExtension().lowercase() in listOf("zst", "opus")
    }
    
    /**
     * 获取显示名称（去除压缩后缀）
     */
    fun getDisplayName(): String {
        val ext = getExtension().lowercase()
        return if (ext in listOf("zst", "opus")) {
            name.substringBeforeLast('.')
        } else {
            name
        }
    }
}
