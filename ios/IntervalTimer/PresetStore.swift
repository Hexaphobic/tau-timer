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
                      intervals: p.intervals.map { IntervalDTO(phase: $0.phase.rawValue, sec: $0.durationSec) },
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
              let dto = try? JSONDecoder().decode([PresetDTO].self, from: data) else { return [] }
        return dto.map { p in
            Preset(p.name,
                   p.intervals.map { SeqInterval(Phase(rawValue: $0.phase) ?? .work, $0.sec) },
                   repeatAll: max(p.repeatAll ?? 1, 1))
        }
    }
}

private struct PresetDTO: Codable {
    let name: String
    let intervals: [IntervalDTO]
    let repeatAll: Int?
}

private struct IntervalDTO: Codable {
    let phase: String
    let sec: Int
}
