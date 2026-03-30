package com.feitu.monitor

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.feitu.monitor.models.OaConfig
import kotlinx.coroutines.launch
import java.security.MessageDigest

class WebListFragment : Fragment(R.layout.fragment_web_list) {

    // 使用 lateinit 必须确保在 onViewCreated 中全部初始化
    private lateinit var hsCodeEt: EditText
    private lateinit var accessKeyEt: EditText
    private lateinit var accessSecretEt: EditText
    private lateinit var queryValueEt: EditText
    private lateinit var webView: WebView

    private var selectedQueryKey = "patNo"
    private var generatedUrl = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 顶部状态栏占位适配
        val statusBarSpacer = view.findViewById<View>(R.id.status_bar_spacer)
        statusBarSpacer?.layoutParams?.let { params ->
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                params.height = resources.getDimensionPixelSize(resourceId)
                statusBarSpacer.layoutParams = params
                statusBarSpacer.visibility = View.VISIBLE // 只有找到了才显示
            }
        }

        // 2. 初始化 View (必须与新 XML 中的 ID 完全对应)
        hsCodeEt = view.findViewById(R.id.et_hs_code)
        accessKeyEt = view.findViewById(R.id.et_access_key)
        accessSecretEt = view.findViewById(R.id.et_access_secret)
        queryValueEt = view.findViewById(R.id.et_query_value)
        webView = view.findViewById(R.id.web_view)

        setupWebView()
        setupRadioGroup(view)

        // 3. 按钮点击事件
        view.findViewById<Button>(R.id.btn_generate).setOnClickListener { generateLink(view) }
        view.findViewById<Button>(R.id.btn_copy).setOnClickListener { copyToClipboard() }
        view.findViewById<Button>(R.id.btn_browser).setOnClickListener { openInBrowser() }

        // 4. 加载配置列表
        loadConfigs(view)
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.updateToolbarTitle("公众号工具")
    }

    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
    }

    private fun setupRadioGroup(view: View) {
        val radioGroup = view.findViewById<RadioGroup>(R.id.rg_query_key)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedQueryKey = when (checkedId) {
                R.id.rb_patNo -> "patNo"
                R.id.rb_clinicId -> "clinicId"
                R.id.rb_patIcCard -> "patIcCard" // 🌟 身份证
                R.id.rb_hisId2 -> "hisId2"       // 🌟 唯一码
                else -> "patNo"
            }
            // 打印一下，确认选中的 Key 对不对
            Log.d("FeituWeb", "当前选择的查询维度: $selectedQueryKey")
        }
    }

    private fun calculateSign(timeStamp: String): String {
        val hsCode = hsCodeEt.text.toString().trim()
        val key = accessKeyEt.text.toString().trim()
        val secret = accessSecretEt.text.toString().trim()
        val value = queryValueEt.text.toString().trim()

        val raw = when (selectedQueryKey) {
            "patNo" -> {
                // accesskey + hospitalId + patNo + timeStamp + accessSecret
                "accesskey${key}hospitalId${hsCode}patNo${value}timeStamp${timeStamp}accessSecret${secret}"
            }
            "clinicId" -> {
                // accesskey + clinicId + hospitalId + timeStamp + accessSecret
                "accesskey${key}clinicId${value}hospitalId${hsCode}timeStamp${timeStamp}accessSecret${secret}"
            }
            "patIcCard" -> {
                // accesskey + hospitalId + patIcCard + timeStamp + accessSecret
                "accesskey${key}hospitalId${hsCode}patIcCard${value}timeStamp${timeStamp}accessSecret${secret}"
            }
            "hisId2" -> {
                // accesskey + hisId2 + hospitalId + timeStamp + accessSecret
                "accesskey${key}hisId2${value}hospitalId${hsCode}timeStamp${timeStamp}accessSecret${secret}"
            }
            else -> ""
        }

        Log.d("FeituWeb", "待签名原始字符串: $raw")
        return md5(raw).uppercase()
    }

    private fun generateLink(view: View) {
        val timeStamp = System.currentTimeMillis().toString()
        val sign = calculateSign(timeStamp)

        val builder = Uri.parse("https://yyx.ftimage.cn/open/index.html").buildUpon()
            .appendQueryParameter("hsCode", hsCodeEt.text.toString().trim())
            .appendQueryParameter("queryKey", selectedQueryKey)
            .appendQueryParameter("queryValue", queryValueEt.text.toString().trim())
            .appendQueryParameter("accessKey", accessKeyEt.text.toString().trim())
            .appendQueryParameter("timeStamp", timeStamp)
            .appendQueryParameter("sign", sign)

        generatedUrl = builder.build().toString()

        view.findViewById<View>(R.id.layout_result).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.tv_generated_url).text = generatedUrl

        webView.loadUrl(generatedUrl)
    }

    private fun loadConfigs(view: View) {
        // 1. 这里的 ID 必须对应 XML 里的 AutoCompleteTextView
        val autoCompleteTextView = view.findViewById<AutoCompleteTextView>(R.id.spinner_configs)
        val authService = AuthService(requireContext())

        lifecycleScope.launch {
            try {
                // 2. 从网络获取配置
                val configs = authService.getOaConfigs()

                // 打印一下，看看后台到底有没有给数据
                Log.d("FeituWeb", "获取到 OA 配置数量: ${configs.size}")

                if (configs.isNotEmpty()) {
                    // 3. 数据回来后，再创建并设置适配器
                    val names = configs.map { it.hospitalName ?: "未知医院" }
                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line, // 使用系统标准简易列表样式
                        names
                    )
                    autoCompleteTextView.setAdapter(adapter)

                    // 4. 设置点击填充逻辑
                    autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
                        val selected = configs[position]
                        hsCodeEt.setText(selected.hsCode ?: "")
                        accessKeyEt.setText(selected.accessKey ?: "")
                        accessSecretEt.setText(selected.accessSecret ?: "")

                        Toast.makeText(context, "已加载: ${selected.hospitalName}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    autoCompleteTextView.setText("暂无预设配置")
                }
            } catch (e: Exception) {
                Log.e("FeituWeb", "加载配置异常: ${e.message}")
                autoCompleteTextView.setText("加载失败")
            }
        }
    }

    private fun md5(content: String): String {
        val hash = MessageDigest.getInstance("MD5").digest(content.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun copyToClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", generatedUrl))
        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
    }

    private fun openInBrowser() {
        if (generatedUrl.isNotEmpty()) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(generatedUrl)))
        }
    }
}