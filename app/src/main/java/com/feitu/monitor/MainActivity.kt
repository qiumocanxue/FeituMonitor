package com.feitu.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import cn.jpush.android.api.JPushInterface
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.feitu.monitor.models.NotificationManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // --- 全局变量 ---
    private lateinit var authService: AuthService
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var tvLogin: TextView
    private lateinit var ivMessage: ImageView
    private lateinit var topBar: ConstraintLayout
    private lateinit var tvBadge: TextView // 红点
    private lateinit var notifyService: NotificationService

    // 监听登录页面关闭后传回来的结果
    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshUIByLoginState()
            notifyService.startService()

            // 登录成功后，偷偷拉取一次未读消息
            lifecycleScope.launch {
                notifyService.fetchHistory()
            }

            // 强制刷新二维码页面，触发网络请求拉取下拉框
            if (bottomNav.selectedItemId == R.id.nav_qrcode) {
                switchFragment(QrGeneratorFragment())
            }
        }
    }

    // --- 权限回调 ---
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) goToNotificationSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化极光
        JPushInterface.setDebugMode(true)
        JPushInterface.init(this)

        // 🌟 2. 【核心修复】必须先初始化这个服务！
        notifyService = NotificationService(this)

        // 3. 接下来再执行其他逻辑
        initViews()
        setupImmersive()
        setupNavigation()
        askNotificationPermission()

        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

            // 如果现在的页面是二维码页面（或者是主 Tab 里的任何一个）
            if (currentFragment is QrGeneratorFragment) {
                // 1. 恢复显示
                bottomNav.visibility = View.VISIBLE
                topBar.visibility = View.VISIBLE

                // 2. 🌟 极其重要：强制让系统重新计算一次刘海间距
                // 否则返回后顶部栏会直接钻到状态栏下面
                ViewCompat.requestApplyInsets(topBar)
                ViewCompat.requestApplyInsets(bottomNav)
            }
        }

        // 4. 最后加载首页 (这里面会用到 notifyService，现在它已经初始化好了)
        if (savedInstanceState == null) {
            handleInitialPage()
        }
    }

    /**
     * 提供给 Fragment 调用的方法：更新顶部标题
     */
    fun updateToolbarTitle(title: String) {
        val tvTitle = findViewById<TextView>(R.id.tv_toolbar_title)
        tvTitle?.text = title
    }


    private fun initViews() {
        bottomNav = findViewById(R.id.bottom_navigation)
        tvLogin = findViewById(R.id.tv_login)
        ivMessage = findViewById(R.id.iv_message)
        topBar = findViewById(R.id.top_bar)
        tvBadge = findViewById(R.id.tv_badge)

        authService = AuthService(this)

        // 刚进 App 时，根据本地真实的 Token 状态刷新 UI
        refreshUIByLoginState()

        // 🌟 监听红点数据变化
        NotificationManager.onUnreadChanged = { count ->
            if (count > 0 && authService.isLoggedIn()) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = if (count > 99) "99+" else count.toString()
            } else {
                tvBadge.visibility = View.GONE
            }
        }

        // 🌟 合并后正确的小铃铛点击事件
        ivMessage.setOnClickListener {
            // 获取当前显示的 Fragment
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

            // 如果已经在消息页了，就不做任何事
            if (currentFragment !is NotificationFragment) {
                // 1. 点开铃铛，立刻把未读数清零 (隐藏红点)
                NotificationManager.markAllAsRead()
                // 2. 跳转到消息页面
                switchFragment(NotificationFragment())
            }
        }

        // 登录/退出点击逻辑
        tvLogin.setOnClickListener {
            if (authService.isLoggedIn()) {
                authService.logout()
                notifyService.stopService() // 停止服务
                NotificationManager.clearMessages()
                refreshUIByLoginState()

                bottomNav.selectedItemId = R.id.nav_qrcode
                switchFragment(QrGeneratorFragment())
            } else {
                val intent = Intent(this, LoginActivity::class.java)
                loginLauncher.launch(intent)
            }
        }
    }

    private fun setupImmersive() {
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // 1. 二维码生成
                R.id.nav_qrcode -> {
                    switchFragment(QrGeneratorFragment())
                    true
                }
                // 🌟 2. 公众号请求 (修复这里！)
                R.id.nav_wechat -> {
                    switchFragment(WebListFragment())
                    true
                }
                // 3. 监控 (建议对应 MonitorFragment)
                R.id.nav_monitor -> {
                    // switchFragment(MonitorFragment())
                    true
                }
                // 4. 文件
                R.id.nav_file -> {
                    // switchFragment(FileCenterFragment())
                    true
                }
                // 5. 配置
                R.id.nav_config -> {
                    // switchFragment(ConfigFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun handleInitialPage() {
        bottomNav.selectedItemId = R.id.nav_qrcode
        switchFragment(QrGeneratorFragment())

        // 刚打开软件时拉取历史消息
        if (authService.isLoggedIn()) {
            notifyService.startService() // 启动长连接
            lifecycleScope.launch {
                // 🌟 修复：改为调用 notifyService.fetchHistory()
                notifyService.fetchHistory()
            }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()

        // 使用淡入淡出动画
//        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)

        if (fragment is NotificationFragment) {
            // 进入消息页：隐藏主 UI，并加入返回栈
            bottomNav.visibility = View.GONE
            topBar.visibility = View.GONE
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack("notification") // 给这个记录取个名字
        } else {
            // 切换主 Tab（如二维码页）：显示主 UI，并清空之前的返回栈记录
            bottomNav.visibility = View.VISIBLE
            topBar.visibility = View.VISIBLE

            // 清除掉所有之前的“入栈”记录，防止按返回键在 Tab 之间乱跳
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

            transaction.replace(R.id.fragment_container, fragment)
        }

        transaction.commit()
    }

    private fun refreshUIByLoginState() {
        val isLoggedIn = authService.isLoggedIn()

        tvLogin.text = if (isLoggedIn) "退出" else "登录"
        ivMessage.visibility = if (isLoggedIn) View.VISIBLE else View.GONE

        val menu = bottomNav.menu
        menu.findItem(R.id.nav_monitor).isVisible = isLoggedIn
        menu.findItem(R.id.nav_file).isVisible = isLoggedIn
        menu.findItem(R.id.nav_config).isVisible = isLoggedIn

        // 🌟 核心修复：退出登录时，也要把红点藏起来；登录时刷新一下红点
        if (!isLoggedIn) {
            tvBadge.visibility = View.GONE
        } else {
            NotificationManager.onUnreadChanged?.invoke(NotificationManager.unreadCount)
        }
    }

    // --- 权限 ---
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (status != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun goToNotificationSettings() {
        val intent = Intent().apply {
            action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Settings.ACTION_APP_NOTIFICATION_SETTINGS
            } else {
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            } else {
                data = android.net.Uri.fromParts("package", packageName, null)
            }
        }
        startActivity(intent)
    }
}