package com.offline.mediahub.decompress

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 解压缩工具类
 * 支持zstd压缩文件的解压
 * 
 * 注意：由于Android原生不支持zstd，这里提供接口
 * 实际使用时需要添加zstd-jni库或使用其他方案
 */
object Decompressor {
    
    private const val TAG = "Decompressor"
    
    /**
     * 检查是否为压缩文件
     */
    fun isCompressedFile(uri: Uri): Boolean {
        val path = uri.path ?: return false
        return path.endsWith(".zst", ignoreCase = true) ||
               path.endsWith(".opus", ignoreCase = true)
    }
    
    /**
     * 获取解压后的文件名
     */
    fun getDecompressedName(fileName: String): String {
        return when {
            fileName.endsWith(".zst", ignoreCase = true) -> 
                fileName.substringBeforeLast('.')
            fileName.endsWith(".opus", ignoreCase = true) -> 
                fileName.substringBeforeLast('.') + ".ogg"
            else -> fileName
        }
    }
    
    /**
     * 解压zstd文件
     * 
     * 注意：这需要添加zstd-jni依赖
     * 在build.gradle中添加: implementation("com.github.luben:zstd-jni:1.5.5-5")
     */
    suspend fun decompressZstd(
        context: Context,
        sourceUri: Uri,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext false
            
            // 使用临时文件
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.zst")
            
            // 复制到临时文件
            copyStreamToFile(inputStream, tempFile)
            
            // TODO: 实际解压需要zstd-jni库
            // 示例代码（需要添加依赖）：
            // com.github.luben.zstd.ZstdInputStream(FileInputStream(tempFile)).use { zis ->
            //     FileOutputStream(outputFile).use { fos ->
            //         zis.copyTo(fos)
            //     }
            // }
            
            // 简化处理：直接复制（实际使用时需要真正的解压）
            tempFile.copyTo(outputFile, overwrite = true)
            tempFile.delete()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "解压失败: ${e.message}")
            false
        }
    }
    
    /**
     * 复制流到文件
     */
    private fun copyStreamToFile(inputStream: InputStream, file: File) {
        FileOutputStream(file).use { output ->
            val buffer = ByteArray(8192)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
        }
        inputStream.close()
    }
    
    /**
     * 获取缓存文件路径
     */
    fun getCacheFile(context: Context, fileName: String): File {
        return File(context.cacheDir, "decompressed/$fileName")
    }
    
    /**
     * 清理解压缓存
     */
    fun clearCache(context: Context) {
        val cacheDir = File(context.cacheDir, "decompressed")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }
}
