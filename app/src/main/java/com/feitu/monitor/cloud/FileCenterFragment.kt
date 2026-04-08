package com.feitu.monitor.cloud

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.feitu.monitor.download.DownloadListActivity
import com.feitu.monitor.MainActivity
import com.feitu.monitor.R
import com.feitu.monitor.cloud.models.FtpCacheManager
import com.feitu.monitor.cloud.models.FtpConfig
import com.feitu.monitor.download.models.FtpDownloadManager
import com.feitu.monitor.cloud.models.FtpItem
import com.feitu.monitor.cloud.models.FtpUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import com.feitu.monitor.common.utils.FileIconUtils

class FileCenterFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvTitle: TextView
    private lateinit var ivBack: ImageView
    private lateinit var adapter: FtpAdapter

    private lateinit var backPressedCallback: OnBackPressedCallback

    private var currentSubPath = "/"
    private val pathStack = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_file_center, container, false)

        recyclerView = view.findViewById(R.id.recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        tvError = view.findViewById(R.id.tv_error)
        tvTitle = view.findViewById(R.id.tv_title)
        ivBack = view.findViewById(R.id.iv_back)

        // 初始化适配器
        adapter = FtpAdapter { item -> onItemClick(item) }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        view.findViewById<ImageView>(R.id.iv_refresh).setOnClickListener { fetchDirectory(currentSubPath) }

        view.findViewById<ImageView>(R.id.iv_download_list)?.setOnClickListener {
            startActivity(Intent(requireContext(), DownloadListActivity::class.java))
        }

        ivBack.setOnClickListener {
            if (pathStack.isNotEmpty()) {
                currentSubPath = pathStack.removeAt(pathStack.lastIndex)
                fetchDirectory(currentSubPath, isBack = true)
            }
        }

        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (pathStack.isNotEmpty()) {
                    currentSubPath = pathStack.removeAt(pathStack.lastIndex)
                    fetchDirectory(currentSubPath, isBack = true)
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        fetchDirectory(currentSubPath)
        return view
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.updateToolbarTitle("文件中心")
    }

    private fun fetchDirectory(path: String, isBack: Boolean = false) {
        tvTitle.text = getString(R.string.ftp_title, path)
        ivBack.visibility = if (path == "/") View.GONE else View.VISIBLE
        progressBar.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        recyclerView.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cleanPath = path.replace(Regex("/{2,}"), "/")
                val url = URL("http://${FtpConfig.HOST}$cleanPath")

                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", FtpConfig.getBasicAuthHeader())
                connection.connectTimeout = 10000

                if (connection.responseCode == 200) {
                    val html = connection.inputStream.bufferedReader().readText()
                    val items = parseHtml(html)

                    withContext(Dispatchers.Main) {
                        if (!isBack && currentSubPath != path) {
                            pathStack.add(currentSubPath)
                        }
                        currentSubPath = cleanPath

                        // 🌟 核心修改：使用 submitList 触发差异计算
                        adapter.submitList(items)

                        progressBar.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        backPressedCallback.isEnabled = currentSubPath != "/"
                    }
                } else {
                    throw Exception("HTTP Error: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvError.visibility = View.VISIBLE
                    tvError.text = getString(R.string.load_failed_msg, e.message)
                }
            }
        }
    }

    private fun parseHtml(html: String): List<FtpItem> {
        val items = mutableListOf<FtpItem>()
        val anchorRegex = Regex("""<a\s+href="([^"]+)">([^<]+)</a>""", RegexOption.IGNORE_CASE)
        val metaRegex = Regex("""(\S+\s\S+)\s+([\d-]+)""")

        val matches = anchorRegex.findAll(html).toList()
        for (i in matches.indices) {
            val match = matches[i]
            var href = match.groups[1]?.value ?: ""
            var name = match.groups[2]?.value ?: ""

            if (href == "../" || name == "../") continue

            try {
                href = URLDecoder.decode(href, "UTF-8")
                name = URLDecoder.decode(name, "UTF-8")
            } catch (_: Exception) {}

            val isDir = href.endsWith("/")
            var datePart = "-"
            var sizePart = "-"

            val lookAheadStart = match.range.last + 1
            if (lookAheadStart < html.length) {
                val nextText = html.substring(lookAheadStart).substringBefore('\n')
                val metaMatch = metaRegex.find(nextText)
                if (metaMatch != null) {
                    datePart = metaMatch.groups[1]?.value ?: "-"
                    sizePart = metaMatch.groups[2]?.value ?: "-"
                }
            }

            items.add(
                FtpItem(
                    name = name.replace("/", ""),
                    href = href,
                    isDir = isDir,
                    date = FtpUtils.formatDateString(datePart),
                    size = FtpUtils.formatSize(sizePart)
                )
            )
        }
        return items.sortedWith(compareBy({ !it.isDir }, { it.name }))
    }

    private fun onItemClick(item: FtpItem) {
        if (item.isDir) {
            fetchDirectory("$currentSubPath${item.href}")
        } else {
            val fileUrl = "http://${FtpConfig.HOST}$currentSubPath${item.href}".replace(Regex("/{2,}"), "/")
            val fileNameLower = item.name.lowercase()

            val isText = FtpConfig.PREVIEW_EXTENSIONS.any { fileNameLower.endsWith(it) }
            val previewableExts = listOf(".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".pdf", ".zip", ".rar", ".jpg", ".jpeg", ".png", ".mp4")
            val isThirdPartyPreview = previewableExts.any { fileNameLower.endsWith(it) }

            if (isText) {
                val intent = Intent(requireContext(), FilePreviewActivity::class.java).apply {
                    putExtra("FILE_URL", fileUrl)
                    putExtra("FILE_NAME", item.name)
                }
                startActivity(intent)
            } else if (isThirdPartyPreview) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_title_file_ops)
                    .setMessage(R.string.dialog_msg_file_ops)
                    .setPositiveButton(R.string.action_preview) { _, _ ->
                        FtpCacheManager.previewFile(requireContext(), fileUrl, item.name)
                    }
                    .setNegativeButton(R.string.action_download) { _, _ ->
                        val success = FtpDownloadManager.promoteCacheToDownload(
                            requireContext(), item.name, fileUrl
                        )
                        if (success) {
                            Toast.makeText(context, R.string.toast_cache_restored, Toast.LENGTH_SHORT).show()
                        } else {
                            FtpDownloadManager.startOrResumeDownload(
                                fileUrl, item.name, requireContext()
                            )
                            Toast.makeText(context, R.string.toast_download_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNeutralButton(R.string.action_cancel, null)
                    .show()
            } else {
                FtpDownloadManager.startOrResumeDownload(fileUrl, item.name, requireContext())
                Toast.makeText(context, R.string.toast_download_added, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🌟 1. 定义 DiffUtil 比较器：这是消除警告的关键逻辑
    class FtpDiffCallback : DiffUtil.ItemCallback<FtpItem>() {
        override fun areItemsTheSame(oldItem: FtpItem, newItem: FtpItem): Boolean {
            // 链接唯一，判断是否为同一个条目
            return oldItem.href == newItem.href
        }

        override fun areContentsTheSame(oldItem: FtpItem, newItem: FtpItem): Boolean {
            // data class 自动比对内容是否变化
            return oldItem == newItem
        }
    }

    // 🌟 2. 继承 ListAdapter 而不是 RecyclerView.Adapter
    inner class FtpAdapter(private val onClick: (FtpItem) -> Unit) :
        ListAdapter<FtpItem, FtpAdapter.ViewHolder>(FtpDiffCallback()) {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(android.R.id.icon)
            val tvName: TextView = view.findViewById(android.R.id.text1)
            val tvMeta: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                gravity = Gravity.CENTER_VERTICAL

                val icon = ImageView(context).apply {
                    id = android.R.id.icon
                    layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(0,0,32,0) }
                }
                addView(icon)
                addView(view)
            }
            return ViewHolder(root)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)

            holder.tvName.text = item.fileName // 🌟 使用接口属性
            holder.tvName.textSize = 15f
            holder.tvName.setTextColor("#333333".toColorInt())

            // 🌟 使用接口属性简化代码
            holder.tvMeta.text = holder.itemView.context.getString(
                R.string.ftp_meta_format,
                item.dateText,
                item.sizeText
            )
            holder.tvMeta.textSize = 12f
            holder.tvMeta.setTextColor("#888888".toColorInt())

            // 🌟 替换为统一逻辑
            holder.ivIcon.setImageResource(FileIconUtils.getFileIconRes(item))

            holder.itemView.setOnClickListener { onClick(item) }
        }
    }
}