package com.novikon.pace.ui.stats

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.novikon.pace.R
import com.novikon.pace.data.RealtimeDatabaseManager
import com.novikon.pace.models.HabitCategory
import kotlinx.coroutines.launch
import java.util.Locale

class StatsReportFragment : Fragment() {

    private lateinit var tvCurrentStreak: TextView
    private lateinit var tvMaxStreak: TextView
    private lateinit var tvMonthlyConsistency: TextView
    private lateinit var tvStarHabit: TextView
    private lateinit var pieChart: PieChart
    private lateinit var barChartMonthly: BarChart
    private lateinit var lineChartYearly: LineChart
    private lateinit var barChartTop5: HorizontalBarChart
    private lateinit var legendContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_stats_report, container, false)

        tvCurrentStreak = view.findViewById(R.id.tv_current_streak)
        tvMaxStreak = view.findViewById(R.id.tv_max_streak)
        tvMonthlyConsistency = view.findViewById(R.id.tv_monthly_consistency)
        tvStarHabit = view.findViewById(R.id.tv_star_habit)
        pieChart = view.findViewById(R.id.pieChart)
        barChartMonthly = view.findViewById(R.id.barChartMonthly)
        lineChartYearly = view.findViewById(R.id.lineChartYearly)
        barChartTop5 = view.findViewById(R.id.barChartTop5)
        legendContainer = view.findViewById(R.id.legendContainer)

        view.findViewById<View>(R.id.btn_download_pdf).setOnClickListener {
            // PDF — siguiente paso
        }

        loadData()
        return view
    }

    private fun loadData() {
        val db = RealtimeDatabaseManager()
        lifecycleScope.launch {
            val logs = db.getHabitLogs()
            val habits = db.getHabits()
            val data = StatsDataAnalyzer.analyze(logs, habits)
            bindData(data)
        }
    }

    private fun bindData(data: StatsData) {
        // ── RESUMEN ───────────────────────────────────────────────────────────
        tvCurrentStreak.text = data.currentStreak.toString()
        tvMaxStreak.text = data.maxStreak.toString()
        tvMonthlyConsistency.text = "${data.monthlyConsistency}%"

        if (data.starHabitName.isNotBlank()) {
            tvStarHabit.text = getString(
                R.string.stats_star_habit,
                data.starHabitEmoji,
                data.starHabitName
            )
            tvStarHabit.visibility = View.VISIBLE
        } else {
            tvStarHabit.visibility = View.GONE
        }

        // ── GRÁFICA TARTA (por categoría) ─────────────────────────────────────
        setupPieChart(data.categoryPercentages)

        // ── GRÁFICA BARRAS (constancia mensual) ───────────────────────────────
        setupMonthlyBarChart(data.monthlyDays)

        // ── GRÁFICA LÍNEA (evolución anual) ───────────────────────────────────
        setupYearlyLineChart(data.yearlyConsistency)

        // ── TOP 5 HÁBITOS ─────────────────────────────────────────────────────
        setupTop5Chart(data.top5Habits)
    }

    private fun setupPieChart(categoryPercentages: Map<HabitCategory, Float>) {
        if (categoryPercentages.isEmpty()) return

        val categoryColors = mapOf(
            HabitCategory.PHYSICAL to Color.parseColor("#4CAF50"),
            HabitCategory.MENTAL to Color.parseColor("#2196F3"),
            HabitCategory.STUDY to Color.parseColor("#FF9800"),
            HabitCategory.ROUTINE to Color.parseColor("#9C27B0"),
            HabitCategory.BAD_HABITS to Color.parseColor("#F44336"),
            HabitCategory.WELLBEING to Color.parseColor("#00BCD4"),
            HabitCategory.CUSTOM to Color.parseColor("#607D8B")
        )

        val categoryLabels = mapOf(
            HabitCategory.PHYSICAL to getString(R.string.category_physical),
            HabitCategory.MENTAL to getString(R.string.category_mental),
            HabitCategory.STUDY to getString(R.string.category_study),
            HabitCategory.ROUTINE to getString(R.string.category_routine),
            HabitCategory.BAD_HABITS to getString(R.string.category_bad_habits),
            HabitCategory.WELLBEING to getString(R.string.category_wellbeing),
            HabitCategory.CUSTOM to getString(R.string.category_custom)
        )

        val entries = categoryPercentages.map { (cat, pct) ->
            PieEntry(pct, categoryLabels[cat] ?: cat.name)
        }
        val colors = categoryPercentages.keys.map { categoryColors[it] ?: Color.GRAY }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 11f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    String.format(Locale.getDefault(), "%.0f%%", value)
            }
            sliceSpace = 2f
        }

        pieChart.apply {
            this.data = PieData(dataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 38f
            setHoleColor(Color.TRANSPARENT)
            setEntryLabelColor(Color.TRANSPARENT)
            setEntryLabelTextSize(0f)
            legend.isEnabled = false
            animateY(800)
            invalidate()
        }

// Leyenda manual
        legendContainer.removeAllViews()
        categoryPercentages.entries.forEachIndexed { index, (cat, pct) ->
            val color = categoryColors[cat] ?: Color.GRAY
            val label = categoryLabels[cat] ?: cat.name

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }

            val dot = android.view.View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(16, 16).also {
                    it.marginEnd = 8
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                }
            }

            val text = TextView(requireContext()).apply {
                text = String.format("%.0f%% %s", pct, label)
                textSize = 11f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }

            row.addView(dot)
            row.addView(text)
            legendContainer.addView(row)
        }
    }

    private fun setupMonthlyBarChart(monthlyDays: Map<Int, Boolean>) {
        if (monthlyDays.isEmpty()) return

        val entries = monthlyDays.entries.sortedBy { it.key }.mapIndexed { i, entry ->
            BarEntry(i.toFloat(), if (entry.value) 1f else 0f)
        }
        val labels = monthlyDays.keys.sorted().map { it.toString() }

        val dataSet = BarDataSet(entries, "").apply {
            colors = monthlyDays.values.sortedBy { it }.map { done ->
                if (done) Color.parseColor("#4CAF50") else Color.parseColor("#E0E0E0")
            }.reversed().let {
                monthlyDays.entries.sortedBy { it.key }
                    .map { if (it.value) Color.parseColor("#4CAF50") else Color.parseColor("#E0E0E0") }
            }
            setDrawValues(false)
        }

        barChartMonthly.apply {
            data = BarData(dataSet).apply { barWidth = 0.8f }
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textSize = 9f
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 1.2f
                setDrawLabels(false)
                setDrawGridLines(false)
            }
            axisRight.isEnabled = false
            animateY(600)
            invalidate()
        }
    }

    private fun setupYearlyLineChart(yearlyConsistency: Map<Int, Float>) {
        if (yearlyConsistency.isEmpty()) return

        val monthLabels = listOf("En", "Feb", "Mar", "Abr", "May", "Jun",
            "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")

        val entries = yearlyConsistency.entries.sortedBy { it.key }.mapIndexed { i, entry ->
            Entry(i.toFloat(), entry.value)
        }
        val labels = yearlyConsistency.keys.sorted().map { monthLabels.getOrElse(it - 1) { it.toString() } }

        val dataSet = LineDataSet(entries, "").apply {
            color = Color.parseColor("#495057")
            setCircleColor(Color.parseColor("#495057"))
            lineWidth = 2f
            circleRadius = 4f
            setDrawFilled(true)
            fillColor = Color.parseColor("#E8E9EA")
            fillAlpha = 100
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        lineChartYearly.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textSize = 9f
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}%"
                }
            }
            axisRight.isEnabled = false
            animateX(800)
            invalidate()
        }
    }

    private fun setupTop5Chart(top5: List<Pair<String, Int>>) {
        if (top5.isEmpty()) return

        val reversed = top5.reversed()
        val entries = reversed.mapIndexed { i, (_, count) ->
            BarEntry(i.toFloat(), count.toFloat())
        }

        // Solo el emoji para el eje
        val emojiLabels = reversed.map { pair ->
            pair.first.split(" ").firstOrNull() ?: pair.first
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#495057")
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = value.toInt().toString()
            }
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.5f

        barChartTop5.apply {
            data = barData
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setExtraOffsets(10f, 0f, 20f, 0f)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(emojiLabels)
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textSize = 14f
                labelCount = emojiLabels.size
            }
            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(false)
            }
            axisRight.isEnabled = false

            // Al pulsar una barra → Snackbar con el nombre completo
            setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                override fun onValueSelected(e: com.github.mikephil.charting.data.Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                    val index = e?.x?.toInt() ?: return
                    val fullName = reversed.getOrNull(index)?.first ?: return
                    com.google.android.material.snackbar.Snackbar
                        .make(barChartTop5, fullName, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                        .show()
                }
                override fun onNothingSelected() {}
            })

            animateX(600)
            invalidate()
        }
    }
}