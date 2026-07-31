import SwiftUI
import IntervalTimerCore

@main
struct IntervalTimerApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                // Grayscale chrome, black canvas — colour is reserved for communicating the
                // interval phase in the timer.
                .preferredColorScheme(.dark)
                .statusBarHidden(true)
                .persistentSystemOverlays(.hidden)
        }
    }
}

/// One home section with a stable id, so drag and the item animations can track it across moves.
struct HomeRow: Identifiable, Equatable {
    let id: Int
    var block: Block
}

enum Screen { case setup, settings, presets, editor }

struct RootView: View {
    @StateObject private var engine = TimerEngine()
    @ObservedObject private var settings = Settings.shared
    @Environment(\.scenePhase) private var scenePhase

    @State private var screen: Screen = .setup
    @State private var editIndex: Int?
    @State private var editBuiltin: Preset?
    // The home screen's sections. Hoisted here so a trip to Settings or Presets doesn't wipe an
    // in-progress sequence; seeded from the home as it was left.
    @State private var rows: [HomeRow] = Settings.shared.home.enumerated()
        .map { HomeRow(id: $0.offset, block: $0.element) }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            content
        }
        // Debug builds can be launched straight onto a screen: `simctl launch <id> -startScreen
        // presets`. The simulator takes no synthetic input without a device-access grant, so this is
        // how each screen gets looked at without a human tapping through to it.
        .onAppear {
            #if DEBUG
            switch UserDefaults.standard.string(forKey: "startScreen") {
            case "presets": screen = .presets
            case "settings": screen = .settings
            case "editor": screen = .editor
            case "timer": engine.start(homePreset(rows.map(\.block)).toWorkout(prepareMs: 5_000))
            default: break
            }
            #endif
        }
        .onChange(of: scenePhase) { _, phase in
            // "Run in background" off means leaving the app ends the workout. On Android that hangs
            // off the task being removed; the iOS equivalent of walking away is backgrounding.
            if phase == .background, !settings.runInBackground, engine.isRunning {
                engine.stop()
            }
        }
    }

    @ViewBuilder private var content: some View {
        if engine.state.running {
            TimerView(
                ui: engine.state,
                onPause: { engine.pause() },
                onResume: { engine.resume() },
                onEnd: { engine.stop() }
            )
        } else {
            switch screen {
            case .settings:
                SettingsView(onBack: { screen = .setup })
            case .presets:
                PresetsView(
                    onBack: { screen = .setup },
                    onStart: { engine.start($0.toWorkout(prepareMs: settings.prepareSec * 1000)) },
                    onNew: { editIndex = nil; editBuiltin = nil; screen = .editor },
                    onEdit: { editIndex = $0; editBuiltin = nil; screen = .editor },
                    onEditBuiltin: { editIndex = nil; editBuiltin = $0; screen = .editor }
                )
            case .editor:
                EditorView(
                    initial: editIndex.flatMap { PresetStore.shared.saved.indices.contains($0)
                        ? PresetStore.shared.saved[$0] : nil } ?? editBuiltin,
                    // Leave the editor as the workout starts. Its draft lives in @State inside that
                    // view, so the running timer takes it out of the hierarchy and it is gone — and
                    // on End the branch would rebuild from `initial`, silently showing the *original*
                    // preset (or a blank Work 30 / Rest 15) as though it were the user's unsaved
                    // work, ready for Save to write it over the top. Landing on Presets is honest
                    // about the loss.
                    onStart: {
                        engine.start($0.toWorkout(prepareMs: settings.prepareSec * 1000))
                        screen = .presets
                    },
                    onSave: { p in
                        if let idx = editIndex {
                            PresetStore.shared.update(idx, p)
                        } else if let original = editBuiltin {
                            // The copy becomes a saved preset and the original goes away by name —
                            // the same mechanism deleting one uses — so the list ends up holding
                            // the edited version once.
                            PresetStore.shared.add(p)
                            settings.hideBuiltin(original.name)
                        } else {
                            PresetStore.shared.add(p)
                        }
                        screen = .presets
                    },
                    onCancel: { screen = .presets }
                )
            case .setup:
                HomeView(
                    rows: $rows,
                    onGo: { engine.start($0) },
                    onSettings: { screen = .settings },
                    onPresets: { screen = .presets }
                )
            }
        }
    }
}
