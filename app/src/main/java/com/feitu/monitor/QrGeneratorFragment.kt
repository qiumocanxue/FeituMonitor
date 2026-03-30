package com.feitu.monitor

import android.app.DatePickerDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.feitu.monitor.models.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class QrGeneratorFragment : Fragment() {

    private var selectedDate = ""
    private var currentQrBitmap: Bitmap? = null

    // 🌟 扫码器必须定义在成员变量位置
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            view?.findViewById<EditText>(R.id.et_url_to_decrypt)?.setText(result.contents)
            performParse(result.contents)
            Toast.makeText(context, "扫码成功", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 🌟 关键修正：请确保这里的 R.layout. 后面的名字和你 res/layout 下的文件名完全一致
        val root = inflater.inflate(R.layout.fragment_qr_generator, container, false)

        // --- 初始化 UI 组件 (必须使用 root.findViewById) ---
        val scrollView = root.findViewById<ScrollView>(R.id.scroll_view_qr) ?: (root as? ScrollView)
        val etAccession = root.findViewById<EditText>(R.id.et_accession)
        val etHsCode = root.findViewById<EditText>(R.id.et_hscode)
        val etKey = root.findViewById<EditText>(R.id.et_key)
        val etIv = root.findViewById<EditText>(R.id.et_iv)
        val btnPickDate = root.findViewById<Button>(R.id.btn_pick_date)
        val btnGenerate = root.findViewById<Button>(R.id.btn_generate)
        val ivQr = root.findViewById<ImageView>(R.id.iv_qr_result)
        val tvUrl = root.findViewById<TextView>(R.id.tv_url_result)
        val cardResult = root.findViewById<View>(R.id.card_result)
        val btnShareQr = root.findViewById<Button>(R.id.btn_share_qr)
        val btnScan = root.findViewById<Button>(R.id.btn_scan)
        val btnParse = root.findViewById<Button>(R.id.btn_parse)
        val etUrlToDecrypt = root.findViewById<EditText>(R.id.et_url_to_decrypt)
        val tvDecodeResult = root.findViewById<TextView>(R.id.tv_decode_result)
        val tilQuickFill = root.findViewById<TextInputLayout>(R.id.til_quick_fill)
        val actvQuickFill = root.findViewById<AutoCompleteTextView>(R.id.actv_quick_fill)

        // --- 1. 快速填充逻辑 ---
        val authService = AuthService(requireContext())
        if (authService.isLoggedIn()) {
            lifecycleScope.launch {
                try {
                    val configs = authService.getQrConfigs()
                    if (configs.isNotEmpty()) {
                        tilQuickFill?.visibility = View.VISIBLE
                        val names = configs.map { it.hospitalName ?: "未知配置" }
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
                        actvQuickFill?.setAdapter(adapter)

                        // 🌟 修正：显式声明 lambda 参数类型，防止推导失败
                        actvQuickFill?.setOnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
                            val selected = configs[position]
                            etHsCode?.setText(selected.hsCode)
                            etKey?.setText(selected.aesKey ?: "")
                            etIv?.setText(selected.aesIv ?: "")
                        }
                    }
                } catch (e: Exception) { Log.e("FeituQR", "Config error", e) }
            }
        }

        // --- 2. 日期选择 ---
        var lastClickTime: Long = 0
        var lastDateStr = ""

        btnPickDate?.setOnClickListener {
            val c = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(requireContext(), R.style.FeituDatePickerTheme, { _, y, m, d ->
                // 点击“确定”后的逻辑
                selectedDate = String.format("%d%02d%02d", y, m + 1, d)
                btnPickDate.text = "日期: $selectedDate"
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))

            // 🌟 核心：手动初始化 datePicker 以拦截双击
            datePickerDialog.datePicker.init(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)) { _, year, month, day ->
                val now = System.currentTimeMillis()
                val current = "$year$month$day"

                // 如果 500ms 内点击了同一个日期，视为双击
                if (now - lastClickTime < 500 && current == lastDateStr) {
                    selectedDate = String.format("%d%02d%02d", year, month + 1, day)
                    btnPickDate.text = "日期: $selectedDate"
                    datePickerDialog.dismiss() // 自动关闭
                    Toast.makeText(context, "已快速选择: $selectedDate", Toast.LENGTH_SHORT).show()
                }
                lastClickTime = now
                lastDateStr = current
            }
            datePickerDialog.show()
        }

        // --- 3. 生成二维码 ---
        btnGenerate?.setOnClickListener {
            val acc = etAccession?.text?.toString()?.trim() ?: ""
            val hs = etHsCode?.text?.toString()?.trim() ?: ""
            val k = etKey?.text?.toString()?.trim() ?: ""
            val i = etIv?.text?.toString()?.trim() ?: ""

            if (acc.isEmpty() || hs.isEmpty() || selectedDate.isEmpty()) {
                Toast.makeText(context, "请补全信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val encryptedAcc = acc.split(",").filter { it.isNotBlank() }
                    .joinToString(",") { encryptAES(it.trim(), k, i) }

                val url = "https://yyx.ftimage.cn/dimage/index.html?hsCode=$hs&date=$selectedDate&accessionNumber=$encryptedAcc"
                tvUrl?.text = url

                val bitmap = BarcodeEncoder().encodeBitmap(url, BarcodeFormat.QR_CODE, 800, 800)
                currentQrBitmap = bitmap
                ivQr?.setImageBitmap(bitmap)
                cardResult?.visibility = View.VISIBLE

                scrollView?.post { scrollView.smoothScrollTo(0, cardResult?.top ?: 0) }
            } catch (e: Exception) { Toast.makeText(context, "生成失败", Toast.LENGTH_SHORT).show() }
        }

        // --- 4. 扫码与解析 ---
        btnScan?.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("")
                setBeepEnabled(true)
                setOrientationLocked(true)
                // 🌟 确保这个设为 true (虽然默认是 true，但写上更稳)
                addExtra(com.google.zxing.client.android.Intents.Scan.CAMERA_ID, 0) // 使用后置摄像头
                setCaptureActivity(CustomScannerActivity::class.java)
            }
            barcodeLauncher.launch(options)
        }

        btnParse?.setOnClickListener {
            val urlToParse = etUrlToDecrypt?.text?.toString()?.trim() ?: ""
            val key = etKey?.text?.toString()?.trim() ?: ""
            val iv = etIv?.text?.toString()?.trim() ?: ""

            // 1. 先校验 URL 是否为空
            if (urlToParse.isEmpty()) {
                Toast.makeText(context, "请先输入或扫描链接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. 🌟 新增：校验 AES Key 和 IV 是否为空
            if (key.isEmpty() || iv.isEmpty()) {
                // 弹出 Toast 强提示
                Toast.makeText(context, "解析失败：请先填写上方的 AES Key 和 IV", Toast.LENGTH_LONG).show()

                // 给输入框设置红字报错状态
                if (key.isEmpty()) etKey?.error = "请填写 Key"
                if (iv.isEmpty()) etIv?.error = "请填写 IV"

                // 自动滚动到页面顶部，方便用户看到 Key/IV 输入框
                scrollView?.post {
                    scrollView.smoothScrollTo(0, 0)
                }
                return@setOnClickListener
            }

            // 3. 校验长度（AES 通常要求 16 位）
            if (key.length != 16 || iv.length != 16) {
                Toast.makeText(context, "Key 或 IV 长度不正确（需16位）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 全部校验通过，执行解析
            Log.d("FeituDebug", "校验通过，开始解析...")
            performParse(urlToParse)
        }

        // --- 5. 复制与分享 ---
        tvUrl?.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", tvUrl.text))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }

        btnShareQr?.setOnClickListener {
            // 1. 清除所有输入框焦点
            etAccession?.clearFocus()
            etHsCode?.clearFocus()
            etKey?.clearFocus()
            etIv?.clearFocus()

            // 2. 让 ScrollView 拿走焦点，防止焦点跳回第一个输入框
            scrollView?.requestFocus()

            // 3. 隐藏键盘
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            // 🌟 修复编译报错：将 view.windowToken 改为 view?.windowToken
            imm.hideSoftInputFromWindow(view?.windowToken, 0)

            // 4. 执行分享逻辑
            currentQrBitmap?.let { shareBitmapViaFileProvider(it) }
        }

        return root
    }

    override fun onResume() {
        super.onResume()

        // 🌟 1. 更新顶部标题为“二维码工具”
        // (activity as? MainActivity) 是将当前的 activity 强制识别为你的 MainActivity
        (activity as? MainActivity)?.updateToolbarTitle("二维码工具")

        // 🌟 2. 拦截焦点逻辑（保持你之前的代码）
        val scrollView = view?.findViewById<View>(R.id.scroll_view_qr)
        scrollView?.isFocusableInTouchMode = true
        scrollView?.requestFocus()
    }

    private fun performParse(url: String) {
        try {
            val uri = Uri.parse(url)
            val hs = uri.getQueryParameter("hsCode") ?: ""
            val dt = uri.getQueryParameter("date") ?: ""
            val acc = uri.getQueryParameter("accessionNumber") ?: ""

            var res = "医院ID: $hs\n日期: $dt\n查询内容: $acc"

            val k = view?.findViewById<EditText>(R.id.et_key)?.text?.toString()?.trim() ?: ""
            val i = view?.findViewById<EditText>(R.id.et_iv)?.text?.toString()?.trim() ?: ""

            if (acc.length > 20 && k.length == 16) {
                val dec = decryptAES(acc, k, i)
                res += "\n\n解密结果: $dec"
            }

            val tv = view?.findViewById<TextView>(R.id.tv_decode_result)
            tv?.text = res
            tv?.visibility = View.VISIBLE
        } catch (e: Exception) { }
    }

    private fun encryptAES(data: String, key: String, iv: String): String {
        if (key.length != 16) return data
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(), "AES"), IvParameterSpec(iv.toByteArray()))
        return cipher.doFinal(data.toByteArray()).joinToString("") { "%02X".format(it) }
    }

    // 1. 核心：将十六进制字符串转为字节数组
    private fun hexToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) +
                    Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    // 2. 修改后的解密函数
    private fun decryptAES(encryptedData: String, key: String, iv: String): String {
        return try {
            // 校验：Key 和 IV 必须是 16 位
            if (key.length != 16 || iv.length != 16) return "Key/IV 长度需为 16 位"

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(iv.toByteArray())
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            // 🌟 关键修改：改用 hexToByteArray 而不是 Base64.decode
            val decodedBytes = hexToByteArray(encryptedData)
            val decryptedBytes = cipher.doFinal(decodedBytes)

            String(decryptedBytes)
        } catch (e: Exception) {
            Log.e("FeituDecrypt", "Error: ${e.message}")
            "解密失败: 请检查 Key/IV 是否正确"
        }
    }

    private fun shareBitmapViaFileProvider(bitmap: Bitmap) {
        try {
            // 1. 确保目录存在
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_qr.png")

            // 2. 写入文件
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            // 3. 获取 URI
            val authority = "${requireContext().packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(requireContext(), authority, file)

            if (contentUri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    // 🌟 核心：必须授予临时读权限，否则接收方（如微信）打不开文件
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "分享影像二维码"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}