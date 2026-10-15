package com.trilink.app

import com.trilink.app.domain.PathSanitizer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PathSanitizerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPathTraversalStripping() {
        val sanitized = PathSanitizer.sanitizeFilename("../../etc/passwd")
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))
        assertEquals("passwd", sanitized)
    }

    @Test
    fun testWindowsPathSeparatorsAndIllegalCharacters() {
        val sanitized = PathSanitizer.sanitizeFilename("..\\..\\secret:docs*?.pdf")
        assertFalse(sanitized.contains("\\"))
        assertFalse(sanitized.contains(":"))
        assertFalse(sanitized.contains("*"))
        assertFalse(sanitized.contains("?"))
        assertTrue(sanitized.endsWith(".pdf"))
    }

    @Test
    fun testEmptyOrWhitespaceFallback() {
        val sanitized = PathSanitizer.sanitizeFilename("   ", fallback = "untitled.bin")
        assertEquals("untitled.bin", sanitized)
    }

    @Test
    fun testCollisionResolution() {
        val dir = tempFolder.newFolder("downloads")
        val file1 = File(dir, "document.txt")
        file1.writeText("first")

        val target = PathSanitizer.resolveDestinationFile(dir, "document.txt")
        assertEquals("document(1).txt", target.name)

        target.writeText("second")
        val target2 = PathSanitizer.resolveDestinationFile(dir, "document.txt")
        assertEquals("document(2).txt", target2.name)
    }
}
