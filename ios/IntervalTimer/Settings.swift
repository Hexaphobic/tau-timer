import Foundation
import SwiftUI
import IntervalTimerCore

// Stock values: the initial state, and what double-tapping a stepper's number goes back to. One
// source, so the two can't drift apart.
let DEFAULT_WORK_SEC = 30
let DEFAULT_REST_SEC = 15
let DEFAULT_ROUNDS = 8
let DEFAULT_PREPARE_SEC = 5

/// Local, UserDefaults-backed app settings. Observable so the UI reacts, and read live by the
/// Beeper each time it plays. Single process, single user — a shared instance is the lazy right call.
final class Settings: ObservableObject {
    static let shared = Settings()

    private let defaults = UserDefaults.standard

    // Volume is read by the Beeper on every cue, and the three home values on every stepper frame.
    //
    // Deliberately NOT @Published — the only view showing it is VolumeSlider, which owns its thumb
    // locally. @ObservedObject has no per-property tracking, so publishing per touch sample re-ran
    // the whole settings body — 24 shader swatches and the full language grid — at drag rate. The
    // Beeper reads the stored value at play(), so a cue mid-drag still gets the live volume.
    // (Compose doesn't have this problem: snapshot state only recomposes actual readers.)
    private(set) var volume: Double
    @Published private(set) var muted: Bool
    @Published private(set) var languageCode: String
    @Published private(set) var wordMode: Bool
    /// Keep the workout alive if the app is backgrounded.
    @Published private(set) var runInBackground: Bool
    /// Phase colours + home aurora.
    @Published private(set) var palette: Palette
    /// Orthogonal to the palette: kills the aura and leaves a black screen, whichever colours are on.
    @Published private(set) var minimalBg: Bool

    /// The main screen as you left it. Sections and the intervals inside them, not just one work/rest
    /// pair: a section can hold a sequence of its own, so storing three numbers would quietly throw
    /// away most of what you built the moment you closed the app.
    @Published private(set) var home: [Block]

    /// The home's outer ×N — the whole thing, top to bottom, that many times. Its own key rather than
    /// a field inside the home JSON: it belongs to the screen, not to any section, and keeping the
    /// list's wire shape untouched means an older build still reads the sections it understands.
    @Published private(set) var homeRepeatAll: Int

    /// Lead-in before the first work interval — long enough to get gloves on if you set it that way.
    @Published private(set) var prepareSec: Int

    /// Built-in presets the user has deleted. They aren't in presets.json (they're compiled in), so
    /// "deleting" one is remembering its name and filtering it out of the list.
    @Published private(set) var hiddenBuiltins: Set<String>

    private init() {
        let d = UserDefaults.standard
        // `object(forKey:)` first, because `bool(forKey:)` and friends return 0/false for a key
        // that was never written — which would silently flip every defaulted-on switch off.
        volume = d.object(forKey: "volume") as? Double ?? 1
        muted = d.object(forKey: "muted") as? Bool ?? false
        languageCode = d.string(forKey: "lang") ?? "en"
        wordMode = d.object(forKey: "wordMode") as? Bool ?? true
        runInBackground = d.object(forKey: "runInBackground") as? Bool ?? true
        // Stored by name, so a palette dropped in a later version degrades to Default instead of
        // failing on launch.
        palette = Palette(rawValue: d.string(forKey: "palette") ?? "") ?? .standard
        // Falls back to the three loose values an older build wrote, so an existing install comes
        // back to the home it left rather than to the stock one.
        home = Settings.decodeHome(d.data(forKey: "home"))
            ?? [basicBlock(workSec: d.object(forKey: "workSec") as? Int ?? DEFAULT_WORK_SEC,
                           restSec: d.object(forKey: "restSec") as? Int ?? DEFAULT_REST_SEC,
                           rounds: d.object(forKey: "rounds") as? Int ?? DEFAULT_ROUNDS)]
        homeRepeatAll = max(d.object(forKey: "homeRepeatAll") as? Int ?? 1, 1)
        prepareSec = d.object(forKey: "prepareSec") as? Int ?? DEFAULT_PREPARE_SEC
        minimalBg = d.object(forKey: "minimalBg") as? Bool ?? false
        hiddenBuiltins = Set(d.stringArray(forKey: "hiddenBuiltins") ?? [])
    }

    func hideBuiltin(_ name: String) {
        hiddenBuiltins.insert(name)
        defaults.set(Array(hiddenBuiltins), forKey: "hiddenBuiltins")
    }

    // Split so a slider drag doesn't queue a defaults write per touch sample. The Beeper reads
    // `volume` live, so playback follows the drag either way; only the stored value waits for the lift.
    func updateVolume(_ v: Double) { volume = min(max(v, 0), 1) }
    func persistVolume() { defaults.set(volume, forKey: "volume") }

    func updateMuted(_ m: Bool) { muted = m; defaults.set(m, forKey: "muted") }
    func updateLanguage(_ code: String) { languageCode = code; defaults.set(code, forKey: "lang") }
    func updateWordMode(_ w: Bool) { wordMode = w; defaults.set(w, forKey: "wordMode") }
    func updateRunInBackground(_ b: Bool) { runInBackground = b; defaults.set(b, forKey: "runInBackground") }
    func updatePalette(_ p: Palette) { palette = p; defaults.set(p.rawValue, forKey: "palette") }
    func updateMinimalBg(_ b: Bool) { minimalBg = b; defaults.set(b, forKey: "minimalBg") }
    func updateHome(_ blocks: [Block]) {
        guard !blocks.isEmpty else { return }
        home = blocks
        defaults.set(try? JSONEncoder().encode(blocks.map(HomeBlockDTO.init)), forKey: "home")
    }

    func updateHomeRepeatAll(_ n: Int) {
        homeRepeatAll = max(n, 1)
        defaults.set(homeRepeatAll, forKey: "homeRepeatAll")
    }

    /// Nil for anything unreadable, so the caller falls back rather than starting on an empty home.
    /// A section that survives decoding but holds nothing is dropped: the UI's floor is one interval,
    /// and a card with none would render an empty box you couldn't delete.
    private static func decodeHome(_ data: Data?) -> [Block]? {
        guard let data, let dto = try? JSONDecoder().decode([HomeBlockDTO].self, from: data) else { return nil }
        let blocks = dto.map(\.block).filter { !$0.items.isEmpty }
        return blocks.isEmpty ? nil : blocks
    }
    func updatePrepareSec(_ s: Int) {
        prepareSec = min(max(s, 0), 600)
        defaults.set(prepareSec, forKey: "prepareSec")
    }
}

/// The home layout's wire shape — the same one Android writes into its prefs, so the two builds
/// describe a section identically even though nothing syncs between them.
private struct HomeBlockDTO: Codable {
    let items: [ItemDTO]
    let `repeat`: Int
    /// Optional, and nil rather than "" when there is no name: a synthesised `encode(to:)` skips a
    /// nil entirely, so an unnamed home writes the exact bytes it always did — which is also what
    /// keeps a build that predates names reading it.
    let name: String?

    struct ItemDTO: Codable {
        let phase: String
        let sec: Int
    }

    init(_ b: Block) {
        items = b.items.map { ItemDTO(phase: $0.phase.rawValue, sec: $0.durationSec) }
        self.repeat = b.repeatCount
        name = b.name.isEmpty ? nil : b.name
    }

    var block: Block {
        Block(items.map { SeqInterval(Phase(rawValue: $0.phase) ?? .work, $0.sec) },
              max(self.repeat, 1), name ?? "")
    }
}
