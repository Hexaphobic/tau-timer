package com.chrispoole.intervaltimer

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Pushes the user's saved presets to the watch over the Wearable Data Layer at "/presets". */
object WearSync {
    /** [json] is PresetStore's on-disk format verbatim — the watch parses the same shape. */
    fun publish(context: Context, json: String) {
        val req = PutDataMapRequest.create("/presets").apply {
            dataMap.putString("json", json)
        }.asPutDataRequest().setUrgent()
        // No-op if there's no paired watch / no Play Services; harmless.
        runCatching { Wearable.getDataClient(context.applicationContext).putDataItem(req) }
    }
}
