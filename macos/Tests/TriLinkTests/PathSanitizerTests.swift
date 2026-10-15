import XCTest
@testable import TriLinkCore

final class PathSanitizerTests: XCTestCase {

    func testPathTraversalStripping() {
        let sanitized = PathSanitizer.sanitizeFilename("../../etc/passwd")
        XCTAssertFalse(sanitized.contains(".."))
        XCTAssertFalse(sanitized.contains("/"))
        XCTAssertEqual(sanitized, "passwd")
    }

    func testWindowsPathAndIllegalChars() {
        let sanitized = PathSanitizer.sanitizeFilename("..\\..\\malicious:photo*?.png")
        XCTAssertFalse(sanitized.contains("\\"))
        XCTAssertFalse(sanitized.contains(":"))
        XCTAssertFalse(sanitized.contains("*"))
        XCTAssertTrue(sanitized.hasSuffix(".png"))
    }

    func testEmptyFallback() {
        let sanitized = PathSanitizer.sanitizeFilename("   ", fallback = "untitled.dat")
        XCTAssertEqual(sanitized, "untitled.dat")
    }

    func testCollisionResolution() throws {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: tempDir) }

        let file1 = tempDir.appendingPathComponent("note.txt")
        try "content1".write(to: file1, atomically: true, encoding: .utf8)

        let resolved = PathSanitizer.resolveDestinationFile(parentDir: tempDir, filename: "note.txt")
        XCTAssertEqual(resolved.lastPathComponent, "note(1).txt")

        try "content2".write(to: resolved, atomically: true, encoding: .utf8)
        let resolved2 = PathSanitizer.resolveDestinationFile(parentDir: tempDir, filename: "note.txt")
        XCTAssertEqual(resolved2.lastPathComponent, "note(2).txt")
    }
}
