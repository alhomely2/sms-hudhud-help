package com.hudhud.app.ui.shared

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.hudhud.app.databinding.SheetSendSourceBinding
import com.hudhud.app.ui.ready.ReadyMessagesActivity
import com.hudhud.app.ui.template.DynamicTemplateActivity

/**
 * The send action is a source picker, not a third entry: the two data shapes the
 * app supports stay exactly two, and the operator picks one right before sending.
 */
object SendSourceSheet {

    fun show(activity: AppCompatActivity) {
        val binding = SheetSendSourceBinding.inflate(activity.layoutInflater)
        val dialog = BottomSheetDialog(activity).apply {
            setContentView(binding.root)
        }

        binding.sendReadyOption.setOnClickListener {
            dialog.dismiss()
            activity.startActivity(Intent(activity, ReadyMessagesActivity::class.java))
        }
        binding.sendTemplateOption.setOnClickListener {
            dialog.dismiss()
            activity.startActivity(Intent(activity, DynamicTemplateActivity::class.java))
        }

        dialog.show()
    }
}
