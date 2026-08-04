package com.novikon.pace.ui.stats

import android.graphics.Bitmap
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
import com.novikon.pace.billing.PremiumGate
import com.novikon.pace.data.RealtimeDatabaseManager
import com.novikon.pace.models.AdviceContent
import com.novikon.pace.models.HabitCategory
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var statsData: StatsData? = null
    private var userName: String = ""
    private var firstInstallDate: String = ""

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
            if (PremiumGate.isPremium(requireContext())) {
                generateAndSharePdf()
            } else {
                PremiumGate.showGate(requireActivity())
            }
        }

        loadData()
        return view
    }

    private fun loadData() {
        val db = RealtimeDatabaseManager()
        lifecycleScope.launch {
            val logs = db.getHabitLogs()
            val habits = db.getHabits()
            userName = db.getUserDisplayName() ?: ""
            firstInstallDate = db.getFirstInstallDate() ?: ""
            val data = StatsDataAnalyzer.analyze(logs, habits)
            bindData(data)
        }
    }

    private fun bindData(data: StatsData) {
        statsData = data
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

        // ── GRÁFICA TARTA (por categoría) — esta es la que se ve en la APP ──────
        setupPieChart(data.categoryPercentages)

        // ── GRÁFICA BARRAS (constancia mensual) — la que se ve en la APP ────────
        setupMonthlyBarChart(data.monthlyDays)

        // ── GRÁFICA LÍNEA (evolución anual) — la que se ve en la APP ────────────
        setupYearlyLineChart(data.yearlyConsistency)

        // ── TOP 5 HÁBITOS — la que se ve en la APP ───────────────────────────────
        setupTop5Chart(data.top5Habits)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CHARTS DE PANTALLA (los que ve el usuario en la app, con animaciones,
    // listener de clicks, etc.). NO SE DEBEN TOCAR desde la generación del PDF.
    // ═══════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════
    // CHARTS PARA EL PDF (instancias NUEVAS e independientes, nunca las de
    // pantalla). Cada build*ForPdf() crea un chart desde cero con los mismos
    // datos y estilo que su gemelo de arriba, pero sin animaciones (no hacen
    // falta offscreen) y sin tocar en ningún momento la View real.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Renderiza un chart de MPAndroidChart "offscreen" a la resolución exacta deseada.
     * Debe llamarse desde el hilo principal (Main), nunca desde IO, porque measure/layout/draw
     * son operaciones de View.
     *
     * IMPORTANTE: el chart que le pases aquí debe ser una instancia recién creada con
     * build*ForPdf() — si le pasas una View que ya está en pantalla (ej. barChartMonthly),
     * measure()+layout() le cambiarán el tamaño REAL en la app y se quedará encogida.
     *
     * scale = factor de sobre-muestreo para que se vea nítido incluso impreso (2.5f = alta resolución)
     */
    private fun <T : com.github.mikephil.charting.charts.Chart<*>> renderChartOffscreen(
        chart: T,
        widthPt: Int,
        heightPt: Int,
        scale: Float = 2.5f
    ): Bitmap {
        val w = (widthPt * scale).toInt()
        val h = (heightPt * scale).toInt()

        chart.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        chart.layout(0, 0, w, h)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(Color.WHITE) // evita fondo negro/transparente al imprimir
        chart.draw(canvas)
        return bmp
    }

    /** Gemelo offscreen de setupPieChart(), pero como instancia nueva para el PDF. */
    private fun buildPieChartForPdf(categoryPercentages: Map<HabitCategory, Float>): PieChart {
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

        val entries = categoryPercentages.map { (cat, pct) -> PieEntry(pct, categoryLabels[cat] ?: cat.name) }
        val colors = categoryPercentages.keys.map { categoryColors[it] ?: Color.GRAY }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 11f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = String.format(Locale.getDefault(), "%.0f%%", value)
            }
            sliceSpace = 2f
        }

        val chart = PieChart(requireContext())
        chart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 38f
            setHoleColor(Color.TRANSPARENT)
            setEntryLabelColor(Color.TRANSPARENT)
            setEntryLabelTextSize(0f)
            legend.isEnabled = false
            // Sin animateY(): en un chart offscreen no se ve, y capturar el bitmap
            // mientras "animaría" puede dar un frame incompleto.
        }
        return chart
    }

    /** Gemelo offscreen de setupMonthlyBarChart(), como instancia nueva para el PDF. */
    private fun buildMonthlyBarChartForPdf(monthlyDays: Map<Int, Boolean>): BarChart {
        val entries = monthlyDays.entries.sortedBy { it.key }.mapIndexed { i, entry ->
            BarEntry(i.toFloat(), if (entry.value) 1f else 0f)
        }
        val labels = monthlyDays.keys.sorted().map { it.toString() }

        val dataSet = BarDataSet(entries, "").apply {
            colors = monthlyDays.entries.sortedBy { it.key }
                .map { if (it.value) Color.parseColor("#4CAF50") else Color.parseColor("#E0E0E0") }
            setDrawValues(false)
        }

        val chart = BarChart(requireContext())
        chart.apply {
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
        }
        return chart
    }

    /** Gemelo offscreen de setupYearlyLineChart(), como instancia nueva para el PDF. */
    private fun buildYearlyLineChartForPdf(yearlyConsistency: Map<Int, Float>): LineChart {
        val monthLabels = listOf("En", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        val entries = yearlyConsistency.entries.sortedBy { it.key }.mapIndexed { i, entry -> Entry(i.toFloat(), entry.value) }
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

        val chart = LineChart(requireContext())
        chart.apply {
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
        }
        return chart
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GENERACIÓN DEL PDF
    // ═══════════════════════════════════════════════════════════════════════

    private fun generateAndSharePdf() {
        val currentData = statsData ?: return
        val advice = (activity as? StatsActivity)?.adviceContent

        // 1) Generar TODOS los bitmaps en Main, usando instancias NUEVAS de cada
        //    chart (build*ForPdf), nunca las Views que están en pantalla.
        val pieChartPdf = buildPieChartForPdf(currentData.categoryPercentages)
        val pieBmp = renderChartOffscreen(pieChartPdf, widthPt = 190, heightPt = 190) // cuadrado, ratio real del donut

        val monthlyChartPdf = buildMonthlyBarChartForPdf(currentData.monthlyDays)
        val monthlyBmp = renderChartOffscreen(monthlyChartPdf, widthPt = 250, heightPt = 150)

        val yearlyChartPdf = buildYearlyLineChartForPdf(currentData.yearlyConsistency)
        val yearlyBmp = renderChartOffscreen(yearlyChartPdf, widthPt = 250, heightPt = 150)

        // 2) Construir el PDF en IO, pasándole los bitmaps ya listos (nunca Views)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val pdfFile = buildPdf(currentData, advice, pieBmp, monthlyBmp, yearlyBmp)
            withContext(kotlinx.coroutines.Dispatchers.Main) { sharePdf(pdfFile) }
        }
    }

    private fun buildPdf(
        data: StatsData,
        advice: AdviceContent?,
        pieBmp: Bitmap,
        monthlyBmp: Bitmap,
        yearlyBmp: Bitmap
    ): java.io.File {
        val document = android.graphics.pdf.PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1

        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val today = sdf.format(java.util.Date())

        // Márgenes de seguridad para evitar recortes en visores (Xiaomi, etc.)
        val margin = 24f // ~17pt margen en todos los lados
        val safeWidth = pageWidth - 2 * margin
        val safeHeight = pageHeight - 2 * margin

        // Colores
        val colorDark = android.graphics.Color.parseColor("#212529")
        val colorAccent = android.graphics.Color.parseColor("#495057")
        val colorSecondary = android.graphics.Color.parseColor("#6C757D")
        val colorLight = android.graphics.Color.parseColor("#F8F9FA")
        val colorWhite = android.graphics.Color.WHITE

        // Paints
        fun boldPaint(size: Float, color: Int = colorDark) = android.graphics.Paint().apply {
            textSize = size; typeface = android.graphics.Typeface.DEFAULT_BOLD
            this.color = color; isAntiAlias = true
        }
        fun regularPaint(size: Float, color: Int = colorDark) = android.graphics.Paint().apply {
            textSize = size; this.color = color; isAntiAlias = true
        }
        fun fillPaint(color: Int) = android.graphics.Paint().apply {
            this.color = color; style = android.graphics.Paint.Style.FILL; isAntiAlias = true
        }

        // ── PORTADA ──────────────────────────────────────────────────────────
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // Fondo blanco arriba, bloque oscuro abajo
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), fillPaint(colorWhite))
        canvas.drawRect(0f, pageHeight * 0.55f, pageWidth.toFloat(), pageHeight.toFloat(), fillPaint(colorAccent))

        // Logo centrado en zona blanca
        try {
            val logoBmp = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_pace_logo)
            val logoSize = 100
            val scaledLogo = android.graphics.Bitmap.createScaledBitmap(logoBmp, logoSize, logoSize, true)
            canvas.drawBitmap(scaledLogo, (pageWidth - logoSize) / 2f, 120f, null)
        } catch (e: Exception) { }

        // Título
        val titleP = boldPaint(42f, colorDark)
        val titleText = "Pace"
        val titleW = titleP.measureText(titleText)
        canvas.drawText(titleText, (pageWidth - titleW) / 2f, 260f, titleP)

        val subP = regularPaint(16f, colorSecondary)
        val subText = getString(R.string.pdf_tagline)
        val subW = subP.measureText(subText)
        canvas.drawText(subText, (pageWidth - subW) / 2f, 290f, subP)

        // Línea separadora
        val linePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#DEE2E6")
            strokeWidth = 1f
        }
        canvas.drawLine(100f, 315f, pageWidth - 100f, 315f, linePaint)

        // Nombre usuario y fechas en zona oscura
        if (userName.isNotBlank()) {
            val nameP = boldPaint(28f, colorWhite)
            val nameW = nameP.measureText(userName)
            canvas.drawText(userName, (pageWidth - nameW) / 2f, pageHeight * 0.65f, nameP)
        }

        val dateP = regularPaint(13f, android.graphics.Color.parseColor("#ADB5BD"))
        val dateText = if (firstInstallDate.isNotBlank())
            "${getString(R.string.pdf_from)} $firstInstallDate  →  $today"
        else today
        val dateW = dateP.measureText(dateText)
        canvas.drawText(dateText, (pageWidth - dateW) / 2f, pageHeight * 0.72f, dateP)

        document.finishPage(page)

        // Helper para dibujar una card en el PDF
        fun drawCard(c: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float) {
            val cardPaint = fillPaint(colorWhite)
            val shadowPaint = fillPaint(android.graphics.Color.parseColor("#E9ECEF"))
            val rect = android.graphics.RectF(x, y, x + w, y + h)
            val shadowRect = android.graphics.RectF(x + 2f, y + 2f, x + w + 2f, y + h + 2f)
            c.drawRoundRect(shadowRect, 12f, 12f, shadowPaint)
            c.drawRoundRect(rect, 12f, 12f, cardPaint)
        }

        fun drawSectionHeader(c: android.graphics.Canvas, text: String, yPos: Float) {
            c.drawText(text, margin, yPos, boldPaint(15f, colorAccent))
            c.drawLine(margin, yPos + 5f, pageWidth - margin, yPos + 5f, android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#DEE2E6"); strokeWidth = 0.5f
            })
        }

        // Dibuja la leyenda del pie chart en el PDF (réplica de setupPieChart líneas 169-200)
        fun drawPieLegend(
            c: android.graphics.Canvas,
            x: Float,
            y: Float,
            maxWidth: Float,
            maxHeight: Float,
            categoryPercentages: Map<HabitCategory, Float>,
            colorDark: Int,
            colorSecondary: Int
        ) {
            val categoryColors = mapOf(
                HabitCategory.PHYSICAL to android.graphics.Color.parseColor("#4CAF50"),
                HabitCategory.MENTAL to android.graphics.Color.parseColor("#2196F3"),
                HabitCategory.STUDY to android.graphics.Color.parseColor("#FF9800"),
                HabitCategory.ROUTINE to android.graphics.Color.parseColor("#9C27B0"),
                HabitCategory.BAD_HABITS to android.graphics.Color.parseColor("#F44336"),
                HabitCategory.WELLBEING to android.graphics.Color.parseColor("#00BCD4"),
                HabitCategory.CUSTOM to android.graphics.Color.parseColor("#607D8B")
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

            val sortedEntries = categoryPercentages.entries.sortedByDescending { it.value }
            var currentY = y
            val rowHeight = 18f
            val dotSize = 12f
            val gap = 6f

            for ((cat, pct) in sortedEntries) {
                if (currentY + rowHeight > y + maxHeight) break
                val color = categoryColors[cat] ?: android.graphics.Color.GRAY
                val label = categoryLabels[cat] ?: cat.name
                val text = String.format("%.0f%% %s", pct, label)
                val textPaint = regularPaint(9f, colorDark)
                val dotPaint = android.graphics.Paint().apply {
                    this.color = color
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }
                c.drawCircle(x + dotSize / 2f, currentY + rowHeight / 2f, dotSize / 2f, dotPaint)
                c.drawText(text, x + dotSize + gap, currentY + rowHeight * 0.75f, textPaint)
                currentY += rowHeight
            }
        }

        val colLeft = margin
        val colRight = margin + safeWidth / 2f + 10f
        val colWidth = safeWidth / 2f - 20f
        val marginTop = 40f

        // ── PÁGINA 2: RESUMEN + CATEGORÍA ────────────────────────────────────
        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), fillPaint(colorLight))

        var y = marginTop
        drawSectionHeader(canvas, getString(R.string.stats_summary_title), y)
        y += 20f

        // Card resumen izquierda
        drawCard(canvas, colLeft, y, colWidth, 130f)
        canvas.drawText(getString(R.string.stats_current_streak), colLeft + 14f, y + 25f, regularPaint(11f, colorSecondary))
        canvas.drawText("${data.currentStreak} días", colLeft + 14f, y + 45f, boldPaint(18f, colorDark))
        canvas.drawText(getString(R.string.stats_max_streak), colLeft + 14f, y + 70f, regularPaint(11f, colorSecondary))
        canvas.drawText("${data.maxStreak} días", colLeft + 14f, y + 90f, boldPaint(18f, colorDark))
        canvas.drawText(getString(R.string.stats_monthly_consistency), colLeft + 14f, y + 112f, regularPaint(11f, colorSecondary))
        canvas.drawText("${data.monthlyConsistency}%", colLeft + 14f, y + 128f, boldPaint(18f, colorDark))

        // Card consejos derecha — resumen (texto envuelto con StaticLayout, altura dinámica)
        val summaryText = advice?.summaryAdvice ?: getString(R.string.pdf_advice_placeholder)
        val summaryPaint = regularPaint(9f, colorSecondary)
        val summaryMaxW = (colWidth - 28f).toInt()
        val summaryH = measureWrappedTextHeight(summaryText, summaryMaxW, summaryPaint)
        val summaryCardH = maxOf(130f, summaryH + 40f)

        drawCard(canvas, colRight, y, colWidth, summaryCardH)
        drawWrappedText(canvas, summaryText, colRight + 14f, y + 20f, summaryMaxW, summaryPaint)

        y += 150f
        drawSectionHeader(canvas, getString(R.string.stats_category_title), y)
        y += 20f

        // Card categoría izquierda: dividir en leyenda (izq 50%) + gráfica (der 50%)
        // igual que en la pantalla (layout_weight="1" en LinearLayout horizontal)
        val catCardW = colWidth
        val catCardH = 180f // reducir altura para que no quede alargado
        drawCard(canvas, colLeft, y, catCardW, catCardH)

        val halfW = (catCardW - 20f) / 2f // ancho útil por mitad (con padding 10 cada lado)
        val legendX = colLeft + 10f
        val chartX = colLeft + 10f + halfW + 10f // 10 padding + halfW + 10 gap
        val chartMaxH = catCardH - 10f
        // Hacer la gráfica cuadrada (ancho = alto) para que no se deforme
        val chartSize = minOf(halfW, chartMaxH)
        val chartY = y + 5f + (chartMaxH - chartSize) / 2f // centrar verticalmente

        // Dibujar leyenda manual (réplica de setupPieChart líneas 169-200)
        drawPieLegend(canvas, legendX, y + 10f, halfW, chartMaxH, data.categoryPercentages, colorDark, colorSecondary)

        // Dibujar gráfica en la mitad derecha (cuadrada, centrada)
        val scaledPie = android.graphics.Bitmap.createScaledBitmap(pieBmp, chartSize.toInt(), chartSize.toInt(), true)
        canvas.drawBitmap(scaledPie, chartX, chartY, null)

        // Card consejos categoría derecha
        val categoryText = advice?.categoryAdvice ?: getString(R.string.pdf_advice_placeholder)
        val categoryPaint = regularPaint(9f, colorSecondary)
        val categoryMaxW = (colWidth - 28f).toInt()
        val categoryH = measureWrappedTextHeight(categoryText, categoryMaxW, categoryPaint)
        val categoryCardH = maxOf(catCardH, categoryH + 40f)

        drawCard(canvas, colRight, y, colWidth, categoryCardH)
        drawWrappedText(canvas, categoryText, colRight + 14f, y + 20f, categoryMaxW, categoryPaint)

        y += catCardH + 20f

        document.finishPage(page)

        // ── PÁGINA 3: TU MES + EVOLUCIÓN ─────────────────────────────────────
        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), fillPaint(colorLight))

        y = marginTop
        drawSectionHeader(canvas, getString(R.string.stats_monthly_title), y)
        y += 20f

        // AHORA usa monthlyBmp (offscreen, parámetro), no barChartMonthly.getChartBitmap()
        drawCard(canvas, colLeft, y, colWidth, 170f)
        val scaledMonthly = android.graphics.Bitmap.createScaledBitmap(monthlyBmp, colWidth.toInt() - 20, 160, true)
        canvas.drawBitmap(scaledMonthly, colLeft + 10f, y + 5f, null)

        val monthlyText = advice?.monthlyEvaluation ?: getString(R.string.pdf_advice_placeholder)
        val monthlyPaint = regularPaint(9f, colorSecondary)
        val monthlyMaxW = (colWidth - 28f).toInt()
        val monthlyH = measureWrappedTextHeight(monthlyText, monthlyMaxW, monthlyPaint)
        val monthlyCardH = maxOf(170f, monthlyH + 40f)

        drawCard(canvas, colRight, y, colWidth, monthlyCardH)
        drawWrappedText(canvas, monthlyText, colRight + 14f, y + 20f, monthlyMaxW, monthlyPaint)

        y += 190f
        drawSectionHeader(canvas, getString(R.string.stats_yearly_title), y)
        y += 20f

        // AHORA usa yearlyBmp (offscreen, parámetro), no lineChartYearly.getChartBitmap()
        drawCard(canvas, colLeft, y, colWidth, 170f)
        val scaledYearly = android.graphics.Bitmap.createScaledBitmap(yearlyBmp, colWidth.toInt() - 20, 160, true)
        canvas.drawBitmap(scaledYearly, colLeft + 10f, y + 5f, null)

        // NOTA: esto usa advice?.summaryAdvice, el mismo texto que en el resumen de la
        // página 2. Probablemente quieras otro campo (ej. generalAdvice) — revísalo.
        val yearlyText = advice?.summaryAdvice ?: getString(R.string.pdf_advice_placeholder)
        val yearlyPaint = regularPaint(9f, colorSecondary)
        val yearlyMaxW = (colWidth - 28f).toInt()
        val yearlyH = measureWrappedTextHeight(yearlyText, yearlyMaxW, yearlyPaint)
        val yearlyCardH = maxOf(170f, yearlyH + 40f)

        drawCard(canvas, colRight, y, colWidth, yearlyCardH)
        drawWrappedText(canvas, yearlyText, colRight + 14f, y + 20f, yearlyMaxW, yearlyPaint)

        y += 190f
        drawSectionHeader(canvas, getString(R.string.stats_top5_title), y)
        y += 20f

        // El Top 5 aquí se dibuja como lista de texto, no como chart, así que no
        // necesita bitmap offscreen.
        drawCard(canvas, colLeft, y, colWidth, 160f)
        data.top5Habits.forEachIndexed { i, (name, count) ->
            canvas.drawText("${i + 1}. $name", colLeft + 14f, y + 25f + (i * 24f), boldPaint(11f, colorDark))
            canvas.drawText("$count veces", colLeft + colWidth - 60f, y + 25f + (i * 24f), regularPaint(10f, colorSecondary))
        }

        val top5Text = advice?.top5Advice ?: getString(R.string.pdf_advice_placeholder)
        val top5Paint = regularPaint(9f, colorSecondary)
        val top5MaxW = (colWidth - 28f).toInt()
        val top5H = measureWrappedTextHeight(top5Text, top5MaxW, top5Paint)
        val top5CardH = maxOf(160f, top5H + 40f)

        drawCard(canvas, colRight, y, colWidth, top5CardH)
        drawWrappedText(canvas, top5Text, colRight + 14f, y + 20f, top5MaxW, top5Paint)

        document.finishPage(page)

        // ── PÁGINA 4: RESUMEN FINAL + CONSEJOS GENERALES ─────────────────────
        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), fillPaint(colorLight))

        y = marginTop
        drawSectionHeader(canvas, getString(R.string.pdf_summary_section), y)
        y += 20f

        drawCard(canvas, colLeft, y, safeWidth, 100f)
        canvas.drawText("${getString(R.string.stats_current_streak)}: ${data.currentStreak} días  |  ${getString(R.string.stats_max_streak)}: ${data.maxStreak} días", colLeft + 14f, y + 30f, regularPaint(11f, colorDark))
        canvas.drawText("${getString(R.string.stats_monthly_consistency)}: ${data.monthlyConsistency}%", colLeft + 14f, y + 55f, regularPaint(11f, colorDark))
        if (data.starHabitName.isNotBlank()) {
            canvas.drawText("${getString(R.string.stats_star_habit, data.starHabitEmoji, data.starHabitName)}", colLeft + 14f, y + 80f, regularPaint(11f, colorDark))
        }

        y += 120f
        drawSectionHeader(canvas, getString(R.string.pdf_general_advice), y)
        y += 20f

        val generalText = advice?.generalAdvice ?: getString(R.string.pdf_advice_placeholder)
        val generalPaint = regularPaint(10f, colorSecondary)
        val generalMaxW = (safeWidth - 28f).toInt()
        val generalH = measureWrappedTextHeight(generalText, generalMaxW, generalPaint)
        val generalCardH = maxOf(80f, generalH + 40f)

        drawCard(canvas, colLeft, y, safeWidth, generalCardH)
        drawWrappedText(canvas, generalText, colLeft + 14f, y + 20f, generalMaxW, generalPaint)

        y += 100f
        drawSectionHeader(canvas, getString(R.string.pdf_specific_tips), y)
        y += 20f

        val specificText = advice?.specificTips ?: getString(R.string.pdf_advice_placeholder)
        val specificPaint = regularPaint(10f, colorSecondary)
        val specificMaxW = (safeWidth - 28f).toInt()
        val specificH = measureWrappedTextHeight(specificText, specificMaxW, specificPaint)
        val specificCardH = maxOf(200f, specificH + 40f)

        drawCard(canvas, colLeft, y, safeWidth, specificCardH)
        drawWrappedText(canvas, specificText, colLeft + 14f, y + 20f, specificMaxW, specificPaint)

        // Footer
        val footerP = regularPaint(9f, colorSecondary)
        val footerText = "Pace · ${getString(R.string.pdf_generated_on)} $today"
        val footerW = footerP.measureText(footerText)
        canvas.drawText(footerText, (pageWidth - footerW) / 2f, pageHeight - margin - 10f, footerP)

        document.finishPage(page)

        // ── GUARDAR ───────────────────────────────────────────────────────────
        val fileName = "pace_stats_${System.currentTimeMillis()}.pdf"
        val file = java.io.File(requireContext().cacheDir, fileName)
        document.writeTo(java.io.FileOutputStream(file))
        document.close()
        return file
    }

    private fun sharePdf(file: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )

        // Compartir
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // También guardar en Descargas
        saveToDownloads(file)

        startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.stats_share_pdf)))
    }

    private fun saveToDownloads(file: java.io.File) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = requireContext().contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { out ->
                    file.inputStream().copyTo(out)
                }
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val destFile = java.io.File(downloadsDir, file.name)
            file.copyTo(destFile, overwrite = true)
        }
    }

    /**
     * Dibuja texto ajustado al ancho disponible (word-wrap real vía StaticLayout)
     * y devuelve la altura total que ha ocupado, para poder calcular el alto
     * de la card ANTES de pintarla (ver measureWrappedTextHeight).
     */
    private fun drawWrappedText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Int,
        paint: android.graphics.Paint,
        lineSpacingExtra: Float = 4f
    ): Float {
        val staticLayout = android.text.StaticLayout.Builder
            .obtain(text, 0, text.length, android.text.TextPaint(paint), maxWidth)
            .setLineSpacing(lineSpacingExtra, 1f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(x, y)
        staticLayout.draw(canvas)
        canvas.restore()

        return staticLayout.height.toFloat()
    }

    /** Mide la altura que ocuparía el texto envuelto SIN dibujarlo. */
    private fun measureWrappedTextHeight(text: String, maxWidth: Int, paint: android.graphics.Paint): Float {
        return android.text.StaticLayout.Builder
            .obtain(text, 0, text.length, android.text.TextPaint(paint), maxWidth)
            .setIncludePad(false)
            .build()
            .height.toFloat()
    }

    private fun getCategoryLabel(cat: com.novikon.pace.models.HabitCategory): String {
        return when (cat) {
            com.novikon.pace.models.HabitCategory.PHYSICAL -> getString(R.string.category_physical)
            com.novikon.pace.models.HabitCategory.MENTAL -> getString(R.string.category_mental)
            com.novikon.pace.models.HabitCategory.STUDY -> getString(R.string.category_study)
            com.novikon.pace.models.HabitCategory.ROUTINE -> getString(R.string.category_routine)
            com.novikon.pace.models.HabitCategory.BAD_HABITS -> getString(R.string.category_bad_habits)
            com.novikon.pace.models.HabitCategory.WELLBEING -> getString(R.string.category_wellbeing)
            com.novikon.pace.models.HabitCategory.CUSTOM -> getString(R.string.category_custom)
        }
    }
}