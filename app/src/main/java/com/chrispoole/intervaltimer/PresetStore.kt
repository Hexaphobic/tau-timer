package com.chrispoole.intervaltimer

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.Preset
import com.chrispoole.intervaltimer.model.SeqInterval
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** User presets persisted as JSON in filesDir. Built-ins are always prepended, never persisted. */
object PresetStore {
    private var file: File? = null
    private var appContext: Context? = null
    val saved = mutableStateListOf<Preset>()

    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        val f = File(context.applicationContext.filesDir, "presets.json")
        file = f
        saved.clear()
        saved.addAll(load(f))
        pushToWatch() // sync whatever's already saved on launch
    }

    fun add(preset: Preset) { saved.add(preset); persist() }
    fun update(index: Int, preset: Preset) { if (index in saved.indices) { saved[index] = preset; persist() } }
    fun deleteAt(index: Int) { if (index in saved.indices) { saved.removeAt(index); persist() } }

    /** One wire format for both the file and the watch, so the two can't drift apart. */
    private fun json(): String {
        val arr = JSONArray()
        for (p in saved) {
            val ivs = JSONArray()
            for (s in p.intervals) ivs.put(JSONObject().put("phase", s.phase.name).put("sec", s.durationSec))
            // repeatAll is only written when it's doing something: an older build (or an older
            // watch) reading this file just sees the sequence it always saw.
            val o = JSONObject().put("name", p.name).put("intervals", ivs)
            if (p.repeatAll > 1) o.put("repeatAll", p.repeatAll)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun persist() {
        // Write-then-rename, not truncate-in-place: writeText leaves a partial file if the process
        // dies before writeback, load() turns a file it can't parse at all into emptyList(), and the
        // next persist() then makes that loss permanent — and pushes it to the watch. Replace-via-
        // rename gets the data out before the name flips. renameTo reports failure by returning false
        // rather than throwing, so check() exists only to route that false down the same path as a
        // write exception, which is what gets the stale presets.json.tmp deleted — nothing reads it,
        // and a leftover one would be the newer content under the wrong name. It does not make the
        // failure visible: on a full disk memory and the watch still move on while presets.json keeps
        // the old list, and it's the next launch's load() + init() push that reconverges the three,
        // onto the old list, so the edit comes back gone. Surfacing that would need a path out of
        // persist() to the UI, which nothing here has.
        file?.let { f ->
            val tmp = File(f.parentFile, "${f.name}.tmp")
            runCatching { tmp.writeText(json()); check(tmp.renameTo(f)) }.onFailure { tmp.delete() }
        }
        pushToWatch()
    }

    /**
     * Sync the saved presets AND the set of hidden built-ins to the watch, so deleting a built-in on
     * the phone hides it there too. Public so hiding a built-in (which doesn't touch presets.json)
     * can still trigger a push.
     */
    fun pushToWatch() {
        appContext?.let { WearSync.publish(it, json(), Settings.hiddenBuiltins) }
    }

    private fun load(f: File): List<Preset> {
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            // Catch per entry, not per file: one preset naming a Phase this build doesn't have (a
            // downgrade, a hand edit) used to take the whole library down with it, and the next
            // persist() wrote that emptiness back over the file and pushed it to the watch. Losing
            // the one bad preset is recoverable; losing all of them isn't.
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    val ivs = o.getJSONArray("intervals")
                    val list = (0 until ivs.length()).map { j ->
                        val s = ivs.getJSONObject(j)
                        SeqInterval(Phase.valueOf(s.getString("phase")), s.getInt("sec"))
                    }
                    Preset(o.getString("name"), list, o.optInt("repeatAll", 1).coerceAtLeast(1))
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}
