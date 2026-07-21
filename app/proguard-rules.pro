# R8 rules for the release build.

# Phase is persisted by constant NAME to JSON — presets.json on disk and the Wear Data Layer — and
# read back with Phase.valueOf(...). If R8 obfuscated the constants, `.name` would emit renamed
# strings and valueOf would fail to round-trip a preset saved by an earlier build. Keep it intact.
-keep class com.chrispoole.intervaltimer.model.Phase { *; }

# org.json ships with the Android platform; Compose, AndroidX, and play-services-wearable bundle
# their own consumer rules, so nothing else needs keeping here.
