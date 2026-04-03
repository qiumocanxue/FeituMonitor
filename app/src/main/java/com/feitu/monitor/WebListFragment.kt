package com.feitu.monitor

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.net.toUri // 🌟 需要导入 KTX 扩展
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.security.MessageDigest

class WebListFragment : Fragment(R.layout.fragment_web_list) {

    private lateinit var hsCodeEt: EditText
    private lateinit var accessKeyEt: EditText
    private lateinit var accessSecretEt: EditText
    private lateinit var queryValueEt: EditText
    private lateinit var webView: WebView
    private lateinit var authService: AuthService

    private var selectedQueryKey = "patNo"
    private var generatedUrl = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authService = AuthService(requireContext())

        val statusBarSpacer = view.findViewById<View>(R.id.status_bar_spacer)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarSpacer?.apply {
                layoutParams.height = systemBars.top
                visibility = View.VISIBLE
            }
            insets
        }

        // 初始化 View
        hsCodeEt = view.findViewById(R.id.et_hs_code)
        accessKeyEt = view.findViewById(R.id.et_access_key)
        accessSecretEt = view.findViewById(R.id.et_access_secret)
        queryValueEt = view.findViewById(R.id.et_query_value)
        webView = view.findViewById(R.id.web_view)

        setupWebView()
        setupRadioGroup(view)

        view.findViewById<Button>(R.id.btn_generate).setOnClickListener { generateLink(view) }
        view.findViewById<Button>(R.id.btn_copy).setOnClickListener { copyToClipboard() }
        view.findViewById<Button>(R.id.btn_browser).setOnClickListener { openInBrowser() }

        setupQuickFill(view)

    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.updateToolbarTitle("公众号工具")
    }

    // 🌟 3. 修复 WebView 安全警告
    @SuppressLint("SetJavaScriptEnabled") // 明确由于业务需要开启 JS，并确保 URL 安全
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            // 额外安全设置
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.webViewClient = WebViewClient()
    }

    private fun setupRadioGroup(view: View) {
        val radioGroup = view.findViewById<RadioGroup>(R.id.rg_query_key)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedQueryKey = when (checkedId) {
                R.id.rb_patNo -> "patNo"
                R.id.rb_clinicId -> "clinicId"
                R.id.rb_patIcCard -> "patIcCard"
                R.id.rb_hisId2 -> "hisId2"
                else -> "patNo"
            }
        }
    }

    private fun setupQuickFill(view: View) {
        val cardQuickFill = view.findViewById<View>(R.id.card_quick_fill)
        val actv = view.findViewById<AutoCompleteTextView>(R.id.spinner_configs)

        if (!authService.isLoggedIn()) {
            cardQuickFill?.visibility = View.GONE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val allParams = authService.getEncryptionParams()

                // 🌟 核心：按需过滤 (公众号需要 Access 接口配置)
                val filteredList = allParams.filter {
                    it.accessKey.isNotBlank() && it.accessSecret.isNotBlank()
                }

                if (filteredList.isNotEmpty()) {
                    cardQuickFill?.visibility = View.VISIBLE
                    val names = filteredList.map { it.hospitalName }
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
                    actv.setAdapter(adapter)

                    // 点击即显示下拉
                    actv.setOnClickListener { actv.showDropDown() }

                    actv.setOnItemClickListener { _, _, position, _ ->
                        val selected = filteredList[position]
                        hsCodeEt.setText(selected.hscod)
                        accessKeyEt.setText(selected.accessKey)
                        accessSecretEt.setText(selected.accessSecret)
                        Toast.makeText(requireContext(), "已同步: ${selected.hospitalName}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    cardQuickFill?.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("WebListFragment", "setupQuickFill failed", e)
                cardQuickFill?.visibility = View.GONE
            }
        }
    }

    private fun calculateSign(timeStamp: String): String {
        val hsCode = hsCodeEt.text.toString().trim()
        val key = accessKeyEt.text.toString().trim()
        val secret = accessSecretEt.text.toString().trim()
        val value = queryValueEt.text.toString().trim()

        val raw = when (selectedQueryKey) {
            "patNo" -> "accesskey${key}hospitalId${hsCode}patNo${value}timeStamp${timeStamp}accessSecret${secret}"
            "clinicId" -> "accesskey${key}clinicId${value}hospitalId${hsCode}timeStamp${timeStamp}accessSecret${secret}"
            "patIcCard" -> "accesskey${key}hospitalId${hsCode}patIcCard${value}timeStamp${timeStamp}accessSecret${secret}"
            "hisId2" -> "accesskey${key}hisId2${value}hospitalId${hsCode}timeStamp${timeStamp}accessSecret${secret}"
            else -> ""
        }
        return md5(raw).uppercase()
    }

    private fun generateLink(view: View) {
        val timeStamp = System.currentTimeMillis().toString()
        val sign = calculateSign(timeStamp)

        // 🌟 4. 修复 KTX 扩展警告：使用 .toUri()
        val baseUri = "https://yyx.ftimage.cn/open/index.html".toUri()
        val builder = baseUri.buildUpon()
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
            // 🌟 5. 修复 KTX 扩展警告：使用 .toUri()
            startActivity(Intent(Intent.ACTION_VIEW, generatedUrl.toUri()))
        }
    }
}