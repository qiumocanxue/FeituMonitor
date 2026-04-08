package com.feitu.monitor.remote

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.feitu.monitor.R
import com.feitu.monitor.remote.models.RemoteFile
import com.feitu.monitor.common.utils.FileIconUtils

class FileAdapter(
    private val items: List<RemoteFile>,
    private val onItemClick: (RemoteFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 🌟 统一使用标准布局
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_standard_row, parent, false)
        return ViewHolder(view)
    }

    // 对应更新 ViewHolder 引用
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.iv_icon)
        val tvName: TextView = view.findViewById(R.id.tv_primary)
        val tvInfo: TextView = view.findViewById(R.id.tv_secondary)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // 🌟 使用接口提供的统一属性
        holder.tvName.text = item.fileName
        holder.tvInfo.text = "${item.dateText}  |  ${item.sizeText}"

        // 🌟 使用统一图标工具类
        holder.ivIcon.setImageResource(FileIconUtils.getFileIconRes(item))

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

}