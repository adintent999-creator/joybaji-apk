package com.bypass.studio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object ExportManager {
    fun saveToDownloads(context: Context, source: File, displayName: String): String {
        if (!source.exists()) return "No export file yet"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BypassStudio")
                }
                val uri: Uri? = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri == null) return "Could not create Downloads entry"
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: return "Could not open Downloads stream"
                "Saved to Downloads/BypassStudio/$displayName"
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BypassStudio")
                dir.mkdirs()
                val target = File(dir, displayName)
                source.copyTo(target, overwrite = true)
                "Saved to ${target.absolutePath}"
            }
        } catch (e: Exception) {
            "Save failed: ${e.message}"
        }
    }

    fun share(context: Context, file: File): String {
        if (!file.exists()) return "No export file yet"
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share broadcast")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            "Share sheet opened"
        } catch (e: Exception) {
            "Share failed: ${e.message}"
        }
    }
}
