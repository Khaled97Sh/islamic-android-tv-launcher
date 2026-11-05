# Islamic Android TV Launcher

A calm, Arabic-first home screen for Android TV / Google TV. It replaces the stock launcher with a "liquid glass" dashboard
of prayer times, the next adhan, Hijri date, a daily ayah and dhikr, a scrolling azkar bar and rotating photos of historic
mosques. Built with Kotlin and Jetpack Compose, designed for the remote (D-pad) and for RTL.

![Home with all widgets](docs/screenshots/home-widgets.jpg)

| | |
|---|---|
| ![Dome of the Rock](docs/screenshots/home-dome.jpg) | ![Mosque ceiling](docs/screenshots/home-ceiling.jpg) |

## Customize with the + button

Press **+** on the app rail to open the selector: toggle each widget, the ayah / dhikr cards and extra apps with the remote.

![Widget selector](docs/screenshots/widget-selector.jpg)

## Features

- **Prayer times that work offline**: a full-year timetable is bundled, and refreshed every 10 days from the mosque's
  Mawaqit page (validated, with typo repair). [adhan-java](https://github.com/batoulapps/adhan-java) is the last-resort fallback.
- **Adhan**: exact alarm for the next prayer, pauses other apps, plays the adhan and shows an on-screen reminder.
  Skipped when the screen is off. OK / Back dismisses it.
- **Widgets**: next prayer + countdown, all five prayers, Gregorian + Hijri date, names of Allah, sunnah and dua (a new one
  every 6 hours, from an offline library large enough to go weeks without repeating). Optional ayah and dhikr cards. Smart chips for Friday (Surat al-Kahf) and the last third of the night.
- **Azkar news bar** along the bottom (morning / evening schedule) and a rotating ayah.
- **Photos** of historic mosques, no people, changing every 30 s, with blurred "glass" panels.
- **App rail** on the left: YouTube, Settings and your own picks. The **+** tile opens a list where you switch widgets on or off and add apps.
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

### Install without adb (no developer mode, no USB debugging)

You only need developer options and USB debugging for the `adb` route above. To sideload on the TV itself:

1. Copy `app-release.apk` to a USB stick, or download it on the TV (browser, or an app such as *Send Files to TV*).
2. Open it with a file manager app on the TV (for example *X-plore* or *File Commander*).
3. When Android asks, allow installing from unknown sources for that file manager
   (Settings > Apps > Special app access > Install unknown apps). This is not developer mode.
4. Tap *Install*, then open the app once so the first azan alarm is armed.
5. Grant the permissions by hand (Settings > Apps > Special app access):
   - **Display over other apps** for the azan reminder card and for coming to the front when the TV wakes up.
   - **Alarms & reminders** for exact azan times. Without it the azan still fires, possibly a little late.

Without adb the launcher is opened from the app list. Making the **Home button** open it needs the stock home disabled,
which only `adb` can do (see below).

Release builds are signed with the debug key, so a later build from another machine will not install over it:
uninstall first (this clears your widget choices).

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

## Location

Prayer times, weather and schedules currently target **Damascus**. Other cities need a few constants changed, see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#location). Making this configurable is a great first contribution.

## Project layout

- `app/src/main/java/com/khaled/tvlauncher/`: one package per feature: `time`, `prayer`, `azan`, `content`, `widgets`, `weather`,
  `youtube`, `apps`, and `ui/{home,widgets,background,theme}`. See the layout in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
- `gradle/libs.versions.toml`: all dependency and plugin versions (version catalog).
- `app/src/main/assets/`: `ayat.json`, `azkar.json`, `duas.json`, `sunnah.json`, `asma.json`, background photos.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): how the pieces fit together.
- `tools/build_content.py`: regenerates the ayat / azkar JSON from open datasets.

## Contributing

Pull requests are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md). `main` is protected: every change goes through a PR
that the maintainer reviews before merge. Corrections to Arabic text (ayat, azkar, duas, hadith) are especially valued,
please cite the source.

## Credits

- Photos: Wikimedia Commons, individual authors and licences in [`app/src/main/assets/bg/CREDITS.txt`](app/src/main/assets/bg/CREDITS.txt) (CC BY / CC BY-SA / CC0).
- Quran text: [Tanzil Project](https://tanzil.net). Azkar: Hisn al-Muslim dataset, [rn0x/Adhkar-json](https://github.com/rn0x/Adhkar-json).
- Fonts: IBM Plex Sans Arabic and Amiri Quran, SIL OFL 1.1 (`app/src/main/assets/licenses/`).
- Prayer timetable: the Umayyad Mosque (Damascus) published timetable on [Mawaqit](https://mawaqit.net).
- If you own any asset and want it removed or credited, please open an issue.

## License

Source code: [MIT](LICENSE). Third-party assets keep their own licences (see Credits).
