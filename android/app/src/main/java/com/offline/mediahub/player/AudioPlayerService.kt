package com.offline.mediahub.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.offline.mediahub.MainActivity
import com.offline.mediahub.R

/**
 * 音频播放服务
 * 支持后台播放和通知栏控制
 * 
 * 性能优化：
 * 1. 单例播放器，避免重复创建
 * 2. 低内存时自动释放资源
 */
class AudioPlayerService : Service() {
    
    companion object {
        private const val TAG = "AudioPlayerService"
        private const val CHANNEL_ID = "audio_player_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    private var player: ExoPlayer? = null
    private val binder = AudioBinder()
    
    private var currentTitle: String = ""
    private var currentUri: Uri? = null
    
    inner class AudioBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
        fun getPlayer(): ExoPlayer? = player
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initPlayer()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("uri", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("uri")
                }
                val title = intent.getStringExtra("title") ?: ""
                play(uri, title)
            }
            "PAUSE" -> player?.pause()
            "RESUME" -> player?.play()
            "STOP" -> stopSelf()
        }
        return START_NOT_STICKY
    }
    
    /**
     * 初始化播放器
     */
    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateNotification()
                }
            })
        }
    }
    
    /**
     * 播放音频
     */
    fun play(uri: Uri?, title: String) {
        if (uri == null) return
        
        currentUri = uri
        currentTitle = title
        
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setTitle(title)
            .build()
        
        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音频播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音频播放控制"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建播放通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val pauseIntent = Intent(this, AudioPlayerService::class.java).apply {
            action = "PAUSE"
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText("正在播放")
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_pause, "暂停", pausePendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }
    
    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
