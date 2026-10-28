# Installing Ightanam TV — instructions for AI agents

You are helping a person install this app on their Android TV / Google TV over ADB.
Follow these steps in order. Talk to the person in their language (usually Arabic).

## Rules

- Ask before every step marked **(ask first)**. Explain what it changes and how to undo it.
- Never factory-reset the TV, uninstall system apps, or change security settings.
- Never ask the person for passwords or account details. They don't need any.
- Run one command per step and check its output before moving on.
- Run `adb` with `-s <TV_IP>:5555` if more than one device is connected.

## 0. What you need from the person

1. The TV's IP address: **Settings → Network & Internet → (their network)**.
2. Developer options on: **Settings → System → About → Android TV OS build**, pressed 7 times.
3. **USB debugging** on in **Settings → System → Developer options**. Some TVs also show **Network debugging**; turn that on too if present.

## 1. Get the APK

Download `ightanam-tv.apk` from the latest release:

```bash
gh release download --repo aboumar5/ightanam-tv --pattern ightanam-tv.apk
```

Without `gh`, download it from https://github.com/aboumar5/ightanam-tv/releases/latest.
To build it yourself instead, see "البناء" in README.md (Windows; needs the Android SDK and JDK 17).

## 2. Connect

```bash
adb connect <TV_IP>:5555
```

The TV shows "Allow USB debugging?". Ask the person to choose **Always allow** and press OK.
Then `adb devices` should list `<TV_IP>:5555  device`. If it says `unauthorized`, the prompt wasn't accepted.

## 3. Install and allow the adhan card

```bash
adb install -r ightanam-tv.apk
```

```bash
adb shell appops set com.aboumarai.ightanam SYSTEM_ALERT_WINDOW allow
```

## 4. Screensaver (ask first)

Replaces the TV's screensaver with the prayer clock.

```bash
adb shell settings put secure screensaver_components com.aboumarai.ightanam/.IghtanamDream
```

```bash
adb shell settings put secure screensaver_enabled 1
```

Undo: pick another screensaver in **Settings → System → Ambient mode / Screen saver**.

## 5. First run

```bash
adb shell am start -n com.aboumarai.ightanam/.MainActivity
```

Ask the person to press **ابدأ** and pick their city and muezzin with the remote. The app stores these for the adhan alarm.

Check the adhan card (it plays the adhan; Back on the remote stops it):

```bash
adb shell am broadcast -n com.aboumarai.ightanam/.AdhanReceiver --ez test true --es key asr
```

## 6. Home screen (optional, ask first)

Replaces the Google TV home screen with Ightanam's. Google TV only lets another home screen run
if its own is disabled. That stops Google TV's recommendations; voice search and casting keep working.

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

```bash
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

```bash
adb shell cmd package set-home-activity com.aboumarai.ightanam/.HomeActivity
```

Then press Home on the remote (or `adb shell input keyevent KEYCODE_HOME`) and confirm the new screen shows.
If a "repair" screen or nothing appears, undo straight away (below).

Undo (also required before any factory reset):

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
```

```bash
adb shell pm enable com.google.android.tungsten.setupwraith
```

Tell the person: the grid button (next to the gear) lists all apps; long-press OK on an app there adds it to or removes it from the bottom row.

## 7. Speed tweaks (optional, ask first)

Halve the menu animation time (snappier navigation):

```bash
adb shell settings put global window_animation_scale 0.5
```

```bash
adb shell settings put global transition_animation_scale 0.5
```

```bash
adb shell settings put global animator_duration_scale 0.5
```

Undo: the same commands with `1`.

Turn off the click sound when moving between icons:

```bash
adb shell settings put system sound_effects_enabled 0
```

Hide preinstalled apps the person never uses: list them with `adb shell pm list packages`, show the
person the list, and disable only the ones they name, one at a time:

```bash
adb shell pm disable-user --user 0 <package>
```

Keep a list of what you disabled and give it to them. Undo one with `adb shell pm enable <package>`.
Never disable anything needed for HDMI inputs (e.g. `com.mediatek.wwtv.tvcenter`), the keyboard in use,
Google Play, Settings or Google services unless the person insists and understands the effect.

## 8. Finish

Ask the person to turn **USB debugging** off again in Developer options, and to revoke USB debugging
authorizations. Summarize what you changed and how to undo each change.
