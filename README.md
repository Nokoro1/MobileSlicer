# MobileSlicer

MobileSlicer is a local, touch-first 3D slicer for Android.

It opens STL and 3MF files, prepares plates, slices on-device, previews G-code,
and exports or sends print files from a phone or tablet. The app is built for
Android with Kotlin, Jetpack Compose, and a native slicer engine derived from
OrcaSlicer and related open-source slicer work.

MobileSlicer is not affiliated with, endorsed by, or sponsored by the OrcaSlicer
project.

## Status

MobileSlicer is preparing for a public Google Play beta. APKs are not
distributed from the website or this repository.

Current focus:

- STL and 3MF import from Android storage and share sheets
- Touch-first model workspace
- Printer, filament, and process profile selection
- Local slicing
- G-code preview
- Print-file export, sharing, and compatible network printer sending
- Saved plates and calibration flows

## Screenshots

<p>
  <img src="docs/screenshots/home.jpg" alt="MobileSlicer home screen" width="180">
  <img src="docs/screenshots/workspace.jpg" alt="MobileSlicer workspace" width="180">
  <img src="docs/screenshots/gcode-preview.jpg" alt="MobileSlicer G-code preview" width="180">
  <img src="docs/screenshots/profiles.jpg" alt="MobileSlicer profile selection" width="180">
  <img src="docs/screenshots/calibrations.jpg" alt="MobileSlicer printer calibrations" width="180">
</p>

## Build

Requirements:

- Android Studio or Android SDK command-line tools
- JDK 17
- Android SDK and NDK installed
- CMake available through the Android SDK

Set `ANDROID_HOME` or create `android-app/local.properties` with your local SDK
path:

```properties
sdk.dir=/path/to/Android/Sdk
```

Build the debug app:

```bash
cd android-app
./gradlew :app:assembleDebug
```

Release builds require local signing configuration that is intentionally not
tracked in git.

## Website And Support

- Website: <https://mobileslicer.com>
- Support email: <mobileslicerapp@gmail.com>
- Discord: <https://discord.gg/ckAAYAhRxE>

## License And Source

MobileSlicer release source is distributed under the GNU Affero General Public
License version 3. See [LICENSE](LICENSE), [SOURCE_NOTICE.md](SOURCE_NOTICE.md),
and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Public source is hosted at <https://github.com/Nokoro1/MobileSlicer>.
