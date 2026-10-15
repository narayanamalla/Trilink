package com.trilink.app.domain

import java.io.File
import java.util.regex.Pattern

object PathSanitizer {
    private val ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]")

    /**
     * Sanitizes untrusted incoming filenames to prevent path traversal and filesystem attacks.
     * Extracts only the terminal basename, removes control and illegal characters, and enforces length bounds.
     */
    fun sanitizeFilename(rawName: String, fallback: String = "received_file"): String {
        var name = rawName.trim()
        if (name.isEmpty()) return fallback

        // Normalize forward & backward slashes, take only the basename
        name = name.replace('\\', '/')
        if (name.contains('/')) {
            name = name.substringAfterLast('/')
        }

        // Remove dangerous relative path tokens
        name = name.replace("..", "").replace(".", "")

        // If after cleaning name is empty, fallback
        if (name.isEmpty()) return fallback

        // Re-extract extension if original had one
        val originalExtension = rawName.substringAfterLast('.', "")
        val cleanedBase = ILLEGAL_CHARS.matcher(name).replaceAll("_")

        var finalName = if (originalExtension.isNotEmpty() && !originalExtension.contains('/') && !originalExtension.contains('\\')) {
            val baseWithoutExt = cleanedBase.removeSuffix(".$originalExtension")
            "$baseWithoutExt.$originalExtension"
        } else {
            cleanedBase
        }

        // Limit length
        if (finalName.length > 120) {
            val ext = finalName.substringAfterLast('.', "")
            finalName = if (ext.isNotEmpty() && ext.length < 10) {
                finalName.take(110) + "." + ext
            } else {
                finalName.take(120)
            }
        }

        return if (finalName.isBlank()) fallback else finalName
    }

    /**
     * Resolves file collision within a target directory by appending a numeric counter (e.g., photo(1).jpg).
     */
    fun resolveDestinationFile(parentDir: File, filename: String): File {
        val sanitized = sanitizeFilename(filename)
        var target = File(parentDir, sanitized)
        if (!target.exists()) return target

        val nameWithoutExt = sanitized.substringBeforeLast('.', sanitized)
        val ext = if (sanitized.contains('.')) "." + sanitized.substringAfterLast('.') else ""

        var counter = 1
        while (target.exists() && counter < 1000) {
            target = File(parentDir, "$nameWithoutExt($counter)$ext")
            counter++
        }
        return target
    }
}
