package com.hudhud.app.ui.shared

import android.app.Activity
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hudhud.app.R
import com.hudhud.app.ui.templates.TemplatesActivity

/**
 * The only approved bridge between a workflow and template creation.
 *
 * It never creates a template itself: it explains the mismatch and, when the
 * operator agrees, opens the Templates tab with the editor ready. Creation
 * therefore always happens inside that tab.
 */
object NoMatchingTemplateDialog {

    fun show(
        activity: Activity,
        message: String? = null,
        onDismiss: (() -> Unit)? = null,
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.template_missing_dialog_title)
            .setMessage(message ?: activity.getString(R.string.template_missing_dialog_body))
            .setNegativeButton(R.string.template_missing_dialog_cancel) { _, _ ->
                onDismiss?.invoke()
            }
            .setPositiveButton(R.string.template_missing_dialog_confirm) { _, _ ->
                activity.startActivity(
                    Intent(activity, TemplatesActivity::class.java)
                        .putExtra(TemplatesActivity.EXTRA_START_CREATE, true)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        ),
                )
            }
            .show()
    }
}
