#!/bin/sh
# One-step install: puts the launcher on the TV, grants its permissions and makes the Home button open it.
#
#   tools/install.sh <tv-ip> [path/to/app.apk]   install (APK: the given file, else your local release build,
#                                                else the latest GitHub release is downloaded)
#   tools/install.sh --undo <tv-ip>              give the Home button back to the stock Google TV home
#
# Needs adb on your computer and USB debugging switched on in the TV's developer options.
set -eu

PKG=com.khaled.tvlauncher
REPO=Khaled97Sh/islamic-android-tv-launcher
# Never disabled, whatever the TV says owns Home.
PROTECTED="android com.android.settings com.android.tv.settings com.android.systemui com.android.provision"
# The script records every home app it disables here (on the TV), so --undo restores exactly those.
DISABLED_LIST=/data/local/tmp/islamic-tv-launcher-disabled-homes
# Used by --undo only, for TVs that were set up by hand before this script existed.
KNOWN_STOCK_HOMES="com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith com.google.android.tvlauncher"

usage() { sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }
say() { printf '%s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

UNDO=0
[ "${1:-}" = "--undo" ] && { UNDO=1; shift; }
[ -n "${1:-}" ] || usage
case "$1" in *:*) TV="$1" ;; *) TV="$1:5555" ;; esac
APK="${2:-}"

command -v adb >/dev/null 2>&1 || die "adb not found. Install Android platform-tools first."

tv() { adb -s "$TV" shell "$@" 2>&1 | tr -d '\r'; }
installed() { [ -n "$(tv pm path "$1" | grep '^package:' || true)" ]; }
home_now() {
    tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
}
# Works on any TV: ask Android who owns Home, and while it is not us, switch that app off and ask again
# (on Google TV this walks launcherx -> setupwraith -> us). Stops at protected packages and after 6 rounds.
take_home() {
    round=0
    while [ "$round" -lt 6 ]; do
        tv cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null
        HOME_NOW="$(home_now)"
        owner="${HOME_NOW%%/*}"
        case "$HOME_NOW" in "$PKG"/*) return 0 ;; esac
        case "$owner" in "" | *" "* | *[!A-Za-z0-9_.]*) return 1 ;; *.*) ;; *) return 1 ;; esac
        for x in $PROTECTED; do [ "$owner" = "$x" ] && return 1; done
        case "$(tv pm disable-user --user 0 "$owner")" in
            *disabled-user*) say "switched off stock home $owner"; tv "echo $owner >> $DISABLED_LIST" >/dev/null ;;
            *) return 1 ;;
        esac
        round=$((round + 1))
    done
    return 1
}

adb connect "$TV" >/dev/null 2>&1 || true
STATE="$(adb -s "$TV" get-state 2>&1 || true)"
case "$STATE" in
    device) ;;
    *unauthorized*) die "the TV has not authorized this computer. Accept the 'Allow USB debugging' box on the TV, then run again." ;;
    *) die "cannot reach $TV. Check the TV IP, that both are on the same network, and that USB debugging is on." ;;
esac

if [ "$UNDO" = 1 ]; then
    RECORDED="$(tv cat "$DISABLED_LIST" | grep -E '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$' || true)"
    for p in $(printf '%s\n' $RECORDED $KNOWN_STOCK_HOMES | sort -u); do
        if installed "$p"; then tv pm enable "$p" >/dev/null; say "enabled $p"; fi
    done
    tv rm -f "$DISABLED_LIST" >/dev/null
    say "Home button now opens: $(home_now)"
    exit 0
fi

# 1. Find the APK.
if [ -z "$APK" ]; then
    LOCAL="$(dirname "$0")/../app/build/outputs/apk/release/app-release.apk"
    if [ -f "$LOCAL" ]; then
        APK="$LOCAL"
    else
        command -v curl >/dev/null 2>&1 || die "no APK given and curl not found. Pass the APK path as the second argument."
        say "No local build found, downloading the latest release (about 85 MB)..."
        URL="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" |
            grep -o '"browser_download_url": *"[^"]*\.apk"' | head -1 | sed 's/.*"\(https[^"]*\)"/\1/')"
        [ -n "$URL" ] || die "could not find an APK in the latest release. Download it from https://github.com/$REPO/releases and pass its path."
        APK="${TMPDIR:-/tmp}/islamic-tv-launcher-latest.apk"
        curl -fL --progress-bar -o "$APK" "$URL" || die "download failed."
    fi
fi
[ -f "$APK" ] || die "APK not found: $APK"

# 2. Install and grant the two permissions the adhan needs.
say "Installing $(basename "$APK")..."
OUT="$(adb -s "$TV" install -r "$APK" 2>&1)" || true
case "$OUT" in
    *Success*) ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*) die "a build signed with another key is already on the TV. Run 'adb -s $TV uninstall $PKG' (this clears your widget choices), then run again." ;;
    *) die "install failed: $OUT" ;;
esac
tv appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null
tv appops set "$PKG" SCHEDULE_EXACT_ALARM allow >/dev/null
# Lets the launcher switch its Home-catcher service on each time it is opened (fallback if Google TV comes back).
tv pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null || true
# TCL TVs block background services unless APP_AUTO_START is allowed; harmless elsewhere.
tv appops set "$PKG" APP_AUTO_START allow >/dev/null || true

# 3. Home button: the stock home outranks any app, so switch it off and take the Home role.
#    Done only after the install succeeded, so the TV is never left without a home screen.
HOME_NOW=""
take_home || true

# 4. Open it once (arms the first adhan alarm) and report who owns Home.
tv am start -n "$PKG/.MainActivity" >/dev/null
case "$HOME_NOW" in
    "$PKG"/*)
        say "Done. The Home button opens the launcher."
        say "Undo any time with: $0 --undo ${TV%:5555}"
        ;;
    android/*)
        say ""
        say "Installed. The TV is now asking which home app to use:"
        say "press Home on the remote, choose 'Islamic Tv Launcher' and pick 'Always'."
        ;;
    *)
        say ""
        say "Installed, but the Home button still opens: $HOME_NOW"
        say "That one is a system package or refused to be switched off, so it was left alone."
        say "Please report this line and your TV model at https://github.com/$REPO/issues"
        exit 2
        ;;
esac
