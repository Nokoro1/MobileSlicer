# Third-Party Notices

This inventory is a release checklist starter, not a substitute for legal review.

## Vendored Or Wrapped Components

- OrcaSlicer / libSlic3r-derived code: `vendor/orcaslicer/`, `engine-wrapper/orca-android-libslic3r/`
- cereal 1.3.0: `engine-wrapper/orca-android-libslic3r/third_party/cereal-1.3.0/`
- rapidjson and rapidxml copies under cereal external headers
- Android Gradle Plugin, Kotlin, AndroidX, Jetpack Compose, Material components: declared under `android-app/gradle/libs.versions.toml` and `android-app/app/build.gradle.kts`

## Release Rule

Before publishing a release build, verify each bundled native/static dependency license and update this file with:

- dependency name and version or source commit
- license
- source URL or vendored path
- required notice text, if any

Generated dependency build directories and local APK artifacts must not be used as the license inventory source of truth.
