package com.chrispoole.intervaltimer.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.chrispoole.intervaltimer.wear.timer.BUILTIN_PRESETS
import com.chrispoole.intervaltimer.wear.timer.Phase
import com.chrispoole.intervaltimer.wear.timer.Preset
import com.chrispoole.intervaltimer.wear.timer.SeqInterval
import org.json.JSONArray
import java.io.File

/** Built-in presets plus whatever the phone has synced over the Data Layer (cached to files). */
object PresetRepo {
    private var file: File? = null
    private var hiddenFile: File? = null
    val synced = mutableStateListOf<Preset>()
    // Built-in names the phone has deleted. Observable so a sync that only changes this still
    // recomposes the list. Persisted so a deletion survives the watch being offline.
    private var hidden by mutableStateOf(emptySet<String>())

    fun init(context: Context) {
        if (file != null) return
        val dir = context.applicationContext.filesDir
        file = File(dir, "synced_presets.json")
        hiddenFile = File(dir, "hidden_builtins.json")
        synced.clear()
        synced.addAll(file?.takeIf { it.exists() }?.let { parse(it.readText()) } ?: emptyList())
        hidden = hiddenFile?.takeIf { it.exists() }?.let { parseNames(it.readText()) } ?: emptySet()
    }

    fun all(): List<Preset> = BUILTIN_PRESETS.filterNot { it.name in hidden } + synced

    /** Called by the Data Layer listener when the phone pushes its preset list + hidden built-ins. */
    fun setFromPhone(json: String, hiddenNames: List<String>, context: Context) {
        init(context)
        synced.clear()
        synced.addAll(parse(json))
        file?.let { runCatching { it.writeText(json) } }
        hidden = hiddenNames.toSet()
        hiddenFile?.let { runCatching { it.writeText(JSONArray(hiddenNames).toString()) } }
    }

    private fun parse(json: String): List<Preset> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ivs = o.getJSONArray("intervals")
            Preset(
                o.getString("name"),
                (0 until ivs.length()).map { j ->
                    val s = ivs.getJSONObject(j)
                    SeqInterval(Phase.valueOf(s.getString("phase")), s.getInt("sec"))
                },
                // Absent on anything saved before overall repeats existed — that's a plain once-through.
                o.optInt("repeatAll", 1).coerceAtLeast(1),
            )
        }
    }.getOrDefault(emptyList())

    private fun parseNames(json: String): Set<String> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    }.getOrDefault(emptySet())
}
