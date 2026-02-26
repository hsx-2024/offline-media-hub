package com.offline.mediahub.model

/**
 * 媒体文件类型枚举
 */
enum class MediaType {
    NOVEL,      // 小说/文本
    AUDIO,      // 音频
    VIDEO,      // 视频
    FOLDER,     // 文件夹
    UNKNOWN;    // 未知类型
    
    companion object {
        /**
         * 根据文件扩展名判断媒体类型
         */
        fun fromExtension(extension: String): MediaType {
            return when (extension.lowercase()) {
                // 小说/文本格式
                "txt", "epub", "mobi", "pdf", "doc", "docx", "rtf" -> NOVEL
                // 压缩的小说文件
                "zst" -> NOVEL
                
                // 音频格式
                "mp3", "wav", "flac", "aac", "m4a", "ogg", "wma", "opus" -> AUDIO
                
                // 视频格式
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v" -> VIDEO
                
                else -> UNKNOWN
            }
        }
    }
    
    /**
     * 获取显示图标
     */
    fun getIcon(): String = when (this) {
        NOVEL -> "📚"
        AUDIO -> "🎵"
        VIDEO -> "🎬"
        FOLDER -> "📁"
        UNKNOWN -> "📄"
    }
}
