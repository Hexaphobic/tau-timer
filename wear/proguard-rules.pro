# R8 rules for the watch release build.

# Phase is persisted and synced by constant NAME — the phone writes it into the /presets DataItem,
# PresetRepo reads it back with Phase.valueOf (PresetRepo.kt), and WearTimerService renders
# phase.name into the ongoing notification. Obfuscated, valueOf throws on every synced preset (so
# the whole list silently empties) and the notification label turns to noise.
-keep class com.chrispoole.intervaltimer.wear.timer.Phase { *; }

# org.json ships with the platform; Wear Compose and play-services-wearable bring their own
# consumer rules, so nothing else needs keeping here.
