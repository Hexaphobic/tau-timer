package com.chrispoole.intervaltimer

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Pushes the user's presets to the watch over the Wearable Data Layer at "/presets". */
object WearSync {
    /**
     * [json] is PresetStore's on-disk format verbatim; [hidden] is the set of built-in names the
     * user has deleted, so the watch can hide the same ones from its own compiled-in list.
     */
    fun publish(context: Context, json: String, hidden: Set<String>) {
        val req = PutDataMapRequest.create("/presets").apply {
            dataMap.putString("json", json)
            dataMap.putStringArrayList("hidden", ArrayList(hidden))
        }.asPutDataRequest().setUrgent()
        // No-op if there's no paired watch / no Play Services; harmless.
        runCatching { Wearable.getDataClient(context.applicationContext).putDataItem(req) }
    }
}
