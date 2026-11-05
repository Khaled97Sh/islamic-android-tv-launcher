#!/bin/sh
# Backup only: if the stock Google TV home ever gets enabled again, run this from the Mac to make our launcher Home again.
# Usage: tools/reclaim-home.sh [tv-ip]   (default 172.20.10.5; adb must be authorized from this Mac)
TV="${1:-172.20.10.5}:5555"
adb connect "$TV" >/dev/null
adb -s "$TV" shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb -s "$TV" shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb -s "$TV" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
