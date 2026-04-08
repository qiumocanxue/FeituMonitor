package com.feitu.monitor.common

import android.annotation.SuppressLint
import android.content.Context
import android.widget.TextView
import com.feitu.monitor.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

@SuppressLint("ViewConstructor")
class ChartMarkerView(
    context: Context,
    layoutResource: Int,
    private val timeLabels: List<String>,
    private val allPoints: () -> List<Map<String, Any>>
) : MarkerView(context, layoutResource) {

    private val tvTime = findViewById<TextView>(R.id.tv_marker_time)
    private val tvCpu = findViewById<TextView>(R.id.tv_marker_cpu)
    private val tvRam = findViewById<TextView>(R.id.tv_marker_ram)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val index = e?.x?.toInt() ?: 0
        val points = allPoints()

        if (index >= 0 && index < points.size) {
            val data = points[index]

            val time = timeLabels.getOrNull(index) ?: ""
            tvTime.text = context.getString(R.string.marker_time, time)

            val cpuVal = data["cpu"]?.toString() ?: "0"
            tvCpu.text = context.getString(R.string.marker_cpu, cpuVal)

            val ramVal = data["ram"]?.toString() ?: "0"
            tvRam.text = context.getString(R.string.marker_ram, ramVal)
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // 让气泡显示在手指上方中心 (水平居中偏移，垂直向上偏移并留出 20dp 间距)
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat() - 20f)
    }
}