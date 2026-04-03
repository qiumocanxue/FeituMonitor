package com.feitu.monitor

import android.app.DatePickerDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.google.android.material.textfield.TextInputLayout

class QrGeneratorFragment : Fragment() {

    private var selectedDate = ""
    private var currentQrBitmap: Bitmap? = null
    private lateinit var authService: AuthService
    private var tvDecodeResult: TextView? = null

    // 扫码回调处理
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
        val root = inflater.inflate(R.layout.fragment_qr_generator, container, false)
        authService = AuthService(requireContext())

        val scrollView = root.findViewById<ScrollView>(R.id.scroll_view_qr)
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

        val tilQuickFill = root.findViewById<TextInputLayout>(R.id.til_quick_fill)
        val actvQuickFill = root.findViewById<AutoCompleteTextView>(R.id.actv_quick_fill)

        tvDecodeResult = root.findViewById(R.id.tv_decode_result)

        // 加载并设置“加密参数”快速填充
        setupQuickFill(tilQuickFill, actvQuickFill, etHsCode, etKey, etIv)


        // 2. 日期选择
        var lastClickTime: Long = 0
        var lastDateStr = ""

        btnPickDate?.setOnClickListener {
            val c = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(requireContext(), R.style.FeituDatePickerTheme, { _, y, m, d ->
                // 🌟 修复 Warning 108: 明确指定 Locale
                selectedDate = String.format(Locale.US, "%d%02d%02d", y, m + 1, d)
                // 🌟 修复 Warning 109: 使用字符串资源占位符
                btnPickDate.text = getString(R.string.date_prefix, selectedDate)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))

            datePickerDialog.datePicker.init(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)) { _, year, month, day ->
                val now = System.currentTimeMillis()
                val current = "$year$month$day"

                if (now - lastClickTime < 500 && current == lastDateStr) {
                    // 🌟 修复 Warning 119/120: 同上
                    selectedDate = String.format(Locale.US, "%d%02d%02d", year, month + 1, day)
                    btnPickDate.text = getString(R.string.date_prefix, selectedDate)
                    datePickerDialog.dismiss()
                    Toast.makeText(context, "已快速选择: $selectedDate", Toast.LENGTH_SHORT).show()
                }
                lastClickTime = now
                lastDateStr = current
            }
            datePickerDialog.show()
        }

        // 3. 生成二维码
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
            } catch (_: Exception) {
                Toast.makeText(context, "生成失败", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. 扫码与解析
        btnScan?.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("")
                setBeepEnabled(true)
                setOrientationLocked(true)
                addExtra(com.google.zxing.client.android.Intents.Scan.CAMERA_ID, 0)
                setCaptureActivity(CustomScannerActivity::class.java)
            }
            barcodeLauncher.launch(options)
        }

        btnParse?.setOnClickListener {
            val urlToParse = etUrlToDecrypt?.text?.toString()?.trim() ?: ""
            val key = etKey?.text?.toString()?.trim() ?: ""
            val iv = etIv?.text?.toString()?.trim() ?: ""

            if (urlToParse.isEmpty()) {
                Toast.makeText(context, "请先输入或扫描链接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (key.isEmpty() || iv.isEmpty()) {
                Toast.makeText(context, "解析失败：请先填写上方的 AES Key 和 IV", Toast.LENGTH_LONG).show()
                if (key.isEmpty()) etKey?.error = "请填写 Key"
                if (iv.isEmpty()) etIv?.error = "请填写 IV"
                scrollView?.post { scrollView.smoothScrollTo(0, 0) }
                return@setOnClickListener
            }

            if (key.length != 16 || iv.length != 16) {
                Toast.makeText(context, "Key 或 IV 长度不正确（需16位）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performParse(urlToParse)
        }

        // 5. 复制与分享
        tvUrl?.setOnClickListener {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("URL", tvUrl.text))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }

        btnShareQr?.setOnClickListener {
            etAccession?.clearFocus()
            etHsCode?.clearFocus()
            etKey?.clearFocus()
            etIv?.clearFocus()
            scrollView?.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)
            currentQrBitmap?.let { shareBitmapViaFileProvider(it) }
        }

        return root
    }

    private fun setupQuickFill(
        til: TextInputLayout?,
        actv: AutoCompleteTextView?,
        etHs: EditText,
        etKey: EditText,
        etIv: EditText
    ) {
        if (til == null || actv == null) return

        viewLifecycleOwner.lifecycleScope.launch {
            if (!authService.isLoggedIn()) {
                til.visibility = View.GONE
                return@launch
            }

            try {
                // 1. 获取所有参数
                val allParams = authService.getEncryptionParams()

                // 🌟 2. 过滤：二维码界面要求 aesKey 和 aesIv 必须都有值
                val filteredList = allParams.filter {
                    it.aesKey.isNotBlank() && it.aesIv.isNotBlank()
                }

                if (filteredList.isNotEmpty()) {
                    til.visibility = View.VISIBLE
                    val names = filteredList.map { it.hospitalName }
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
                    actv.setAdapter(adapter)

                    actv.setOnClickListener { actv.showDropDown() }

                    actv.setOnItemClickListener { _, _, position, _ ->
                        val selected = filteredList[position]
                        etHs.setText(selected.hscod)
                        etKey.setText(selected.aesKey)
                        etIv.setText(selected.aesIv)
                        Toast.makeText(context, "已快速填充: ${selected.hospitalName}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // 如果过滤后没有符合条件的配置，则隐藏快速填充入口
                    til.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("QrGenerator", "加载加密参数失败", e)
                til.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.updateToolbarTitle("二维码工具")
        val scrollView = view?.findViewById<View>(R.id.scroll_view_qr)
        scrollView?.isFocusableInTouchMode = true
        scrollView?.requestFocus()
    }

    private fun performParse(url: String) {
        try {
            // 🌟 修复 Warning 254: 使用 KTX 扩展
            val uri = url.toUri()
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

            tvDecodeResult?.text = res
            tvDecodeResult?.visibility = View.VISIBLE
        } catch (_: Exception) { }
    }

    private fun encryptAES(data: String, key: String, iv: String): String {
        if (key.length != 16) return data
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(), "AES"), IvParameterSpec(iv.toByteArray()))
        // 🌟 格式化通常也建议指定 Locale
        return cipher.doFinal(data.toByteArray()).joinToString("") { "%02X".format(Locale.US, it) }
    }

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

    private fun decryptAES(encryptedData: String, key: String, iv: String): String {
        return try {
            if (key.length != 16 || iv.length != 16) return "Key/IV 长度需为 16 位"

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(key.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(iv.toByteArray())
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val decodedBytes = hexToByteArray(encryptedData)
            val decryptedBytes = cipher.doFinal(decodedBytes)

            String(decryptedBytes)
        } catch (_: Exception) { // 🌟 修复 Warning 272: 忽略异常
            "解密失败: 请检查 Key/IV 是否正确"
        }
    }

    private fun shareBitmapViaFileProvider(bitmap: Bitmap) {
        try {
            val cachePath = File(requireContext().cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "shared_qr.png")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val authority = "${requireContext().packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(requireContext(), authority, file)

            if (contentUri != null) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "分享影像二维码"))
            }
        } catch (_: Exception) { }
    }
}