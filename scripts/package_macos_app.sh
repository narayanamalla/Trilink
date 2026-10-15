#!/usr/bin/env bash
set -e

APP_NAME="TriLink"
OUTPUT_DIR="${1:-./build}"
BINARY_PATH="${2:-./bin/trilink-mac}"
APP_BUNDLE="${OUTPUT_DIR}/${APP_NAME}.app"

echo "==> Creating ${APP_BUNDLE}..."
mkdir -p "${APP_BUNDLE}/Contents/MacOS"
mkdir -p "${APP_BUNDLE}/Contents/Resources"

cp "${BINARY_PATH}" "${APP_BUNDLE}/Contents/MacOS/TriLink"
chmod +x "${APP_BUNDLE}/Contents/MacOS/TriLink"

cat << 'EOF' > "${APP_BUNDLE}/Contents/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>TriLink</string>
    <key>CFBundleIdentifier</key>
    <string>com.narayanamalla.trilink</string>
    <key>CFBundleName</key>
    <string>TriLink</string>
    <key>CFBundleDisplayName</key>
    <string>TriLink</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>LSUIElement</key>
    <true/>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF

# Zip the .app bundle for easy download/sharing
(cd "${OUTPUT_DIR}" && zip -r -y "${APP_NAME}-macOS.zip" "${APP_NAME}.app")

echo "✓ Successfully created ${APP_BUNDLE} and ${OUTPUT_DIR}/${APP_NAME}-macOS.zip!"
