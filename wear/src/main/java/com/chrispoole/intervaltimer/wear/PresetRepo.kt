package com.chrispoole.intervaltimer.wear

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.chrispoole.intervaltimer.wear.timer.BUILTIN_PRESETS
import com.chrispoole.intervaltimer.wear.timer.Phase
import com.chrispoole.intervaltimer.wear.timer.Preset
import com.chrispoole.intervaltimer.wear.timer.SeqInterval
import org.json.JSONArray
import java.io.File

/** Built-in presets plus whatever the phone has synced over the Data Layer (cached to a file). */
object PresetRepo {
    private var file: File? = null
    val synced = mutableStateListOf<Preset>()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "synced_presets.json")
        file = f
        synced.clear()
        synced.addAll(if (f.exists()) parse(f.readText()) else emptyList())
    }

    fun all(): List<Preset> = BUILTIN_PRESETS + synced

    /** Called by the Data Layer listener when the phone pushes its preset list. */
    fun setFromPhone(json: String, context: Context) {
        init(context)
        synced.clear()
        synced.addAll(parse(json))
        file?.let { runCatching { it.writeText(json) } }
    }

    private fun parse(json: String): List<Preset> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ivs = o.getJSONArray("intervals")
            Preset(o.getString("name"), (0 until ivs.length()).map { j ->
                val s = ivs.getJSONObject(j)
                SeqInterval(Phase.valueOf(s.getString("phase")), s.getInt("sec"))
            })
        }
    }.getOrDefault(emptyList())
}
