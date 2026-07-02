package com.novikon.pace.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.novikon.pace.R
import com.novikon.pace.adapters.AdviceAdapter
import com.novikon.pace.adapters.AdviceSection
import com.novikon.pace.data.GeminiResult
import com.novikon.pace.data.GeminiService
import com.novikon.pace.data.RealtimeDatabaseManager
import com.novikon.pace.models.AdviceContent
import kotlinx.coroutines.launch
import java.util.Locale

class StatsAdviceFragment : Fragment() {

    private lateinit var btnRefresh: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var rvAdvice: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: AdviceAdapter
    private lateinit var db: RealtimeDatabaseManager

    private var statsData: StatsData? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_stats_advice, container, false)
        btnRefresh = view.findViewById(R.id.btn_refresh_advice)
        progressBar = view.findViewById(R.id.progressAdvice)
        rvAdvice = view.findViewById(R.id.rv_advice)
        tvEmpty = view.findViewById(R.id.tv_empty_advice)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = RealtimeDatabaseManager()

        adapter = AdviceAdapter()
        rvAdvice.layoutManager = LinearLayoutManager(requireContext())
        rvAdvice.adapter = adapter

        btnRefresh.setOnClickListener { loadAdvice() }

        val cached = (activity as? StatsActivity)?.adviceContent
        if (cached != null) {
            displayAdvice(cached)
        } else {
            loadAdvice()
        }
    }

    private fun loadAdvice() {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val logs = db.getHabitLogs()
                val habits = db.getHabits()
                val data = StatsDataAnalyzer.analyze(logs, habits)
                statsData = data

                val languageCode = getLanguageCode()
                when (val result = GeminiService.getAdvice(data, habits, languageCode)) {
                    is GeminiResult.Success -> {
                        (activity as? StatsActivity)?.adviceContent = result.advice
                        displayAdvice(result.advice)
                    }
                    is GeminiResult.Error -> {
                        showError(result.message)
                    }
                    is GeminiResult.NoKey -> {
                        showError(getString(R.string.advice_no_key))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError(getString(R.string.advice_error))
            }
        }
    }

    private fun displayAdvice(advice: AdviceContent) {
        showLoading(false)

        val sections = listOf(
            AdviceSection("📊", getString(R.string.advice_summary), advice.summaryAdvice),
            AdviceSection("📂", getString(R.string.advice_category), advice.categoryAdvice),
            AdviceSection("🏆", getString(R.string.advice_top5), advice.top5Advice),
            AdviceSection("📅", getString(R.string.advice_monthly), advice.monthlyEvaluation),
            AdviceSection("💡", getString(R.string.advice_general), advice.generalAdvice),
            AdviceSection("🎯", getString(R.string.advice_specific), advice.specificTips)
        )

        adapter.submitList(sections)
        rvAdvice.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
    }

    private fun showLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnRefresh.isEnabled = !loading
        if (loading) {
            rvAdvice.visibility = View.GONE
            tvEmpty.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        showLoading(false)
        rvAdvice.visibility = View.GONE
        tvEmpty.text = message
        tvEmpty.visibility = View.VISIBLE
    }

    private fun getLanguageCode(): String {
        val locale = resources.configuration.locales.get(0) ?: Locale.getDefault()
        return when (locale.language.lowercase()) {
            "es" -> "es"
            "en" -> "en"
            "fr" -> "fr"
            else -> "en"
        }
    }
}
