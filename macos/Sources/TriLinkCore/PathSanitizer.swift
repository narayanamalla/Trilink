import Foundation

public enum PathSanitizer {
    private static let illegalCharacters = CharacterSet(charactersIn: "\\/:*?\"<>|\0")
        .union(.controlCharacters)

    public static func sanitizeFilename(_ rawName: String, fallback: String = "received_file") -> String {
        var name = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        if name.isEmpty { return fallback }

        // Normalize slashes and take basename
        name = name.replacingOccurrences(of: "\\", with: "/")
        if let lastSlash = name.lastIndex(of: "/") {
            name = String(name[name.index(after: lastSlash)...])
        }

        // Remove dangerous relative path tokens
        name = name.replacingOccurrences(of: "..", with: "")

        let originalExt = (rawName as NSString).pathExtension
        let cleanedComponents = name.components(separatedBy: illegalCharacters)
        var cleaned = cleanedComponents.joined(separator: "_")

        if cleaned.isEmpty || cleaned == "." {
            return fallback
        }

        if !originalExt.isEmpty && !originalExt.contains("/") && !originalExt.contains("\\") {
            let base = (cleaned as NSString).deletingPathExtension
            cleaned = "\(base).\(originalExt)"
        }

        if cleaned.count > 120 {
            let ext = (cleaned as NSString).pathExtension
            if !ext.isEmpty && ext.count < 10 {
                let prefix = String(cleaned.prefix(110))
                cleaned = "\(prefix).\(ext)"
            } else {
                cleaned = String(cleaned.prefix(120))
            }
        }

        return cleaned.isEmpty ? fallback : cleaned
    }

    public static func resolveDestinationFile(parentDir: URL, filename: String) -> URL {
        let sanitized = sanitizeFilename(filename)
        var target = parentDir.appendingPathComponent(sanitized)
        let fm = FileManager.default

        if !fm.fileExists(atPath: target.path) {
            return target
        }

        let baseName = (sanitized as NSString).deletingPathExtension
        let ext = (sanitized as NSString).pathExtension
        let dotExt = ext.isEmpty ? "" : ".\(ext)"

        var counter = 1
        while fm.fileExists(atPath: target.path) && counter < 1000 {
            target = parentDir.appendingPathComponent("\(baseName)(\(counter))\(dotExt)")
            counter += 1
        }
        return target
    }
}
