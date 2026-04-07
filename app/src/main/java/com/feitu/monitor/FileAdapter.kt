package com.feitu.monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.feitu.monitor.models.RemoteFile
import java.util.*

class FileAdapter(
    private val items: List<RemoteFile>,
    private val onItemClick: (RemoteFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.iv_file_icon)
        val tvName: TextView = view.findViewById(R.id.tv_file_name)
        val tvInfo: TextView = view.findViewById(R.id.tv_file_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name

        // 1. 设置文件信息（大小与时间）
        val sizeStr = if (item.isDirectory) "文件夹" else formatSize(item.size)
        holder.tvInfo.text = "${item.lastModified}  |  $sizeStr"

        // 2. 🌟 根据你的逻辑设置图标
        val iconRes = if (item.isDirectory) {
            R.drawable.ic_folder // 假设你已经有了文件夹图标
        } else {
            getFileIcon(item.name.lowercase(Locale.getDefault()))
        }
        holder.ivIcon.setImageResource(iconRes)

        // 3. 点击事件
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    /**
     * 🌟 核心图标匹配逻辑
     */
    private fun getFileIcon(name: String): Int {
        return when {
            name.endsWith(".doc") || name.endsWith(".docx") -> R.drawable.ic_file_word
            name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".csv") -> R.drawable.ic_file_excel
            name.endsWith(".ppt") || name.endsWith(".pptx") -> R.drawable.ic_file_ppt
            name.endsWith(".pdf") -> R.drawable.ic_file_pdf
            name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".ini") ||
                    name.endsWith(".json") || name.endsWith(".xml") -> R.drawable.ic_file_txt
            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ||
                    name.endsWith(".tar") || name.endsWith(".gz") -> R.drawable.ic_file_zip
            name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> R.drawable.ic_file_image
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") ||
                    name.endsWith(".avi") || name.endsWith(".rmvb") -> R.drawable.ic_file_video
            name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") ||
                    name.endsWith(".m4a") -> R.drawable.ic_file_audio
            name.endsWith(".url") || name.endsWith(".link") -> R.drawable.ic_file_url
            else -> R.drawable.ic_file_unknown
        }
    }

    /**
     * 字节转换辅助工具
     */
    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return try {
            String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        } catch (e: Exception) {
            "$size B"
        }
    }
}