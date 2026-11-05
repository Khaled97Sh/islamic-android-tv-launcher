# Islamic Android TV Launcher

A calm, Arabic-first home screen for Android TV / Google TV. It replaces the stock launcher with a "liquid glass" dashboard
of prayer times, the next adhan, Hijri date, a daily ayah and dhikr, a scrolling azkar bar and rotating photos of historic
mosques. Built with Kotlin and Jetpack Compose, designed for the remote (D-pad) and for RTL.

![Home with all widgets](docs/screenshots/home-widgets.jpg)

| | |
|---|---|
| ![Dome of the Rock](docs/screenshots/home-dome.jpg) | ![Mosque ceiling](docs/screenshots/home-ceiling.jpg) |

## Features

- **Prayer times that work offline**: a full-year timetable is bundled, and refreshed every 10 days from the mosque's
  Mawaqit page (validated, with typo repair). [adhan-java](https://github.com/batoulapps/adhan-java) is the last-resort fallback.
- **Adhan**: exact alarm for the next prayer, pauses other apps, plays the adhan and shows an on-screen reminder.
  Skipped when the screen is off. OK / Back dismisses it.
- **Widgets**: next prayer + countdown, all five prayers, Gregorian + Hijri date, names of Allah, sunnah of the day,
  duas. Optional ayah and dhikr cards. Smart chips for Friday (Surat al-Kahf) and the last third of the night.
- **Azkar news bar** along the bottom (morning / evening schedule) and a rotating ayah.
- **Photos** of Sunni / Andalusian / Umayyad mosques, no people, changing every 30 s, with blurred "glass" panels.
- **App rail** on the left: YouTube, Pro TV, Settings, your own picks.
- **Trusted clock**: TVs without a battery-backed clock show a wrong date after an offline boot; the launcher keeps correct time anyway.
- Fonts: IBM Plex Sans Arabic and Amiri Quran.

## Build

Requirements: Android Studio (or JDK 17 + Android SDK), `adb`.

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Judge performance on the **release** build only; debug is much slower.

## Install on a TV

1. TV: Settings > System > About > tap *Build* 7 times, then Developer options > USB debugging ON. Note the TV IP.
2. From a computer on the same Wi-Fi:

```sh
adb connect TV_IP:5555
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell appops set com.khaled.tvlauncher SYSTEM_ALERT_WINDOW allow
adb shell appops set com.khaled.tvlauncher SCHEDULE_EXACT_ALARM allow
```

Without those two permissions the adhan reminder card and exact alarms won't work. Open the app once so the alarm is armed.

### Making the Home button open it (optional)

The stock Google TV home outranks any app, so it has to be disabled (this is device-specific; it was tested on a TCL
"Smart TV Pro", Android 12):

```sh
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.khaled.tvlauncher
```

Undo: `adb shell pm enable <package>` for both packages, or `adb uninstall com.khaled.tvlauncher`.
Use at your own risk on other TVs. See [`tools/reclaim-home.sh`](tools/reclaim-home.sh).

## Project layout

- `app/src/main/java/com/khaled/tvlauncher/`: UI (`HomeScreen`, `WidgetCards`, `Glass`), azan (`AzanScheduler`,
  `AzanReceiver`, `AzanService`), prayer data (`MosqueCalendar`, `MawaqitSync`, `PrayerTimeProvider`), `TrustedClock`.
- `app/src/main/assets/`: `ayat.json`, `azkar.json`, `duas.json`, `sunnah.json`, `asma.json`, background photos.
- [`CLAUDE.md`](CLAUDE.md): detailed technical notes on design decisions.

## Contributing

Pull requests are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md). `main` is protected: every change goes through a PR
that the maintainer reviews before merge. Corrections to Arabic text (ayat, azkar, duas, hadith) are especially valued,
please cite the source.

## Credits

- Photos: Wikimedia Commons, individual authors and licences in [`app/src/main/assets/bg/CREDITS.txt`](app/src/main/assets/bg/CREDITS.txt) (CC BY / CC BY-SA / CC0).
- Quran text: [Tanzil Project](https://tanzil.net). Azkar: Hisn al-Muslim dataset, [rn0x/Adhkar-json](https://github.com/rn0x/Adhkar-json).
- Fonts: IBM Plex Sans Arabic and Amiri Quran, SIL OFL 1.1 (`app/src/main/assets/licenses/`).
- Adhan audio and timetable: see the notes in CLAUDE.md. If you own any asset and want it removed or credited, open an issue.

## License

Source code: [MIT](LICENSE). Third-party assets keep their own licences (see Credits).
