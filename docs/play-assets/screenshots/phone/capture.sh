#!/usr/bin/env bash
# Recapture the five Play phone screenshots off the Flip over adb.
#
#   ./docs/play-assets/screenshots/phone/capture.sh
#
# It prompts you screen by screen, grabs a 1080x2520 screencap for each, then widens every one to
# 1260x2520 through tools/widen_screenshots.py -- Play caps screenshots at 2:1 and rejected the raw
# 2.33:1 captures once already (eb45817). Raw frames are kept in ./raw/ so a reframe never needs a
# rerun of the whole set.
#
# There is NO start-screen flag on Android. The iOS twin takes `-startScreen presets` (see
# IntervalTimerApp.swift), the Android build has no equivalent -- MainActivity holds `screen` in a
# plain remember and the manifest declares only MAIN/LAUNCHER. So you tap through by hand.
#
# BEFORE YOU START
#   * The debug APK has no applicationIdSuffix: it is com.chrispoole.intervaltimer, the same id as
#     the Play build, so `adb install -r` over a Play-installed release fails on signature mismatch
#     and `adb uninstall` first wipes your settings and saved presets. Back them up or accept it.
#   * Shot 05 needs Play's own formatted price. Billing.price is null until Play returns the
#     product, so a sideloaded debug APK renders "Six themes come with the unlock" with no price at
#     all. Capture at least that frame from an internal-testing build on a licence-tested account,
#     or the listing advertises a string the app never shows.
#   * Hold these constant across all five, or the set stops reading as one series:
#       theme Default (ringed)   Minimal off   language English   Get ready 5s
#       Mute off   Volume full   Run in background on   sections UNNAMED
#   * Word mode: the default is ON, and under 60s that prints "twenty-five", not "25". The old
#     01-running.png has "25", so it was shot with Word mode OFF -- while 05-themes.png shows the
#     switch ON. The old set contradicts itself. Pick one now and hold it; 05 shows the switch, so
#     whichever you pick is visible in the set either way.
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p raw
REPO=$(cd ../../../.. && pwd)

adb get-state >/dev/null 2>&1 || { echo "no device: plug in the Flip, enable USB debugging, accept the prompt"; exit 1; }
echo "device: $(adb shell getprop ro.product.model | tr -d '\r')  $(adb shell wm size | tr -d '\r')"

shoot() { # shoot <file> <what to set up>
  printf '\n=== %s ===\n%s\n' "$1" "$2"
  read -r -p "set it up, then press Enter (s to skip) " k
  [ "$k" = s ] && return 0
  adb exec-out screencap -p > "raw/$1"
  python3 -c "
from PIL import Image; im=Image.open('raw/$1')
print('  captured', im.size[0], 'x', im.size[1])
assert im.size==(1080,2520), 'unexpected size -- check the device is on its main screen, not the cover'
"
}

shoot 02-home.png 'Home, two sections.
  Home = two sections, each x1 holding Work 30s + Rest 15s. Rounds 6. Both sections UNNAMED.
  Must be visible: "Save as preset" pill; "12 sets  .  8:45"; both section cards showing
  grip dots, -, x 1, +, THE LUGGAGE-TAG CIRCLE, 0:45, x; Work 30s and Rest 15s bands and
  "+ interval" under each; the group frame around both; "Rounds  -  6  +"; the "+" pill; "GO".
  The tag circle between "+" and "0:45" is the whole reason this frame is being retaken -- it
  landed 2026-08-08 and the old shot has bare space there.'

shoot 01-running.png 'Live timer, mid-Work.
  From that same home, tap GO, let the 5s Get ready pass, capture about 5s into the first Work.
  Must be visible: "Work" alone at the top with NOTHING under it (the section-name subtext only
  appears when a section is named -- leaving them unnamed is what keeps this frame matching the
  old one); the big count centred; "1 / 12" low on the screen; 12 pips under it; the green
  perimeter progress arms down both edges.'

shoot 03-presets-open.png 'Presets, Tabata expanded.
  Must be visible: Ladder "9 intervals  .  4:40", Pyramid "9 intervals  .  4:00",
  Tabata "15 intervals  .  3:50"; a round play button on the right of EVERY row and no small
  triangle or chevron anywhere; Tabata opened to ONE bordered bubble headed "x 8" holding just
  Work 20s and Rest 10s, then the Edit row. The old shot has 15 flat bands and a chevron; both
  are gone. The card is far shorter now, so scroll to keep Ladder and Pyramid in frame above it.'

shoot 04-settings.png 'Settings, top of the page.
  Must be visible: the back pill; the SETTINGS card reading exactly Mute / Volume /
  Run in background / Get ready 5s -- and NO "No back-to-back rests" row, which is what makes the
  old shot a rejection risk; then the THEME header with the Minimal switch off and the first rows
  of the palette grid with Default ringed.'

shoot 05-themes.png 'Settings, scrolled to the theme grid.
  Must be visible: all four swatch rows -- Default|Mono, Spidey|Miami, Trance|Laser, Vesper|Tron --
  with Default ringed and the six locked labels visibly dimmer than Default and Mono; THE LINE
  "Six themes come with the unlock - $2.99" directly under the grid; then the LANGUAGE card.
  That line is new and is why this frame is being retaken. If it shows no price, you are on a
  sideloaded build and Play has not handed the app a price -- see the note at the top.'

if ! ls raw/*.png >/dev/null 2>&1; then
  echo "nothing captured -- every frame was skipped. Not running the widen step."
  exit 0
fi

echo
python3 "$REPO/tools/widen_screenshots.py" raw/*.png -o .
echo
echo "done. Raw 1080x2520 frames are in ./raw/. The Play-ready 1260x2520 versions REPLACED the"
echo "committed PNGs in this directory -- git diff is how you compare them with what was there,"
echo "and git checkout is how you put a bad frame back. The widen step never touches the centre"
echo "1080 columns."
