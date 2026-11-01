package com.hudhud.app.ui.settings

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * ViewPager adapter for settings tabs
 */
class SettingsPagerAdapter(activity: ConnectionSettingsActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = TAB_COUNT

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            TAB_PROFILES -> ProfilesFragment()
            TAB_API_KEYS -> ApiKeysFragment()
            TAB_RATE_LIMITS -> RateLimitsFragment()
            TAB_ADVANCED -> AdvancedFragment()
            else -> throw IllegalArgumentException("Invalid tab position: $position")
        }
    }

    companion object {
        const val TAB_COUNT = 4
        const val TAB_PROFILES = 0
        const val TAB_API_KEYS = 1
        const val TAB_RATE_LIMITS = 2
        const val TAB_ADVANCED = 3
    }
}