package com.novikon.pace.ui.stats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.novikon.pace.R
import com.novikon.pace.databinding.ActivityStatsBinding
import com.novikon.pace.utils.applySystemBarInsets

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.viewPager.adapter = StatsPagerAdapter(this)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.stats_tab_report)
                1 -> getString(R.string.stats_tab_advice)
                else -> ""
            }
        }.attach()
    }

    private inner class StatsPagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> StatsReportFragment()
            1 -> StatsAdviceFragment()
            else -> StatsReportFragment()
        }
    }
}