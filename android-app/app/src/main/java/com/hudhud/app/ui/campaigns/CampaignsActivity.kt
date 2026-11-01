package com.hudhud.app.ui.campaigns

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hudhud.app.R
import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.data.local.CampaignEntity
import com.hudhud.app.databinding.ActivityCampaignsBinding
import com.hudhud.app.ui.shared.BottomNavBar
import com.hudhud.app.ui.shared.BottomNavTab
import com.hudhud.app.ui.shared.SendSourceSheet
import com.hudhud.app.ui.template.DynamicTemplateActivity
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CampaignsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCampaignsBinding
    private var drafts: List<CampaignEntity> = emptyList()
    private var campaigns: List<CampaignEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCampaignsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        BottomNavBar.bind(this, binding.bottomNav, BottomNavTab.CAMPAIGNS)
        binding.startCampaignButton.setOnClickListener { SendSourceSheet.show(this) }
        lifecycleScope.launch {
            AppDatabase.getInstance(this@CampaignsActivity)
                .campaignDao()
                .observeAll()
                .collect { renderCampaigns(it) }
        }
        lifecycleScope.launch {
            AppDatabase.getInstance(this@CampaignsActivity)
                .campaignDao()
                .observeDrafts()
                .collect(::renderDrafts)
        }
    }

    private fun renderCampaigns(campaigns: List<CampaignEntity>) {
        this.campaigns = campaigns
        binding.campaignListContainer.removeAllViews()
        val hasCampaigns = campaigns.isNotEmpty() || drafts.isNotEmpty()
        binding.campaignsEmptyCard.visibility = if (hasCampaigns) View.GONE else View.VISIBLE
        val active = AppSettingsStore(this).getActiveProfile()
        binding.campaignsLocalNotice.text = getString(
            R.string.campaigns_local_notice_with_profile,
            active.name,
            active.batchSize,
        )
        binding.campaignsLocalNotice.visibility =
            if (campaigns.isNotEmpty()) View.VISIBLE else View.GONE
        campaigns.forEach { campaign ->
            binding.campaignListContainer.addView(createCampaignCard(campaign))
        }
    }

    private fun renderDrafts(drafts: List<CampaignEntity>) {
        this.drafts = drafts
        binding.campaignDraftContainer.removeAllViews()
        if (drafts.isNotEmpty()) {
            binding.campaignDraftContainer.addView(TextView(this).apply {
                text = getString(R.string.campaign_draft_title)
                setTextAppearance(R.style.TextAppearance_Hudhud_CardTitle)
            })
        }
        drafts.forEach { draft ->
            binding.campaignDraftContainer.addView(createDraftCard(draft))
        }
        renderCampaigns(campaigns)
    }

    private fun createDraftCard(draft: CampaignEntity): View {
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.radius_card)
            cardElevation = 0f
            strokeWidth = resources.getDimensionPixelSize(R.dimen.template_default_stroke)
            setStrokeColor(ContextCompat.getColor(this@CampaignsActivity, R.color.hudhud_outline))
            setCardBackgroundColor(ContextCompat.getColor(this@CampaignsActivity, R.color.hudhud_surface))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_md),
            )
        }
        content.addView(TextView(this).apply {
            text = getString(
                R.string.campaign_draft_counts,
                draft.name,
                draft.totalMessages,
                draft.reviewMessages,
            )
            setTextAppearance(R.style.TextAppearance_Hudhud_CardBody)
        })
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.campaign_resume_draft)
            setOnClickListener {
                startActivity(
                    DynamicTemplateActivity.resumeIntent(
                        this@CampaignsActivity,
                        draft.id,
                    ),
                )
            }
        })
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.campaign_delete_draft)
            setOnClickListener { confirmDeleteDraft(draft) }
        })
        card.addView(content)
        return card
    }

    private fun confirmDeleteDraft(draft: CampaignEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.campaign_delete_draft_title)
            .setMessage(R.string.campaign_delete_draft_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.campaign_delete_draft) { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.getInstance(this@CampaignsActivity)
                        .campaignDao()
                        .deleteDraft(draft.id)
                }
            }
            .show()
    }

    private fun createCampaignCard(campaign: CampaignEntity): View {
        val card = MaterialCardView(this).apply {
            radius = resources.getDimension(R.dimen.radius_card)
            cardElevation = 0f
            strokeWidth = resources.getDimensionPixelSize(R.dimen.template_default_stroke)
            setStrokeColor(ContextCompat.getColor(this@CampaignsActivity, R.color.hudhud_outline))
            setCardBackgroundColor(ContextCompat.getColor(this@CampaignsActivity, R.color.hudhud_surface))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_md),
            )
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.campaign_name, campaign.name)
            setTextAppearance(R.style.TextAppearance_Hudhud_CardTitle)
        })
        content.addView(TextView(this).apply {
            text = getString(
                R.string.campaign_counts,
                campaign.totalMessages,
                campaign.validMessages,
                campaign.reviewMessages,
            )
            setTextAppearance(R.style.TextAppearance_Hudhud_CardBody)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.space_xs)
            }
        })
        val modeText = if (campaign.importMode == CampaignEntity.MODE_READY_TEXT) {
            getString(R.string.campaign_mode_ready_badge)
        } else {
            getString(R.string.campaign_mode_raw_badge)
        }
        content.addView(TextView(this).apply {
            text = modeText
            setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
        })
        content.addView(TextView(this).apply {
            text = when (campaign.status) {
                CampaignEntity.STATUS_READY_LOCAL -> getString(R.string.campaign_status_ready)
                CampaignEntity.STATUS_SENDING -> getString(R.string.campaign_status_sending)
                CampaignEntity.STATUS_PAUSED -> getString(R.string.campaign_status_paused)
                CampaignEntity.STATUS_COMPLETED -> getString(R.string.campaign_status_completed)
                else -> getString(R.string.campaign_status_review)
            }
            setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
        })
        content.addView(TextView(this).apply {
            text = getString(
                R.string.campaign_created_at,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(campaign.createdAt)),
            )
            setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
        })
        when (campaign.status) {
            CampaignEntity.STATUS_READY_LOCAL -> {
                content.addView(MaterialButton(this).apply {
                    text = getString(R.string.campaign_action_start_send)
                    setOnClickListener {
                        com.hudhud.app.service.CampaignDispatchService.start(this@CampaignsActivity, campaign.id)
                    }
                })
            }
            CampaignEntity.STATUS_SENDING -> {
                content.addView(MaterialButton(this).apply {
                    text = getString(R.string.campaign_action_pause)
                    setOnClickListener {
                        com.hudhud.app.service.CampaignDispatchService.pause(this@CampaignsActivity)
                    }
                })
            }
            CampaignEntity.STATUS_PAUSED -> {
                content.addView(MaterialButton(this).apply {
                    text = getString(R.string.campaign_action_resume)
                    setOnClickListener {
                        com.hudhud.app.service.CampaignDispatchService.resume(this@CampaignsActivity)
                    }
                })
            }
        }
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.campaign_delete)
            setOnClickListener { confirmDeleteCampaign(campaign) }
        })
        card.addView(content)
        return card.apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.space_sm)
            }
        }
    }

    private fun confirmDeleteCampaign(campaign: CampaignEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.campaign_delete_title)
            .setMessage(R.string.campaign_delete_body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.campaign_delete) { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.getInstance(this@CampaignsActivity)
                        .campaignDao()
                        .deleteCampaign(campaign.id)
                }
            }
            .show()
    }
}
