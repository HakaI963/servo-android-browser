# Android browser in this fork

This repository is a fork of the real [servo/servo](https://github.com/servo/servo) engine.
The Android browser is the upstream application, not a reimplementation:

- Engine + JNI: `ports/servoshell/egl/android/mod.rs`
- Kotlin wrapper: `support/android/apk/servoview/`
- Browser app: `support/android/apk/servoapp/`

## Changes applied in this fork

A small design pass was applied to the upstream app, using the `ui-ux-pro-max` and `unslop`
design directions while keeping the real app structure and engine intact:

- `servoapp/src/main/res/values/styles.xml` — graphite dark system chrome (`#0B0E11`)
- `servoapp/src/main/java/org/servo/servoshell/MainActivity.kt` — Material 3 dark color scheme
  with a restrained jade accent (`#5FC79A`); the rendered page remains the only bright element
- `servoapp/src/main/res/values/strings.xml` — plain, sentence-case UI copy

## Containers and keep-alive

The browser can host many independent browsing containers. Each container has:

- its own Servo engine instance and thread-bound slot in the native library
- its own config directory under `filesDir/servo/<container-id>` (site data, cookies, prefs)
- its own browsing history (`filesDir/containers/<container-id>/history.json`)
- its own settings and its own navigation state, so accounts never mix

Create, switch and delete containers from the grid button in the top bar. Up to 256 containers can
be alive at once; deleted containers release their slot for reuse.

Sessions are owned by `ContainerRegistry` at process level, not by an Activity or Composable.
Closing the screen, rotating, or backgrounding the app detaches the surface but keeps the page
loaded. Returning rebinds the callbacks and reattaches the surface without reloading. Sessions are
destroyed only when the user taps **Terminate all sessions** in Settings (or Android force-stops
the app).

Native changes that make this possible:

- `ports/servoshell/egl/android/mod.rs`: `CALLBACK_OBJECTS` and the per-container config directory
- `ports/servoshell/prefs.rs`: slot-indexed `DEFAULT_CONFIG_DIRS`
- `JNIServo.init` accepts a container id and slot

## Build

The upstream workflow builds the engine and app for all three ABIs:

```bash
# In GitHub Actions: run the "Android" workflow (workflow_dispatch), profile checked-release.
# Locally: cd support/android/apk && ./gradlew :servoapp:assembleArm64Release
```

Upstream build instructions: https://book.servo.org/building/android.html

## Release

`.github/workflows/release-apk.yml` attaches the APK produced by the upstream "Android" run to a
GitHub Release, using only GitHub Actions (no local downloads).

See [LICENSE](LICENSE) for the Mozilla Public License 2.0 covering Servo.
