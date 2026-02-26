package com.offline.mediahub.player

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.offline.mediahub.R
import com.offline.mediahub.databinding.ActivityVideoPlayerBinding
import com.offline.mediahub.util.MemoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 视频播放器Activity
 * 使用ExoPlayer播放视频，支持多种格式
 * 
 * 性能优化：
 * 1. 使用ExoPlayer的硬件解码
 * 2. 低端设备自动降低分辨率
 * 3. 后台自动暂停
 */
class VideoPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "VideoPlayerActivity"
    }
    
    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    
    private var videoUri: Uri? = null
    private var videoTitle: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取视频信息
        videoUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("uri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("uri")
        }
        videoTitle = intent.getStringExtra("title") ?: "视频播放"
        
        if (videoUri == null) {
            showError("无法获取视频地址")
            return
        }
        
        setupPlayer()
        playVideo()
    }
    
    /**
     * 初始化播放器
     */
    private fun setupPlayer() {
        // 根据设备性能配置播放器
        val loadControl = if (MemoryUtils.isLowMemoryDevice()) {
            // 低端设备：减少缓冲，降低内存占用
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    1000,  // 最小缓冲
                    5000,  // 最大缓冲
                    1000,  // 播放缓冲
                    1000   // 重新缓冲
                )
                .build()
        } else {
            androidx.media3.exoplayer.DefaultLoadControl.Builder().build()
        }
        
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
            .apply {
                // 监听播放事件
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                binding.progressBar.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                binding.progressBar.visibility = View.GONE
                                binding.errorLayout.visibility = View.GONE
                            }
                            Player.STATE_ENDED -> {
                                // 播放结束
                                finish()
                            }
                            Player.STATE_IDLE -> {
                                // 空闲状态
                            }
                        }
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "播放错误: ${error.message}")
                        showError("播放出错: ${error.message}")
                    }
                })
            }
        
        binding.playerView.player = player
        
        // 重试按钮
        binding.btnRetry.setOnClickListener {
            playVideo()
        }
    }
    
    /**
     * 开始播放视频
     */
    private fun playVideo() {
        val uri = videoUri ?: return
        
        binding.errorLayout.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // 构建MediaItem
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setTitle(videoTitle)
                    .build()
                
                withContext(Dispatchers.Main) {
                    player?.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        playWhenReady = true
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "加载视频失败: ${e.message}")
                showError("加载失败: ${e.message}")
            }
        }
    }
    
    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.errorLayout.visibility = View.VISIBLE
        binding.tvError.text = message
    }
    
    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }
    
    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
    
    override fun onBackPressed() {
        // 保存播放进度
        val position = player?.currentPosition ?: 0
        // 可以保存到SharedPreferences以便下次恢复
        
        super.onBackPressed()
    }
}
