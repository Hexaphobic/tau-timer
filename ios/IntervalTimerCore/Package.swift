// swift-tools-version: 5.9
import PackageDescription

// The pure logic, split out so it is testable from the command line — `swift test` here needs no
// simulator, no signing and no Xcode project, which is what lets the timer's rules be verified
// independently of any UI that might hide a bug in them.
//
// The app links this as a local package. Nothing in it may import UIKit or SwiftUI: the moment it
// does, the command-line test run stops working and the split has bought nothing.
let package = Package(
    name: "IntervalTimerCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "IntervalTimerCore", targets: ["IntervalTimerCore"])],
    targets: [
        .target(name: "IntervalTimerCore"),
        .testTarget(name: "IntervalTimerCoreTests", dependencies: ["IntervalTimerCore"]),
    ]
)
