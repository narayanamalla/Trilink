package com.trilink.app.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

object FileUtils {
    data class FileDetails(
        val name: String,
        val size: Long,
        val mimeType: String?
    )

    fun resolveUriDetails(context: Context, uri: Uri): FileDetails {
        var name = ""
        var size = 0L
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }

        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) {
                            name = cursor.getString(nameIdx) ?: ""
                        }
                        if (sizeIdx != -1) {
                            size = cursor.getLong(sizeIdx)
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        if (name.isBlank()) {
            name = uri.lastPathSegment ?: "file"
        }

        // If name lacks an extension, attempt resolution via MIME type
        val hasExtension = name.contains(".") && name.substringAfterLast(".", "").length in 1..8
        if (!hasExtension && !mimeType.isNullOrBlank()) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (!ext.isNullOrBlank()) {
                name = "$name.$ext"
            } else if (mimeType.startsWith("image/")) {
                name = "$name.jpg"
            }
        }

        if (size <= 0L) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    size = it.statSize
                }
            } catch (e: Exception) {}
        }

        val sanitized = PathSanitizer.sanitizeFilename(name, fallback = "file")
        return FileDetails(name = sanitized, size = size, mimeType = mimeType)
    }

    fun isImage(name: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("image/") == true) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "svg")
    }
}
