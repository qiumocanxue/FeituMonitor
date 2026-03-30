package com.feitu.monitor

import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.CompoundBarcodeView

class CustomScannerActivity : AppCompatActivity() {

    private lateinit var capture: CaptureManager
    private lateinit var barcodeScannerView: CompoundBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_custom_scanner)

        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner)

        // 强制开启连续自动对焦
        val cameraSettings = com.journeyapps.barcodescanner.camera.CameraSettings()
        cameraSettings.isAutoFocusEnabled = true       // 开启对焦
        cameraSettings.isContinuousFocusEnabled = true // 开启连续对焦（像录像一样不停对焦）
        barcodeScannerView.cameraSettings = cameraSettings

        capture = CaptureManager(this, barcodeScannerView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()

        startScanAnimation()
    }

    private fun startScanAnimation() {
        val scanLine = findViewById<View>(R.id.view_scan_line)
        // 向上平移 0 到 240dp 的动画
        val animation = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.9f // 向下移动扫描框 90% 的高度
        )
        animation.duration = 2500 // 2.5秒一回
        animation.repeatCount = Animation.INFINITE
        animation.repeatMode = Animation.RESTART
        scanLine.startAnimation(animation)
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }
}