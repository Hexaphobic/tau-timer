package com.chrispoole.intervaltimer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chrispoole.intervaltimer.ui.Palette

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

    var volume by mutableStateOf(1f); private set
    var muted by mutableStateOf(false); private set
    var languageCode by mutableStateOf("en"); private set
    var wordMode by mutableStateOf(true); private set
    var pureScript by mutableStateOf(false); private set // hide the Western/numeric fallback entirely
    var runInBackground by mutableStateOf(true); private set // keep the workout alive if the app is closed
    var palette by mutableStateOf(Palette.DEFAULT); private set // phase colours + home aurora
    // Orthogonal to the palette: kills the aura and leaves a black screen, whichever colours are on.
    var minimalBg by mutableStateOf(false); private set

    // Last values dialled in on the main screen, so a new workout starts from where you left off.
    var workSec by mutableStateOf(DEFAULT_WORK_SEC); private set
    var restSec by mutableStateOf(DEFAULT_REST_SEC); private set
    var rounds by mutableStateOf(DEFAULT_ROUNDS); private set

    // Lead-in before the first work interval — long enough to get gloves on if you set it that way.
    var prepareSec by mutableStateOf(DEFAULT_PREPARE_SEC); private set

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
        pureScript = p.getBoolean("pureScript", false)
        runInBackground = p.getBoolean("runInBackground", true)
        // Stored by name, so a palette dropped in a later version degrades to Default instead of
        // throwing on launch.
        palette = runCatching { Palette.valueOf(p.getString("palette", "") ?: "") }.getOrDefault(Palette.DEFAULT)
        workSec = p.getInt("workSec", DEFAULT_WORK_SEC)
        restSec = p.getInt("restSec", DEFAULT_REST_SEC)
        rounds = p.getInt("rounds", DEFAULT_ROUNDS)
        prepareSec = p.getInt("prepareSec", DEFAULT_PREPARE_SEC)
        minimalBg = p.getBoolean("minimalBg", false)
        hiddenBuiltins = p.getStringSet("hiddenBuiltins", emptySet()) ?: emptySet()
    }

    fun hideBuiltin(name: String) {
        hiddenBuiltins = hiddenBuiltins + name
        prefs?.edit()?.putStringSet("hiddenBuiltins", hiddenBuiltins)?.apply()
    }

    // Split so a slider drag doesn't queue a prefs write per touch sample. Beeper reads `volume`
    // live, so playback follows the drag either way; only the on-disk value waits for the lift.
    fun updateVolume(v: Float) { volume = v.coerceIn(0f, 1f) }
    fun persistVolume() { prefs?.edit()?.putFloat("volume", volume)?.apply() }
    fun updateMuted(m: Boolean) { muted = m; prefs?.edit()?.putBoolean("muted", m)?.apply() }
    fun updateLanguage(code: String) { languageCode = code; prefs?.edit()?.putString("lang", code)?.apply() }
    fun updateWordMode(w: Boolean) { wordMode = w; prefs?.edit()?.putBoolean("wordMode", w)?.apply() }
    fun updatePureScript(p2: Boolean) { pureScript = p2; prefs?.edit()?.putBoolean("pureScript", p2)?.apply() }
    fun updateRunInBackground(b: Boolean) { runInBackground = b; prefs?.edit()?.putBoolean("runInBackground", b)?.apply() }
    fun updatePalette(p2: Palette) { palette = p2; prefs?.edit()?.putString("palette", p2.name)?.apply() }
    fun updateMinimalBg(b: Boolean) { minimalBg = b; prefs?.edit()?.putBoolean("minimalBg", b)?.apply() }
    fun updateWorkSec(s: Int) { workSec = s; prefs?.edit()?.putInt("workSec", s)?.apply() }
    fun updateRestSec(s: Int) { restSec = s; prefs?.edit()?.putInt("restSec", s)?.apply() }
    fun updateRounds(r: Int) { rounds = r; prefs?.edit()?.putInt("rounds", r)?.apply() }
    fun updatePrepareSec(s: Int) {
        prepareSec = s.coerceIn(0, 600)
        prefs?.edit()?.putInt("prepareSec", prepareSec)?.apply()
    }
}
