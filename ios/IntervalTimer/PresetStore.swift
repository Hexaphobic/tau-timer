import Foundation
import IntervalTimerCore

/// User presets persisted as JSON in the app container. Built-ins are always prepended, never saved.
///
/// The wire format is byte-for-byte the Android one, so a presets.json from either build reads on
/// the other — `repeatAll` is only written when it's doing something, exactly as there.
final class PresetStore: ObservableObject {
    static let shared = PresetStore()

    @Published private(set) var saved: [Preset] = []

    private let url: URL

    private init() {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        url = dir.appendingPathComponent("presets.json")
        saved = PresetStore.load(url)
    }

    func add(_ preset: Preset) { saved.append(preset); persist() }

    func update(_ index: Int, _ preset: Preset) {
        guard saved.indices.contains(index) else { return }
        saved[index] = preset
        persist()
    }

    func deleteAt(_ index: Int) {
        guard saved.indices.contains(index) else { return }
        saved.remove(at: index)
        persist()
    }

    private func persist() {
        let dto = saved.map { p in
            PresetDTO(name: p.name,
                      intervals: p.intervals.map {
                          IntervalDTO(phase: $0.phase, sec: $0.durationSec,
                                      label: $0.label.isEmpty ? nil : $0.label)
                      },
                      repeatAll: p.repeatAll > 1 ? p.repeatAll : nil)
        }
        guard let data = try? JSONEncoder().encode(dto) else { return }
        // Atomic, not a plain overwrite: a truncating write leaves a partial file if the process dies
        // mid-flush, load() turns any unreadable file into an empty list, and the next save would
        // then make that loss permanent. `.atomic` writes to a temp file and renames.
        try? data.write(to: url, options: .atomic)
    }

    private static func load(_ url: URL) -> [Preset] {
        guard let data = try? Data(contentsOf: url),
              let dto = try? JSONDecoder().decode([Lenient].self, from: data) else { return [] }
        return dto.compactMap(\.value).map { p in
            Preset(p.name,
                   p.intervals.map { SeqInterval($0.phase, $0.sec, $0.label ?? "") },
                   repeatAll: max(p.repeatAll ?? 1, 1))
        }
    }
}

/// Decodes to nil instead of throwing, so a preset this build can't read costs that preset and not
/// the library. Decoding the array as `[PresetDTO]` is all-or-nothing: one entry naming a Phase that
/// doesn't exist here — a downgrade, a hand edit, a file written by a newer Android build — took
/// every other preset down with it, and the next save wrote that emptiness back over the file.
/// Losing the one bad preset is recoverable; losing all of them isn't. Android drops per entry in
/// exactly the same places (PresetStore.load, PresetRepo.parse), which matters because the same
/// presets.json moves between the two.
///
/// A malformed entry still costs the whole *file* if the outer JSON isn't an array at all — that one
/// really is unrecoverable, and it's what the atomic write in persist() exists to prevent.
private struct Lenient: Decodable {
    let value: PresetDTO?
    init(from decoder: Decoder) throws { value = try? PresetDTO(from: decoder) }
}

private struct PresetDTO: Codable {
    let name: String
    let intervals: [IntervalDTO]
    let repeatAll: Int?
}

// `phase` is the Phase itself rather than its String: the raw values ARE the Android names, so the
// bytes are unchanged either way, but decoding it strictly is what makes an unknown phase fail the
// entry — the same drop Android's Phase.valueOf gives it — instead of silently coercing to .work and
// handing back a workout the user never built.
private struct IntervalDTO: Codable {
    let phase: Phase
    let sec: Int
    /// The section name, written per interval — optional so an unnamed preset's bytes are unchanged,
    /// and so every presets.json written before names still decodes.
    let label: String?
}
