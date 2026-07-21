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
                val json = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("json") ?: continue
                PresetRepo.setFromPhone(json, applicationContext)
            }
        }
    }

    companion object {
        const val PATH = "/presets"
    }
}
