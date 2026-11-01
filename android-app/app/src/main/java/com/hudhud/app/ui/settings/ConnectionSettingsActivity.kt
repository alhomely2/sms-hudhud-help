package com.hudhud.app.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.hudhud.app.R
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.databinding.ActivityConnectionSettingsBinding
import com.hudhud.app.ui.shared.BottomNavBar
import com.hudhud.app.ui.shared.BottomNavTab

/**
 * Main settings activity with tabbed navigation.
 * Each tab opens a different settings category.
 */
class ConnectionSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectionSettingsBinding
    private lateinit var appSettings: AppSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectionSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.connection_title)

        appSettings = AppSettingsStore(this)

        if (appSettings.consumeMigrationWarning()) {
            Toast.makeText(this, R.string.settings_migration_adjusted, Toast.LENGTH_LONG).show()
        }

        setupViewPager()
        setupBottomNavigation()
    }

    private fun setupViewPager() {
        val pagerAdapter = SettingsPagerAdapter(this)
        binding.settingsViewPager.adapter = pagerAdapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.settingsTabLayout, binding.settingsViewPager) { tab, position ->
            tab.text = when (position) {
                SettingsPagerAdapter.TAB_PROFILES -> getString(R.string.settings_tab_profiles)
                SettingsPagerAdapter.TAB_API_KEYS -> getString(R.string.settings_tab_api_keys)
                SettingsPagerAdapter.TAB_RATE_LIMITS -> getString(R.string.settings_tab_rate_limits)
                SettingsPagerAdapter.TAB_ADVANCED -> getString(R.string.settings_tab_advanced)
                else -> ""
            }
        }.attach()

        // Optimize ViewPager for settings (keep fragments in memory)
        binding.settingsViewPager.offscreenPageLimit = 2
    }

    private fun setupBottomNavigation() {
        BottomNavBar.bind(this, binding.bottomNav, BottomNavTab.SETTINGS)
    }
}