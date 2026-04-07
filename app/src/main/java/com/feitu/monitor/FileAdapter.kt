package com.feitu.monitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.feitu.monitor.models.RemoteFile

class FileAdapter(
    private val items: List<RemoteFile>,
    private val onItemClick: (RemoteFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(android.R.id.icon) // 使用系统ID或自定义
        val tvName: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 这里暂时使用系统自带的简单布局，你可以之后换成漂亮的自定义布局
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        // 简单区分图标
        // holder.ivIcon.setImageResource(if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}