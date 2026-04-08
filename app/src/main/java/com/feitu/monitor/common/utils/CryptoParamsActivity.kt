package com.feitu.monitor.common.utils

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.feitu.monitor.R
import com.feitu.monitor.auth.AuthService
import com.feitu.monitor.config.models.EncryptionParams
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class CryptoParamsActivity : AppCompatActivity() {

    private lateinit var authService: AuthService
    private lateinit var adapter: CryptoParamsAdapter
    private val paramsList = mutableListOf<EncryptionParams>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🌟 1. 开启 Edge-to-Edge 沉浸式，允许内容绘制到刘海区域
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_crypto_params)
        authService = AuthService(this)

        // 🌟 2. 适配刘海与状态栏高度
        val statusBarSpacer = findViewById<View>(R.id.status_bar_spacer)
        val fab = findViewById<FloatingActionButton>(R.id.fab_add_param)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout_crypto)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 将占位符高度设为状态栏（包含刘海）的高度
            statusBarSpacer.apply {
                layoutParams.height = systemBars.top
                visibility = if (systemBars.top > 0) View.VISIBLE else View.GONE
            }

            // 🌟 3. 同时适配底部手势导航条，防止 FAB 被遮挡
            val lp = fab.layoutParams as RelativeLayout.LayoutParams
            lp.bottomMargin = systemBars.bottom + (24 * resources.displayMetrics.density).toInt()
            fab.layoutParams = lp

            insets
        }

        // --- 原有逻辑保持不变 ---
        val rvParams = findViewById<RecyclerView>(R.id.rv_crypto_params)
        adapter = CryptoParamsAdapter(paramsList) { item -> showEditDialog(item) }
        rvParams.layoutManager = LinearLayoutManager(this)
        rvParams.adapter = adapter

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        fab.setOnClickListener { showEditDialog(null) }

        refreshData()
    }

    private fun refreshData() {
        lifecycleScope.launch {
            val data = authService.getEncryptionParams()
            paramsList.clear()
            paramsList.addAll(data)
            adapter.notifyDataSetChanged()
        }
    }

    private fun showEditDialog(item: EncryptionParams?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_crypto_params, null)

        val etHospital = dialogView.findViewById<EditText>(R.id.et_hospital_name)
        val etHsCode = dialogView.findViewById<EditText>(R.id.et_hscod)
        val etAesKey = dialogView.findViewById<EditText>(R.id.et_aes_key)
        val etAesIv = dialogView.findViewById<EditText>(R.id.et_aes_iv)
        val etAk = dialogView.findViewById<EditText>(R.id.et_access_key)
        val etAs = dialogView.findViewById<EditText>(R.id.et_access_secret)

        item?.let {
            etHospital.setText(it.hospitalName)
            etHsCode.setText(it.hscod)
            etAesKey.setText(it.aesKey)
            etAesIv.setText(it.aesIv)
            etAk.setText(it.accessKey)
            etAs.setText(it.accessSecret)
        }

        val dialog = MaterialAlertDialogBuilder(this, R.style.FeituDialogTheme)
            .setTitle(if (item == null) "新增加密参数" else "编辑加密参数")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etHospital.text.toString().trim()
            val hs = etHsCode.text.toString().trim()

            if (name.isEmpty() || hs.isEmpty()) {
                Toast.makeText(this, "名称和编码不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🌟 核心逻辑：如果是新增，item 为 null，则 id 为 0
            val targetId = item?.id ?: 0

            val newParams = EncryptionParams(
                id = targetId,
                hospitalName = name,
                hscod = hs,
                aesKey = etAesKey.text.toString().trim(),
                aesIv = etAesIv.text.toString().trim(),
                accessKey = etAk.text.toString().trim(),
                accessSecret = etAs.text.toString().trim()
            )

            // 📝 [新增日志输出]
            Log.d("FeituAPI", "====================================")
            Log.d("FeituAPI", "📤 [准备提交数据] 操作类型: ${if (item == null) "新增" else "修改"}")
            Log.d("FeituAPI", "🆔 提交 ID: ${newParams.id}")
            Log.d("FeituAPI", "🏥 医院名称: ${newParams.hospitalName}")
            Log.d("FeituAPI", "📊 完整对象: $newParams")
            Log.d("FeituAPI", "====================================")

            lifecycleScope.launch {
                val success = authService.saveEncryptionParams(newParams)
                if (success) {
                    Toast.makeText(this@CryptoParamsActivity, "保存成功", Toast.LENGTH_SHORT).show()
                    refreshData()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this@CryptoParamsActivity, "保存失败，请检查 Logcat 日志", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class CryptoParamsAdapter(
        private val items: List<EncryptionParams>,
        private val onClick: (EncryptionParams) -> Unit
    ) : RecyclerView.Adapter<CryptoParamsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_item_hospital_name)
            val tvHsCode: TextView = view.findViewById(R.id.tv_item_hscod)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_crypto_param, parent, false)
            return ViewHolder(v)
        }

        // CryptoParamsActivity 内部的适配器
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.hospitalName
            holder.tvHsCode.text = "医院编码: ${item.hscod}"

            holder.itemView.setOnClickListener { onClick(item) }

            // 🌟 修复长按逻辑
            holder.itemView.setOnLongClickListener {
                MaterialAlertDialogBuilder(this@CryptoParamsActivity)
                    .setTitle("确认删除")
                    .setMessage("确定要删除 [${item.hospitalName}] 吗？")
                    .setNegativeButton("取消", null)
                    // 🌟 修复点：显式指定 (dialog: DialogInterface, which: Int)
                    .setPositiveButton("删除") { dialog: DialogInterface, which: Int ->
                        lifecycleScope.launch {
                            val success = authService.deleteEncryptionParam(item.id)
                            if (success) {
                                Toast.makeText(this@CryptoParamsActivity, "删除成功", Toast.LENGTH_SHORT).show()
                                refreshData()
                            } else {
                                Toast.makeText(this@CryptoParamsActivity, "删除失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .show()
                true
            }
        }

        override fun getItemCount() = items.size
    }
}