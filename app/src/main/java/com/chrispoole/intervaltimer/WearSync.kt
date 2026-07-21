package com.chrispoole.intervaltimer

import android.content.Context
import com.chrispoole.intervaltimer.model.Preset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject

/** Pushes the user's saved presets to the watch over the Wearable Data Layer at "/presets". */
object WearSync {
    fun publish(context: Context, presets: List<Preset>) {
        val arr = JSONArray()
        for (p in presets) {
            val ivs = JSONArray()
            for (s in p.intervals) ivs.put(JSONObject().put("phase", s.phase.name).put("sec", s.durationSec))
            arr.put(JSONObject().put("name", p.name).put("intervals", ivs))
        }
        val req = PutDataMapRequest.create("/presets").apply {
            dataMap.putString("json", arr.toString())
        }.asPutDataRequest().setUrgent()
        // No-op if there's no paired watch / no Play Services; harmless.
        runCatching { Wearable.getDataClient(context.applicationContext).putDataItem(req) }
    }
}
