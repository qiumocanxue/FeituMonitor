package com.feitu.monitor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.feitu.monitor.models.FtpConfig
import com.feitu.monitor.models.FtpItem
import com.feitu.monitor.models.FtpUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

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
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_title_file_ops)
                    .setMessage(R.string.dialog_msg_file_ops)
                    .setPositiveButton(R.string.action_preview) { _, _ ->
                        com.feitu.monitor.models.FtpCacheManager.previewFile(requireContext(), fileUrl, item.name)
                    }
                    .setNegativeButton(R.string.action_download) { _, _ ->
                        val success = com.feitu.monitor.models.FtpDownloadManager.promoteCacheToDownload(
                            requireContext(), item.name, fileUrl
                        )
                        if (success) {
                            Toast.makeText(context, R.string.toast_cache_restored, Toast.LENGTH_SHORT).show()
                        } else {
                            com.feitu.monitor.models.FtpDownloadManager.startOrResumeDownload(
                                fileUrl, item.name, requireContext()
                            )
                            Toast.makeText(context, R.string.toast_download_added, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNeutralButton(R.string.action_cancel, null)
                    .show()
            } else {
                com.feitu.monitor.models.FtpDownloadManager.startOrResumeDownload(fileUrl, item.name, requireContext())
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
            val root = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(32, 32, 32, 32)
                gravity = android.view.Gravity.CENTER_VERTICAL

                val icon = ImageView(context).apply {
                    id = android.R.id.icon
                    layoutParams = android.widget.LinearLayout.LayoutParams(80, 80).apply { setMargins(0,0,32,0) }
                }
                addView(icon)
                addView(view)
            }
            return ViewHolder(root)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // 🌟 3. 使用 getItem(position) 获取数据
            val item = getItem(position)

            holder.tvName.text = item.name
            holder.tvName.textSize = 15f
            holder.tvName.setTextColor("#333333".toColorInt())

            holder.tvMeta.text = holder.itemView.context.getString(R.string.ftp_meta_format, item.date, item.size)
            holder.tvMeta.textSize = 12f
            holder.tvMeta.setTextColor("#888888".toColorInt())

            holder.ivIcon.setImageResource(getFileIcon(item))
            holder.itemView.setOnClickListener { onClick(item) }
        }

        private fun getFileIcon(item: FtpItem): Int {
            if (item.isDir) return R.drawable.ic_folder
            val name = item.name.lowercase()
            return when {
                name.endsWith(".doc") || name.endsWith(".docx") -> R.drawable.ic_file_word
                name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".csv") -> R.drawable.ic_file_excel
                name.endsWith(".ppt") || name.endsWith(".pptx") -> R.drawable.ic_file_ppt
                name.endsWith(".pdf") -> R.drawable.ic_file_pdf
                name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".ini") || name.endsWith(".json") || name.endsWith(".xml") -> R.drawable.ic_file_txt
                name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz") -> R.drawable.ic_file_zip
                name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> R.drawable.ic_file_image
                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".avi") || name.endsWith(".rmvb") -> R.drawable.ic_file_video
                name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") || name.endsWith(".m4a") -> R.drawable.ic_file_audio
                name.endsWith(".url") || name.endsWith(".link") -> R.drawable.ic_file_url
                else -> R.drawable.ic_file_unknown
            }
        }
    }
}