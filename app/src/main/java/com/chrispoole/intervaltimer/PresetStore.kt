package com.chrispoole.intervaltimer

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.chrispoole.intervaltimer.model.BUILTIN_PRESETS
import com.chrispoole.intervaltimer.model.Phase
import com.chrispoole.intervaltimer.model.Preset
import com.chrispoole.intervaltimer.model.SeqInterval
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** User presets persisted as JSON in filesDir. Built-ins are always prepended, never persisted. */
object PresetStore {
    private var file: File? = null
    val saved = mutableStateListOf<Preset>()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "presets.json")
        file = f
        saved.clear()
        saved.addAll(load(f))
    }

    fun add(preset: Preset) { saved.add(preset); persist() }
    fun update(index: Int, preset: Preset) { if (index in saved.indices) { saved[index] = preset; persist() } }
    fun deleteAt(index: Int) { if (index in saved.indices) { saved.removeAt(index); persist() } }

    private fun persist() {
        val f = file ?: return
        val arr = JSONArray()
        for (p in saved) {
            val ivs = JSONArray()
            for (s in p.intervals) ivs.put(JSONObject().put("phase", s.phase.name).put("sec", s.durationSec))
            arr.put(JSONObject().put("name", p.name).put("intervals", ivs))
        }
        runCatching { f.writeText(arr.toString()) }
    }

    private fun load(f: File): List<Preset> {
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ivs = o.getJSONArray("intervals")
                val list = (0 until ivs.length()).map { j ->
                    val s = ivs.getJSONObject(j)
                    SeqInterval(Phase.valueOf(s.getString("phase")), s.getInt("sec"))
                }
                Preset(o.getString("name"), list)
            }
        }.getOrDefault(emptyList())
    }
}
