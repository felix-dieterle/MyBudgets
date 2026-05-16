package de.mybudgets.app.ui.dashboard

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

class ChartPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getItem(position: Int): Fragment =
        ChartPageFragment().apply {
            arguments = android.os.Bundle().apply { putInt("pageIndex", position) }
        }

    override fun getCount() = 3
}
