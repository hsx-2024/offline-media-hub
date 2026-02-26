package com.offline.mediahub.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.offline.mediahub.R
import com.offline.mediahub.databinding.ItemFileBinding
import com.offline.mediahub.model.MediaFile
import com.offline.mediahub.model.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件列表适配器
 * 使用ListAdapter + DiffUtil优化列表更新性能
 * 
 * 性能优化措施：
 * 1. 使用ViewBinding减少findViewById调用
 * 2. 使用DiffUtil增量更新
 * 3. 复用ViewHolder
 * 4. 避免在onBindViewHolder中创建对象
 */
class FileAdapter(
    private val onItemClick: (MediaFile) -> Unit,
    private val onItemLongClick: (MediaFile) -> Unit
) : ListAdapter<MediaFile, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    // 日期格式化器，避免重复创建
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(
        private val binding: ItemFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: MediaFile) {
            // 设置图标
            binding.tvIcon.text = file.mediaType.getIcon()
            
            // 设置文件名
            binding.tvFileName.text = file.getDisplayName()
            
            // 设置文件信息
            val info = buildString {
                if (!file.isDirectory) {
                    append(file.getFormattedSize())
                    append(" · ")
                }
                append(dateFormat.format(Date(file.lastModified)))
            }
            binding.tvFileInfo.text = info
            
            // 设置分类颜色
            val colorRes = when (file.mediaType) {
                MediaType.NOVEL -> R.color.novel_color
                MediaType.AUDIO -> R.color.audio_color
                MediaType.VIDEO -> R.color.video_color
                else -> R.color.text_secondary
            }
            
            // 点击事件
            binding.root.setOnClickListener { onItemClick(file) }
            binding.root.setOnLongClickListener { 
                onItemLongClick(file)
                true
            }
        }
    }

    /**
     * DiffUtil回调，用于计算列表差异
     */
    class FileDiffCallback : DiffUtil.ItemCallback<MediaFile>() {
        override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean {
            return oldItem == newItem
        }
    }
}
