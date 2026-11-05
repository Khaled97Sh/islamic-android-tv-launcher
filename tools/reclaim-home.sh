#!/bin/sh
# Backup only: if the stock Google TV home ever gets enabled again, run this from your computer to make our launcher Home again.
# Usage: tools/reclaim-home.sh [tv-ip]   ( adb must be authorized from your computer)
[ -z "$1" ] && { echo "usage: $0 <tv-ip>"; exit 1; }
TV="$1:5555"
adb connect "$TV" >/dev/null
adb -s "$TV" shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb -s "$TV" shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
