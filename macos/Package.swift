// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TriLink",
    platforms: [
        .macOS(.v13)
    ],
    products: [
        .library(name: "TriLinkCore", targets: ["TriLinkCore"]),
        .executable(name: "trilink-mac", targets: ["TriLinkApp"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "TriLinkCore",
            dependencies: [],
            path: "Sources/TriLinkCore"
        ),
        .executableTarget(
            name: "TriLinkApp",
            dependencies: ["TriLinkCore"],
            path: "Sources/TriLinkApp"
        ),
        .testTarget(
            name: "TriLinkTests",
            dependencies: ["TriLinkCore"],
            path: "Tests/TriLinkTests"
        )
    ]
)
