package com.feitu.monitor

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.feitu.monitor.models.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson

class PerfTrendsActivity : AppCompatActivity(), OnMessageReceivedListener {

    private lateinit var lineChart: LineChart
    private val timeLabels = mutableListOf<String>()

    private var rawPoints: List<HistoryPoint> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perf_trends)

        lineChart = findViewById(R.id.lineChart)
        findViewById<View>(R.id.btn_back_trends).setOnClickListener { finish() }

        setupChartStyle()

        MonitorFragment.getWssManager(this)?.let {
            it.listener = this
            Log.i("PerfTrends", "历史数据分析链路已就绪")
        }
    }

    private fun setupChartStyle() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            setNoDataText("正在从服务器拉取历史性能数据...")
            setExtraOffsets(10f, 10f, 10f, 20f)

            val mv = ChartMarkerView(this@PerfTrendsActivity, R.layout.layout_chart_marker, timeLabels) {
                rawPoints.map { mapOf("cpu" to it.cpu, "ram" to it.ram, "time" to it.time) }
            }
            mv.chartView = this
            this.marker = mv

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = "#999999".toColorInt()
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
                textColor = "#999999".toColorInt()
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
                    val json = Gson().toJson(envelope.Payload)
                    val historyData = Gson().fromJson(json, HistoryResponsePayload::class.java)

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
                    highLightColor = "#66000000".toColorInt()
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

    override fun onStateChange(state: String) {}
    override fun onError(error: String) {}

    override fun onDestroy() {
        super.onDestroy()
        MonitorFragment.getWssManager(this)?.listener = null
    }
}