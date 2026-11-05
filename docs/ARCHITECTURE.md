# Architecture

Single Gradle module `:app` (Kotlin, Jetpack Compose, minSdk 26, targetSdk 34). Package `com.khaled.tvlauncher`.

## UI
- `HomeScreen.kt`: root layout (RTL). App rail on the physical left, chips + weather + clock on top, widget cards on the right,
  ayah / dhikr cards and the azkar bar at the bottom. The "+" tile opens `AppPicker`: a list to toggle widgets, the bottom
  cards and extra apps in the rail.
- `WidgetCards.kt`, `BottomCards.kt`, `Widgets.kt`: widget definitions and cards (all widget cards share one size, content is fit-to-box).
- `Glass.kt`: `Modifier.glass()` draws only the slice of a pre-blurred copy of the background behind each panel (cheap),
  plus a smoke tint for contrast, a sheen and a light edge.
- `Background.kt`: rotates the photos in `assets/bg/` every 30 s. Photos are 3840x2160 files decoded at 1920x1080. Do not add 8K.
- `Type.kt`, `Text.kt`: fonts (IBM Plex Sans Arabic for UI, Amiri Quran for verses) and per-font size factors.

## Prayer times and adhan
- `MosqueCalendar.kt`: bundled full-year timetable (by month-day, six columns including sunrise).
- `MawaqitSync.kt`: re-downloads the mosque's Mawaqit page every 10 days and validates it with `TimetableCheck`. A cell is
  wrong if it is outside a plausible range or more than 8 min from the median of its +-15 neighbours. Wrong cells are
  interpolated, and the whole table is rejected (old data kept) if more than 2% of cells are wrong, a day is missing or the
  day-to-day step is over 4 min.
- `PrayerTimeProvider.kt`: serves the times, with adhan-java as last resort.
- `AzanScheduler` arms one exact alarm for the next prayer, `AzanReceiver` receives it and `AzanService` plays the adhan
  (taking audio focus so other apps pause) and shows the overlay reminder. Skipped when the screen is off.
  Audio: `res/raw/first.mp3` and `second.mp3`, alternated per prayer.
- `TrustedClock.kt`: TVs without a battery-backed clock boot offline with the firmware build date. The clock only trusts
  the system time if it is newer than the last good time, install time and `Build.TIME`; otherwise it continues from the
  last good time. All alarms use `ELAPSED_REALTIME_WAKEUP` and re-arm on `TIME_SET`.

## Location
The project currently targets **Damascus** (coordinates in `PrayerTimeProvider.kt` / `WeatherRepository.kt`, timezone
`Asia/Damascus` in `TickerSchedule.kt`, Umayyad Mosque timetable in `MosqueCalendar.kt`). Making the city configurable is a
welcome contribution.

## Content
`app/src/main/assets/{ayat,azkar,duas,sunnah,asma}.json`. Source credits are in `assets/bg/CREDITS.txt`.

## Testing
```sh
./gradlew :app:testDebugUnitTest
```
Debug builds expose `AzanReceiver` for testing the adhan:
`adb shell am broadcast -n com.khaled.tvlauncher/.AzanReceiver --es prayer الظهر`.
Always judge scrolling and animation smoothness on the **release** build (R8 + baseline profile); debug is much slower.
