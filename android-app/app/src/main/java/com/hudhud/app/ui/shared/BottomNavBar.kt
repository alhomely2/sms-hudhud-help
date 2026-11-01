package com.hudhud.app.ui.shared

import android.content.Intent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hudhud.app.R
import com.hudhud.app.databinding.ViewBottomNavBinding
import com.hudhud.app.ui.campaigns.CampaignsActivity
import com.hudhud.app.ui.home.MainActivity
import com.hudhud.app.ui.settings.ConnectionSettingsActivity
import com.hudhud.app.ui.templates.TemplatesActivity

/** Destinations that stay highlighted in the bottom bar. */
enum class BottomNavTab { HOME, CAMPAIGNS, TEMPLATES, SETTINGS }

/**
 * Wires the shared bottom bar and paints the selected tab.
 *
 * The bar lives in `view_bottom_nav.xml` and is included by every top-level
 * screen, so navigation stays identical wherever the operator is.
 */
object BottomNavBar {

    fun bind(
        activity: AppCompatActivity,
        nav: ViewBottomNavBinding,
        selected: BottomNavTab,
        onSend: (() -> Unit)? = null,
    ) {
        nav.navHome.setOnClickListener { open(activity, MainActivity::class.java) }
        nav.navCampaigns.setOnClickListener { open(activity, CampaignsActivity::class.java) }
        nav.navTemplates.setOnClickListener { open(activity, TemplatesActivity::class.java) }
        nav.navSettings.setOnClickListener { open(activity, ConnectionSettingsActivity::class.java) }
        nav.navSend.setOnClickListener {
            if (onSend == null) SendSourceSheet.show(activity) else onSend()
        }

        color(activity, nav.navHomeIcon, nav.navHomeLabel, selected == BottomNavTab.HOME)
        color(activity, nav.navCampaignsIcon, nav.navCampaignsLabel, selected == BottomNavTab.CAMPAIGNS)
        color(activity, nav.navTemplatesIcon, nav.navTemplatesLabel, selected == BottomNavTab.TEMPLATES)
        color(activity, nav.navSettingsIcon, nav.navSettingsLabel, selected == BottomNavTab.SETTINGS)
    }

    private fun color(
        activity: AppCompatActivity,
        icon: ImageView,
        label: TextView,
        active: Boolean,
    ) {
        val tint = ContextCompat.getColor(
            activity,
            if (active) R.color.hudhud_primary else R.color.hudhud_on_surface_muted,
        )
        icon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
        icon.alpha = if (active) 1f else 0.85f
        label.setTextColor(tint)
        label.alpha = if (active) 1f else 0.85f
    }

    /**
     * Reuses the existing instance when it is already in the back stack so the
     * bar never stacks duplicate top-level screens.
     *
     * Tabs are siblings of the home screen, never of each other: leaving a tab
     * tears that tab down so pressing back always lands on الرئيسية instead of
     * replaying the tab the operator happened to visit before this one.
     */
    private fun open(activity: AppCompatActivity, target: Class<out AppCompatActivity>) {
        if (activity.javaClass == target) return
        activity.startActivity(
            Intent(activity, target).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        if (activity !is MainActivity) activity.finish()
    }
}
