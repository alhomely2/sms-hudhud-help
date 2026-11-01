package com.hudhud.app.ui.templates

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.hudhud.app.R
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.template.TemplatePlaceholderParser
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * One saved template in the Templates tab.
 *
 * The whole card opens the editor; the overflow button carries the remaining
 * actions so a single row stays scannable and touch targets stay at 48dp.
 */
class TemplateCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : MaterialCardView(context, attrs) {

    private val nameText = TextView(context)
    private val bodyText = TextView(context)
    private val fieldsText = TextView(context)
    private val fixedText = TextView(context)
    private val updatedText = TextView(context)
    private val overflowButton = ImageButton(context)

    init {
        radius = resources.getDimension(R.dimen.radius_card)
        cardElevation = 0f
        strokeWidth = resources.getDimensionPixelSize(R.dimen.template_default_stroke)
        setStrokeColor(ContextCompat.getColor(context, R.color.hudhud_outline))
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.hudhud_surface))
        rippleColor = ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.hudhud_ripple),
        )
        isClickable = true
        isFocusable = true

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_md),
                resources.getDimensionPixelSize(R.dimen.space_sm),
                resources.getDimensionPixelSize(R.dimen.space_sm),
                resources.getDimensionPixelSize(R.dimen.space_md),
            )
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        nameText.apply {
            setTextAppearance(R.style.TextAppearance_Hudhud_CardTitle)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        overflowButton.apply {
            setImageResource(R.drawable.ic_more_vert)
            imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.hudhud_on_surface_muted),
            )
            val borderless = TypedValue()
            context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                borderless,
                true,
            )
            setBackgroundResource(borderless.resourceId)
            layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.touch_target),
                resources.getDimensionPixelSize(R.dimen.touch_target),
            )
        }
        header.addView(nameText)
        header.addView(overflowButton)

        content.addView(header)
        content.addView(bodyText.apply {
            setTextAppearance(R.style.TextAppearance_Hudhud_CardBody)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_xs),
                0,
                resources.getDimensionPixelSize(R.dimen.space_xs),
                0,
            )
        })
        content.addView(fieldsText.apply {
            setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_xs),
                resources.getDimensionPixelSize(R.dimen.space_sm),
                resources.getDimensionPixelSize(R.dimen.space_xs),
                0,
            )
        })
        content.addView(fixedText.apply {
            setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_xs),
                resources.getDimensionPixelSize(R.dimen.space_xs),
                resources.getDimensionPixelSize(R.dimen.space_xs),
                0,
            )
        })
        content.addView(updatedText.apply {
            setTextAppearance(R.style.TextAppearance_Hudhud_Caption)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.space_xs),
                resources.getDimensionPixelSize(R.dimen.space_xs),
                resources.getDimensionPixelSize(R.dimen.space_xs),
                0,
            )
        })

        addView(content)
    }

    fun bind(
        template: SavedTemplate,
        onOpen: () -> Unit,
        onEdit: () -> Unit,
        onDuplicate: () -> Unit,
        onDelete: () -> Unit,
    ) {
        nameText.text = template.name
        bodyText.text = template.body
        val fields = template.variableColumns.keys
            .joinToString(separator = " · ") { "{{$it}}" }
        fieldsText.text = context.getString(
            R.string.template_details_fields,
            fields.ifBlank { context.getString(R.string.template_details_none) },
        )
        fieldsText.visibility = if (fields.isBlank()) GONE else VISIBLE
        val fixed = template.fixedValues.entries.joinToString(separator = " · ") { (name, value) ->
            "${TemplatePlaceholderParser.placeholderFor(name)} = $value"
        }
        fixedText.text = context.getString(R.string.template_details_fixed, fixed)
        fixedText.visibility = if (fixed.isBlank()) GONE else VISIBLE
        updatedText.text = context.getString(
            R.string.templates_updated_at,
            relativeTime(context, template.updatedAt),
        )
        setOnClickListener { onOpen() }
        overflowButton.apply {
            contentDescription = context.getString(
                R.string.template_more_actions,
                template.name,
            )
            setOnClickListener { showMenu(onEdit, onDuplicate, onDelete) }
        }
    }

    private fun showMenu(
        onEdit: () -> Unit,
        onDuplicate: () -> Unit,
        onDelete: () -> Unit,
    ) {
        PopupMenu(context, overflowButton).apply {
            menu.add(0, MENU_EDIT, 0, R.string.template_menu_edit)
            menu.add(0, MENU_DUPLICATE, 1, R.string.template_menu_duplicate)
            menu.add(0, MENU_DELETE, 2, R.string.template_menu_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT -> onEdit()
                    MENU_DUPLICATE -> onDuplicate()
                    MENU_DELETE -> onDelete()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private companion object {
        const val MENU_EDIT = 1
        const val MENU_DUPLICATE = 2
        const val MENU_DELETE = 3
        val ONE_MINUTE_MILLIS = TimeUnit.MINUTES.toMillis(1)
        val ONE_HOUR_MILLIS = TimeUnit.HOURS.toMillis(1)
        val ONE_DAY_MILLIS = TimeUnit.DAYS.toMillis(1)

        fun relativeTime(context: Context, timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < ONE_MINUTE_MILLIS -> context.getString(R.string.time_just_now)
                diff < ONE_HOUR_MILLIS ->
                    context.getString(R.string.time_minutes_ago, diff / ONE_MINUTE_MILLIS)
                diff < ONE_DAY_MILLIS ->
                    context.getString(R.string.time_hours_ago, diff / ONE_HOUR_MILLIS)
                diff < 2 * ONE_DAY_MILLIS -> context.getString(R.string.time_yesterday)
                else -> DateFormat.getMediumDateFormat(context).format(Date(timestamp))
            }
        }
    }
}
