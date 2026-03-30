package com.feitu.monitor

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.feitu.monitor.models.NotificationManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NotificationFragment : Fragment() {

    private lateinit var adapter: NotificationAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var layoutEmpty: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_notification, container, false)

        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        layoutEmpty = view.findViewById(R.id.layout_empty)
        val ivClearAll = view.findViewById<ImageView>(R.id.iv_clear_all)

        // 1. 初始化 RecyclerView
        adapter = NotificationAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // 2. 监听全局数据变化
        NotificationManager.onDataChanged = {
            adapter.notifyDataSetChanged()
            updateEmptyState()
            swipeRefresh.isRefreshing = false
        }

        // 3. 下拉刷新
        swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                // 🌟 改为调用 Service 的 fetchHistory
                val service = NotificationService(requireContext())
                service.fetchHistory()
                swipeRefresh.isRefreshing = false
            }
        }

        // 清空按钮
        ivClearAll.setOnClickListener {
            NotificationManager.clearMessages()
        }

        val ivBack = view.findViewById<ImageView>(R.id.iv_back)
        ivBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 初始化时同步一次空状态
        updateEmptyState()

        // ⚠️ 沉浸式代码已经从这里移走啦！去下面找它。

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🌟 核心逻辑：把占位块的高度设为状态栏高度
        val statusBarSpacer = view.findViewById<View>(R.id.status_bar_spacer)

        // 1. 获取物理状态栏高度
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (25 * resources.displayMetrics.density).toInt() // 保底值
        }

        // 2. 将高度应用到占位 View
        statusBarSpacer.layoutParams.height = statusBarHeight
        statusBarSpacer.requestLayout()

        // --- 业务逻辑：拉取数据 ---
        lifecycleScope.launch {
            // 🌟 改为调用 Service 的 fetchHistory
            val service = NotificationService(requireContext())
            service.fetchHistory()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        NotificationManager.onDataChanged = null
    }

    private fun updateEmptyState() {
        if (NotificationManager.messages.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
        } else {
            layoutEmpty.visibility = View.GONE
        }
    }

    // --- 内部 Adapter ---
    inner class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_msg_title)
            val tvTime: TextView = view.findViewById(R.id.tv_msg_time)
            val ivIcon: ImageView = view.findViewById(R.id.iv_level_icon)
            val flIconBg: FrameLayout = view.findViewById(R.id.fl_icon_bg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = NotificationManager.messages[position]
            holder.tvTitle.text = msg.msg
            holder.tvTime.text = dateFormat.format(Date(msg.time))

            val (colorStr, iconRes) = when (msg.level) {
                "error" -> Pair("#FF3B30", android.R.drawable.stat_notify_error)
                "warning" -> Pair("#FF9500", android.R.drawable.stat_sys_warning)
                "success" -> Pair("#34C759", android.R.drawable.presence_online)
                else -> Pair("#007AFF", android.R.drawable.ic_dialog_info)
            }

            val mainColor = Color.parseColor(colorStr)

            holder.ivIcon.setImageResource(iconRes)
            holder.ivIcon.imageTintList = ColorStateList.valueOf(mainColor)
            holder.flIconBg.backgroundTintList = ColorStateList.valueOf(Color.argb(38, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor)))
        }

        override fun getItemCount() = NotificationManager.messages.size
    }
}