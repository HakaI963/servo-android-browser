# Android browser in this fork

This repository is a fork of the real [servo/servo](https://github.com/servo/servo) engine.
The Android browser is the upstream application, not a reimplementation:

- Engine + JNI: `ports/servoshell/egl/android/mod.rs`
- Kotlin wrapper: `support/android/apk/servoview/`
- Browser app: `support/android/apk/servoapp/`

## App shell

Launching the app opens **ContainerPickerActivity**, a container manager showing every browser
container with an option to create more. Tapping a container opens it in `MainActivity`; the grid
button in the browser returns to the manager. Containers are the app's top-level concept — the
browser is opened inside a chosen container, not on launch.

New containers open on `https://www.google.com/` and the omnibox searches with Google.

## Containers and keep-alive

Each container has:

- its own Servo engine instance and thread-bound slot in the native library
- its own config directory under `filesDir/servo/<container-id>` (site data, cookies, prefs)
- its own browsing history (`filesDir/containers/<container-id>/history.json`)
- its own settings and navigation state, so accounts never mix

Up to 256 containers can be alive at once; deleted containers release their slot for reuse.
Only one container paints at a time; switching pauses the previous engine before the new one
attaches to the surface.

Sessions are owned by `ContainerRegistry` at process level, not by an Activity or Composable.
Closing the screen, rotating, or backgrounding keeps the page loaded. Returning rebinds callbacks
and reattaches the surface without reloading. Sessions are destroyed only via **Terminate all
sessions** in Settings (or an Android force-stop).

## Native changes

- `ports/servoshell/lib.rs` — global init (`init_crypto`, `init_tracing`) is idempotent, so
  creating a second container no longer crashes the process.
- `ports/servoshell/egl/android/mod.rs` — `CALLBACK_OBJECTS` and callbacks are slot-indexed;
  callbacks carry their slot because `wakeup()` runs on arbitrary Servo threads.
- `ports/servoshell/prefs.rs` — slot-indexed `DEFAULT_CONFIG_DIRS`.
- `JNIServo.init` accepts a container id and slot.

## Build

The upstream workflow builds the engine and app for all three ABIs:

```bash
# GitHub Actions: run the "Android" workflow (workflow_dispatch), profile checked-release.
# Locally: cd support/android/apk && ./gradlew :servoapp:assembleArm64Release
```

Upstream build instructions: https://book.servo.org/building/android.html

## Release

`.github/workflows/release-apk.yml` attaches the APK produced by the upstream "Android" run to a
GitHub Release, using only GitHub Actions (no local downloads).

See [LICENSE](LICENSE) for the Mozilla Public License 2.0 covering Servo.
