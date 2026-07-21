package com.chrispoole.intervaltimer.wear.sync

import com.chrispoole.intervaltimer.wear.PresetRepo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives the phone's preset list pushed over the Data Layer at "/presets". */
class PhoneListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == PATH) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = map.getString("json") ?: continue
                val hidden = map.getStringArrayList("hidden") ?: arrayListOf()
                PresetRepo.setFromPhone(json, hidden, applicationContext)
            }
        }
    }

    companion object {
        const val PATH = "/presets"
    }
}
