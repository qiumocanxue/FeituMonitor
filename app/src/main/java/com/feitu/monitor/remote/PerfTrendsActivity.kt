package com.feitu.monitor.remote

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.feitu.monitor.common.ChartMarkerView
import com.feitu.monitor.MonitorFragment
import com.feitu.monitor.R
import com.feitu.monitor.common.models.HistoryPoint
import com.feitu.monitor.common.models.HistoryResponsePayload
import com.feitu.monitor.common.models.MessageEnvelope
import com.feitu.monitor.models.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import androidx.core.content.ContextCompat

class PerfTrendsActivity : AppCompatActivity(), OnMessageReceivedListener {

    private lateinit var lineChart: LineChart
    private val timeLabels = mutableListOf<String>()

    private var rawPoints: List<HistoryPoint> = listOf()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perf_trends)

        lineChart = findViewById(R.id.lineChart)
        findViewById<View>(R.id.btn_back_trends).setOnClickListener { finish() }

        setupChartStyle()

        MonitorFragment.Companion.getWssManager(this)?.let {
            it.addListener(this)
            Log.i("PerfTrends", "历史数据分析链路已就绪")
        }
    }

    private fun setupChartStyle() {
        val secondaryTextColor = ContextCompat.getColor(this, R.color.text_secondary)
        val gridColorHex = "#86868B".toColorInt()
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            setNoDataText("正在从服务器拉取历史性能数据...")
            setExtraOffsets(10f, 10f, 10f, 20f)

            val mv =
                ChartMarkerView(this@PerfTrendsActivity, R.layout.layout_chart_marker, timeLabels) {
                    rawPoints.map { mapOf("cpu" to it.cpu, "ram" to it.ram, "time" to it.time) }
                }
            mv.chartView = this
            this.marker = mv

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = secondaryTextColor
                granularity = 1f
                labelCount = 5
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index >= 0 && index < timeLabels.size) timeLabels[index] else ""
                    }
                }
            }

            axisLeft.apply {
                textColor = secondaryTextColor
                setAxisMinimum(0f)
                setAxisMaximum(105f)
                setLabelCount(6, true)
                gridColor = "#EEEEEE".toColorInt()
            }
            axisRight.isEnabled = false
        }
    }

    override fun onNewMessage(envelope: MessageEnvelope) {
        if (envelope.Type == "HistoryResponse") {
            runOnUiThread {
                try {
                    val payloadJson = gson.toJson(envelope.Payload)
                    val historyData = gson.fromJson(payloadJson, HistoryResponsePayload::class.java)

                    historyData?.Points?.let {
                        updateChartData(it)
                    }
                } catch (e: Exception) {
                    Log.e("PerfTrends", "解析历史数据失败", e)
                }
            }
        }
    }

    private fun updateChartData(points: List<HistoryPoint>) {
        if (points.isEmpty()) return

        try {
            this.rawPoints = points

            timeLabels.clear()
            val cpuEntries = ArrayList<Entry>()
            val ramEntries = ArrayList<Entry>()

            points.forEachIndexed { index, point ->
                val cpu = point.cpu
                val ram = point.ram
                val time = point.time

                timeLabels.add(time)
                cpuEntries.add(Entry(index.toFloat(), cpu))
                ramEntries.add(Entry(index.toFloat(), ram))
            }

            val cpuSet = createDataSet(cpuEntries, "CPU 使用率", "#FF5252")
            val ramSet = createDataSet(ramEntries, "内存占用", "#2196F3")

            val lineData = LineData(cpuSet, ramSet)
            lineChart.data = lineData

            lineData.dataSets.forEach { set ->
                (set as LineDataSet).apply {
                    isHighlightEnabled = true
                    setDrawHorizontalHighlightIndicator(false)
                    highLightColor = Color.parseColor("#66000000") // ✅ 规范写法
                    highlightLineWidth = 1f
                }
            }

            lineChart.animateX(800)
            lineChart.invalidate()
        } catch (e: Exception) {
            Log.e("ChartDebug", "绘图异常: ${e.message}")
        }
    }

    private fun createDataSet(entries: List<Entry>, label: String, colorStr: String): LineDataSet {
        val color = colorStr.toColorInt()
        return LineDataSet(entries, label).apply {
            this.color = color
            setDrawValues(false)
            setDrawCircles(false)
            lineWidth = 2.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(color, Color.TRANSPARENT)
            ).apply { alpha = 40 }
        }
    }

    override fun onNewBinaryMessage(bytes: okio.ByteString) {}
    override fun onStateChange(state: String) {}
    override fun onError(error: String) {}

    override fun onDestroy() {
        super.onDestroy()
        MonitorFragment.Companion.getWssManager(this)?.removeListener(this)
    }
}