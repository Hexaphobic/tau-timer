package com.chrispoole.intervaltimer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.chrispoole.intervaltimer.model.Block
import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.SeqInterval
import com.chrispoole.intervaltimer.model.basicBlock
import com.chrispoole.intervaltimer.ui.Palette
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, prefs-backed app settings. Compose-observable so the UI reacts, and read live by the
 * Beeper each time it plays. Single process, single user — a global object is the lazy right call.
 */
// Stock values: the initial state, and what double-tapping a stepper's number goes back to. One
// source, so the two can't drift apart.
const val DEFAULT_WORK_SEC = 30
const val DEFAULT_REST_SEC = 15
const val DEFAULT_ROUNDS = 8
const val DEFAULT_PREPARE_SEC = 5

object Settings {
    private const val PREFS = "settings"
    private var prefs: android.content.SharedPreferences? = null

    // Primitive state holders: volume is read by the Beeper on every cue, and the three home values
    // on every stepper frame — no reason to box them.
    var volume by mutableFloatStateOf(1f); private set
    var muted by mutableStateOf(false); private set
    var languageCode by mutableStateOf("en"); private set
    var wordMode by mutableStateOf(true); private set
    var runInBackground by mutableStateOf(true); private set // keep the workout alive if the app is closed
    var palette by mutableStateOf(Palette.DEFAULT); private set // phase colours + home aurora
    // Orthogonal to the palette: kills the aura and leaves a black screen, whichever colours are on.
    var minimalBg by mutableStateOf(false); private set

    // Two rests in a row is one longer rest with extra steps. On (the default), the editor won't
    // build one; off, you're free to (a stretch-then-breathe cooldown, say).
    var noDoubleRest by mutableStateOf(true); private set

    // The main screen as you left it. Sections and the intervals inside them, not just one work/rest
    // pair: a section can hold a sequence of its own, so storing three numbers would quietly throw
    // away most of what you built the moment you closed the app.
    var home by mutableStateOf(listOf(basicBlock(DEFAULT_WORK_SEC, DEFAULT_REST_SEC, DEFAULT_ROUNDS)))
        private set

    // The home's outer ×N — the whole thing, top to bottom, that many times. Its own key rather than
    // a field inside the home JSON: it belongs to the screen, not to any section, and keeping the
    // list's wire shape untouched means an older build still reads the sections it understands.
    var homeRepeatAll by mutableIntStateOf(1); private set

    // Lead-in before the first work interval — long enough to get gloves on if you set it that way.
    var prepareSec by mutableIntStateOf(DEFAULT_PREPARE_SEC); private set

    // Built-in presets the user has deleted. They don't live in presets.json (they're compiled in),
    // so "deleting" one is remembering its name and filtering it out of the list.
    var hiddenBuiltins by mutableStateOf(emptySet<String>()); private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        volume = p.getFloat("volume", 1f)
        muted = p.getBoolean("muted", false)
        languageCode = p.getString("lang", "en") ?: "en"
        wordMode = p.getBoolean("wordMode", true)
        runInBackground = p.getBoolean("runInBackground", true)
        // Stored by name, so a palette dropped in a later version degrades to Default instead of
        // throwing on launch.
        palette = runCatching { Palette.valueOf(p.getString("palette", "") ?: "") }.getOrDefault(Palette.DEFAULT)
        noDoubleRest = p.getBoolean("noDoubleRest", true)
        // Falls back to the three loose values an older build wrote, so an existing install comes
        // back to the home it left rather than to the stock one.
        home = parseHome(p.getString("home", null))
            ?: listOf(basicBlock(
                p.getInt("workSec", DEFAULT_WORK_SEC),
                p.getInt("restSec", DEFAULT_REST_SEC),
                p.getInt("rounds", DEFAULT_ROUNDS),
            ))
        homeRepeatAll = p.getInt("homeRepeatAll", 1).coerceAtLeast(1)
        prepareSec = p.getInt("prepareSec", DEFAULT_PREPARE_SEC)
        minimalBg = p.getBoolean("minimalBg", false)
        hiddenBuiltins = p.getStringSet("hiddenBuiltins", emptySet()) ?: emptySet()
    }

    fun hideBuiltin(name: String) {
        hiddenBuiltins = hiddenBuiltins + name
        prefs?.edit { putStringSet("hiddenBuiltins", hiddenBuiltins) }
    }

    // core-ktx's SharedPreferences.edit {} rather than edit().putX().apply() — it applies for you,
    // so a setter can't half-write by forgetting the trailing apply().
    // Split so a slider drag doesn't queue a prefs write per touch sample. Beeper reads `volume`
    // live, so playback follows the drag either way; only the on-disk value waits for the lift.
    fun updateVolume(v: Float) { volume = v.coerceIn(0f, 1f) }
    fun persistVolume() { prefs?.edit { putFloat("volume", volume) } }
    fun updateMuted(m: Boolean) { muted = m; prefs?.edit { putBoolean("muted", m) } }
    fun updateLanguage(code: String) { languageCode = code; prefs?.edit { putString("lang", code) } }
    fun updateWordMode(w: Boolean) { wordMode = w; prefs?.edit { putBoolean("wordMode", w) } }
    fun updateRunInBackground(b: Boolean) { runInBackground = b; prefs?.edit { putBoolean("runInBackground", b) } }
    fun updatePalette(p2: Palette) { palette = p2; prefs?.edit { putString("palette", p2.name) } }
    fun updateMinimalBg(b: Boolean) { minimalBg = b; prefs?.edit { putBoolean("minimalBg", b) } }
    fun updateNoDoubleRest(b: Boolean) { noDoubleRest = b; prefs?.edit { putBoolean("noDoubleRest", b) } }
    fun updateHome(blocks: List<Block>) {
        if (blocks.isEmpty()) return
        home = blocks
        prefs?.edit { putString("home", homeJson(blocks)) }
    }

    fun updateHomeRepeatAll(n: Int) {
        homeRepeatAll = n.coerceAtLeast(1)
        prefs?.edit { putInt("homeRepeatAll", homeRepeatAll) }
    }

    fun updatePrepareSec(s: Int) {
        prepareSec = s.coerceIn(0, 600)
        prefs?.edit { putInt("prepareSec", prepareSec) }
    }

    // Same wire shape as presets.json, one level deeper: a section is its interval list plus how
    // many times it runs.
    private fun homeJson(blocks: List<Block>): String {
        val arr = JSONArray()
        for (b in blocks) {
            val items = JSONArray()
            for (s in b.items) items.put(JSONObject().put("phase", s.phase.name).put("sec", s.durationSec))
            arr.put(JSONObject().put("items", items).put("repeat", b.repeat))
        }
        return arr.toString()
    }

    /**
     * Null for anything unreadable, so the caller falls back rather than starting on an empty home.
     * A section that survives parsing but holds nothing is dropped: the UI's floor is one interval,
     * and a card with none would render an empty box you couldn't delete.
     */
    private fun parseHome(raw: String?): List<Block>? = runCatching {
        if (raw.isNullOrBlank()) return null
        val arr = JSONArray(raw)
        val blocks = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val items = o.getJSONArray("items")
            val list = (0 until items.length()).map { j ->
                val it = items.getJSONObject(j)
                SeqInterval(Phase.valueOf(it.getString("phase")), it.getInt("sec"))
            }
            if (list.isEmpty()) null else Block(list, o.optInt("repeat", 1).coerceAtLeast(1))
        }
        blocks.ifEmpty { null }
    }.getOrNull()
}
