# tcl-tv-launcher

Minimal Arabic/Islamic Android TV launcher for a TCL Google TV (Kotlin + Compose, single module `:app`).

- UI v2 (accepted 2026-11-05): fonts IBM Plex Sans Arabic + Amiri Quran (`Type.kt`, `UI_V2` switch; `Text.kt` shadows BasicText and
  applies per-font size factors so the old v1 sp values look the same); azkar = full-width news bar at the bottom edge (32 dp, ivory,
  40 dp/s, re-picked per pass); widget cards all 220 x 137 dp with fit-to-box content (`WidgetCards.kt`); ayah and zikr cards are
  optional rows in the "+" list (`BottomCards.kt`, on by default); weather/chips/clock sit 24 dp from the top. Old UI: `tv-backup/ui-v1-src/`.
- Home layout (24dp screen margins, 12dp top/bottom): app rail on the physical left (fixed 64dp-wide glass card, 5 slots
  then scrolls; YouTube first, Pro TV second, user picks, then Settings + stock-home icon; the last "+" tile opens a picker
  of installed apps, picks stored in SharedPreferences "rail"; YouTube/Pro TV/Settings/home can't be removed; no name labels),
  weather top-left, clock + Gregorian and Hijri date top-right, and three stacked cards at the bottom: ayah (with ref), zikr
  (both sized to their text, no animation, change every 60 s) and the full-width sliding azkar strip (only moving item).
  Background photo changes every 30 s. The rail is laid out LTR inside the RTL root, so its margin is `padding(start=...)`.
- Backgrounds: Sunni/Andalusian/Umayyad Mosque photos only, no people, no Shia/Iran/Iraq sites (user's rule).
- Sliding azkar strip: 06:00-13:00 morning, 18:00-23:00 evening, all on `Asia/Damascus` time (`TickerSchedule`).
- Azan: `AzanScheduler` arms one exact alarm for the next prayer -> `AzanReceiver` -> `AzanService` (audio focus pauses
  other apps, plays adhan, overlay reminder). Prayer times offline via adhan-java (`PrayerTimeProvider`).
- Content in `app/src/main/assets/{ayat,azkar}.json` (Arabic text should be reviewed by the user).
- Prayer times: the Umayyad Mosque's own Mawaqit timetable (mosque #33637, fixed by month-day, 6 columns incl. sunrise).
  `MawaqitSync` re-downloads the page every 10 days at run time (first try 20 s after start, retry in 10 min on failure)
  and `TimetableCheck` validates it first: a cell is wrong if outside the plausible range or >8 min from the median of
  its +-15 neighbour days; wrong cells are rebuilt by interpolation, and the whole table is rejected (old data kept)
  if >2% of cells are wrong, a day is missing, or the day-to-day step is >4 min. The mosque's own published table has
  typos (Fajr 8 May 07:07, Asr 23-31 May 14:15/14:16, Maghrib 25 May 15:37); `MosqueCalendar.kt` is the already
  repaired offline copy and is checked in tests against `app/src/test/resources/mawaqit_umayyad_page.html`.
  adhan-java is only a last resort. Syria is permanent +03 (no summer/winter time).
- Adhan audio: `res/raw/first.mp3` and `second.mp3`, alternated per prayer (last index in SharedPreferences "azan").
- Azan is skipped when the screen is off (`PowerManager.isInteractive`). OK / Back on the remote dismisses the
  reminder overlay and stops the adhan.
- `MainActivity` sets FLAG_KEEP_SCREEN_ON so the ambient screensaver never starts while the launcher is showing.
- Test the azan (debug build): `adb shell am broadcast -n com.khaled.tvlauncher/.AzanReceiver --es prayer الظهر`.
  Screen-off test: `input keyevent KEYCODE_SLEEP`, broadcast, then `KEYCODE_WAKEUP`.
- Home on the TCL "Smart TV Pro" (Android 12): our app is the HOME role holder AND both
  `com.google.android.apps.tv.launcherx` (stock launcher) and `com.google.android.tungsten.setupwraith` (setup wizard,
  has a higher-priority HOME fallback) are `pm disable-user`-ed. Disabling only the stock launcher sends Home to
  setupwraith's RecoveryActivity. Shell cannot disable that single activity, only the whole package.
  Restore: `adb shell pm enable com.google.android.apps.tv.launcherx` and `... com.google.android.tungsten.setupwraith`.
  Role: `adb shell cmd role add-role-holder --user 0 android.app.role.HOME <pkg>`.
- `LauncherApp` registers ACTION_SCREEN_ON and brings `MainActivity` to the front, so waking from standby shows the
  launcher instead of the last app (allowed from background thanks to the overlay permission).

## Look
- Liquid glass = `Modifier.glass()` (Glass.kt): draws only the slice of a pre-blurred copy of the photo behind each panel
  (cheap), a dark smoke tint for text contrast, sheen and a light edge. Photos: `assets/bg/*.jpg` (3840x2160, decoded at
  1920x1080, rotate every 30 s; Wikimedia Commons CC BY-SA, credits in `assets/bg/CREDITS.txt`). 8K was rejected:
  the panel is 4K at most and an 8K bitmap is ~130 MB.
- ALWAYS test lag on the RELEASE build (R8 + baseline profile); the debug build is much slower. `dumpsys gfxinfo` on
  this TV returns meaningless numbers (4950ms for every percentile).
- Debug builds only: `files/debug_quote.txt` = `ayah|ref|zikr` pins the card text (to test long text).

## Taking Home back (decided 2026-11-04)
The stock home outranks any app, an app can't disable it, and this TV can't authorize an on-device ADB key (SystemUI has no
UsbDebuggingActivity), device-owner is blocked by the signed-in Google account, and TCL's boot manager refuses to bind our
accessibility service. So: keep the stock home DISABLED permanently; use the "original home" tile only to jump into it.
If it is enabled again, run `tools/reclaim-home.sh` from the Mac. Never run blind key presses on the TV (they opened its
app-info page and enabled it before).

## Stock Google TV home
Removed on purpose: an in-app "hold OK 6s to open the real home" tile needed the TV's one-time "Allow USB debugging"
approval for the app's own ADB key, which this TCL never displays. Instead Settings is in the app list; re-enable the
stock home via Settings > Apps (not verified on this TV) or from the Mac:
`adb shell pm enable com.google.android.apps.tv.launcherx` (and `... com.google.android.tungsten.setupwraith`).
Background-started receivers get the app process killed by TCL, so test with the launcher in front.

## Build / install
```
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease          # app/build/outputs/apk/release/app-release.apk
adb connect <tv-ip>:5555 && adb install -r app/build/outputs/apk/release/app-release.apk
```
Debug build exposes `AzanReceiver` for testing:
`adb shell am broadcast -n com.khaled.tvlauncher/.AzanReceiver --es prayer الظهر`

## Rollback
`adb uninstall com.khaled.tvlauncher`; if the stock launcher was disabled, `adb shell pm enable <its package>`.

## Clock without internet (2026-11-05)
The TV has no battery-backed RTC: a cold boot offline shows the firmware build date (2025-04-21) until the network sets it.
`TrustedClock` (used everywhere instead of System.currentTimeMillis) only believes the system clock if it is >= the newest of
last-good-time / install time / Build.TIME; otherwise it continues from the last good time with elapsedRealtime (same boot) or
sits at the last good time (after a reboot, TV off = can only be behind). Azan alarms use ELAPSED_REALTIME_WAKEUP; TIME_SET re-arms.
Prayer data: full-year table (bundled + downloaded every 10 days), so offline works for months. Installed: release/tvlauncher-v2-offline-clock.apk.
