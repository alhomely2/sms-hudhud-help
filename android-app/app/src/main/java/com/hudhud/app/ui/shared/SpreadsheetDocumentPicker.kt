package com.hudhud.app.ui.shared

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class SpreadsheetDocumentPicker(
    activity: ComponentActivity,
    private val onPicked: (Uri, String) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            onFailure("لم يتم اختيار ملف")
        } else {
            val name = activity.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
            onPicked(uri, name ?: "ملف محدد")
        }
    }

    fun launch() {
        launcher.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/pdf",
            ),
        )
    }
}
