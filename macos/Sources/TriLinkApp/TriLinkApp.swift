import SwiftUI
import AppKit

@main
struct TriLinkApp: App {
    @StateObject private var appState = AppStateManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .frame(minWidth: 780, idealWidth: 840, minHeight: 520, idealHeight: 580)
        }
        .windowStyle(.titleBar)
        .windowToolbarStyle(.unified)

        MenuBarExtra("TriLink", systemImage: "bolt.horizontal.fill") {
            MenuBarView()
                .environmentObject(appState)
        }
    }
}
