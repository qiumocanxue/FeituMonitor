package com.feitu.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import cn.jpush.android.api.JPushInterface
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.feitu.monitor.models.NotificationManager
import androidx.lifecycle.lifecycleScope
import com.feitu.monitor.cloud.FileCenterFragment
import kotlinx.coroutines.launch
import com.feitu.monitor.cloud.models.FtpUtils
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var authService: AuthService
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var tvLogin: TextView
    private lateinit var ivMessage: ImageView
    private lateinit var topBar: ConstraintLayout
    private lateinit var tvBadge: TextView
    private lateinit var notifyService: NotificationService

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshUIByLoginState()
            notifyService.startService()
            lifecycleScope.launch {
                notifyService.fetchHistory()
            }
            if (bottomNav.selectedItemId == R.id.nav_qrcode) {
                switchFragment(QrGeneratorFragment())
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) goToNotificationSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        JPushInterface.setDebugMode(true)
        JPushInterface.init(this)

        notifyService = NotificationService(this)

        initViews()
        setupImmersive()
        setupNavigation()
        askNotificationPermission()

        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment !is NotificationFragment) {
                bottomNav.visibility = View.VISIBLE
                topBar.visibility = View.VISIBLE
                ViewCompat.requestApplyInsets(topBar)
                ViewCompat.requestApplyInsets(bottomNav)
            }
        }

        if (savedInstanceState == null) {
            handleInitialPage()
        }
    }

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

        refreshUIByLoginState()

        NotificationManager.onUnreadChanged = { count ->
            if (count > 0 && authService.isLoggedIn()) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = if (count > 99) "99+" else count.toString()
            } else {
                tvBadge.visibility = View.GONE
            }
        }

        ivMessage.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment !is NotificationFragment) {
                NotificationManager.markAllAsRead()
                switchFragment(NotificationFragment())
            }
        }

        tvLogin.setOnClickListener {
            if (authService.isLoggedIn()) {
                showUserMenu(it)
            } else {
                val intent = Intent(this, LoginActivity::class.java)
                loginLauncher.launch(intent)
            }
        }
    }

    private fun showUserMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "软件设置")
        popup.menu.add(0, 2, 1, "退出登录")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val cacheSize = FtpUtils.getPreviewCacheSize(this)

                    AlertDialog.Builder(this)
                        .setTitle("软件设置")
                        .setMessage("当前预览缓存：$cacheSize\n缓存上限：300.00 MB\n\n提示：预览文件时产生的临时缓存会在超过上限时自动清理旧文件。")
                        .setPositiveButton("我知道了", null)
                        .setNeutralButton("手动清理") { _, _ ->
                            // 如果你想加个手动清理功能，可以调用这里
                            val cacheDir = File(externalCacheDir, "ftp_preview")
                            if (cacheDir.exists()) {
                                cacheDir.deleteRecursively() // 递归删除文件夹
                                Toast.makeText(this, "缓存已清空", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .show()
                }
                2 -> {
                    performLogout()
                }
            }
            true
        }
        popup.show()
    }

    private fun performLogout() {
        AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("退出") { _, _ ->
                authService.logout()
                notifyService.stopService()
                NotificationManager.clearMessages()
                refreshUIByLoginState()
                bottomNav.selectedItemId = R.id.nav_qrcode
                switchFragment(QrGeneratorFragment())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshUIByLoginState() {
        val isLoggedIn = authService.isLoggedIn()

        // 登录成功后显示用户名
        tvLogin.text = if (isLoggedIn) authService.getUserName() else "登录"

        ivMessage.visibility = if (isLoggedIn) View.VISIBLE else View.GONE

        val menu = bottomNav.menu
        menu.findItem(R.id.nav_monitor).isVisible = isLoggedIn
        menu.findItem(R.id.nav_file).isVisible = isLoggedIn
        menu.findItem(R.id.nav_config).isVisible = isLoggedIn

        if (!isLoggedIn) {
            tvBadge.visibility = View.GONE
        } else {
            NotificationManager.onUnreadChanged?.invoke(NotificationManager.unreadCount)
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
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_qrcode -> { switchFragment(QrGeneratorFragment()); true }
                R.id.nav_wechat -> { switchFragment(WebListFragment()); true }
                R.id.nav_monitor -> { switchFragment(MonitorFragment()); true }
                R.id.nav_file -> { switchFragment(FileCenterFragment()); true }
                R.id.nav_config -> { switchFragment(ConfigFragment()); true }
                else -> false
            }
        }
    }

    private fun handleInitialPage() {
        bottomNav.selectedItemId = R.id.nav_qrcode
        switchFragment(QrGeneratorFragment())
        if (authService.isLoggedIn()) {
            notifyService.startService()
            lifecycleScope.launch { notifyService.fetchHistory() }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        if (fragment is NotificationFragment) {
            bottomNav.visibility = View.GONE
            topBar.visibility = View.GONE
            transaction.replace(R.id.fragment_container, fragment)
            transaction.addToBackStack("notification")
        } else {
            bottomNav.visibility = View.VISIBLE
            topBar.visibility = View.VISIBLE
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            transaction.replace(R.id.fragment_container, fragment)
        }
        transaction.commit()
    }

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
            // 直接设置 ACTION_APP_NOTIFICATION_SETTINGS，因为 API 30+
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            // 直接设置 Extra，不需要判断版本
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }
}