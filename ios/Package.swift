// swift-tools-version: 5.10
import PackageDescription

let package = Package(
  name: "TagSpeakCore",
  platforms: [
    .iOS(.v17),
    .macOS(.v14),
  ],
  products: [
    .library(
      name: "TagSpeakCore",
      targets: ["TagSpeakCore"]
    )
  ],
  targets: [
    .target(
      name: "TagSpeakCore",
      path: "Sources/TagSpeakCore"
    ),
    .testTarget(
      name: "TagSpeakCoreTests",
      dependencies: ["TagSpeakCore"],
      path: "Tests/TagSpeakCoreTests"
    ),
  ]
)
