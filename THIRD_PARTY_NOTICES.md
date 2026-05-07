# Third-Party Notices

MobileSlicer includes MobileSlicer application code and native slicer components
derived from OrcaSlicer, libSlic3r, and related open-source projects.

MobileSlicer is not affiliated with, endorsed by, or sponsored by the
OrcaSlicer project.

## Project License

MobileSlicer release source is distributed under the GNU Affero General Public
License version 3. See [LICENSE](LICENSE).

Public MobileSlicer releases include access to the corresponding source required
for AGPL-covered components, including MobileSlicer changes and build materials
needed for those components.

## Native Slicer Components

| Component | License | Source path |
| --- | --- | --- |
| OrcaSlicer / libSlic3r-derived code | GNU AGPL v3 | `vendor/orcaslicer/`, `engine-wrapper/orca-android-libslic3r/` |
| cereal 1.3.0 | BSD 3-Clause | `engine-wrapper/orca-android-libslic3r/third_party/cereal-1.3.0/` |
| RapidJSON headers bundled with cereal | MIT/BSD-style upstream license | `engine-wrapper/orca-android-libslic3r/third_party/cereal-1.3.0/assets/external/rapidjson/` |
| RapidXML headers bundled with cereal | Boost Software License 1.0 or MIT | `engine-wrapper/orca-android-libslic3r/third_party/cereal-1.3.0/assets/external/rapidxml/` |
| libnoise-compatible Android header shim | MobileSlicer compatibility source | `third_party/libnoise_compat/` |

Additional upstream packages are vendored under `vendor/orcaslicer/deps_src/`.
Their license files and notices remain with their source trees.

## Android Application Dependencies

Android application dependencies are declared in
`android-app/gradle/libs.versions.toml` and `android-app/app/build.gradle.kts`.

| Dependency | Version | License |
| --- | --- | --- |
| Android Gradle Plugin | 8.5.2 | Apache License 2.0 |
| Kotlin | 2.0.20 | Apache License 2.0 |
| AndroidX Core KTX | 1.13.1 | Apache License 2.0 |
| AndroidX Lifecycle | 2.8.4 | Apache License 2.0 |
| AndroidX Activity Compose | 1.9.1 | Apache License 2.0 |
| Jetpack Compose BOM | 2024.09.00 | Apache License 2.0 |
| Material Components / Material 3 | 1.12.0 / Compose BOM managed | Apache License 2.0 |
| org.json | 20240303 | JSON License |
| JUnit | 4.13.2 | Eclipse Public License 1.0 |
| Eclipse Paho MQTT | 1.2.5 | Eclipse Public License 2.0 |
| Apache Commons Net | 3.11.1 | Apache License 2.0 |

## Release Checklist

Before publishing a release, verify this notice against the exact release source
tree and dependency lock state. Generated build directories, local APKs, and
developer-only artifacts are not the source of truth for license inventory.
