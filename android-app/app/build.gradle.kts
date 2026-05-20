import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("release-signing.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun projectOrEnvironmentValue(propertyName: String, environmentName: String): String? =
    providers.environmentVariable(environmentName).orNull
        ?: releaseSigningProperties.getProperty(propertyName)
        ?: providers.gradleProperty(propertyName).orNull

val releaseStoreFilePath = projectOrEnvironmentValue(
    propertyName = "mobileSlicer.release.storeFile",
    environmentName = "MOBILE_SLICER_RELEASE_STORE_FILE"
)
val releaseStorePassword = projectOrEnvironmentValue(
    propertyName = "mobileSlicer.release.storePassword",
    environmentName = "MOBILE_SLICER_RELEASE_STORE_PASSWORD"
)
val releaseKeyAlias = projectOrEnvironmentValue(
    propertyName = "mobileSlicer.release.keyAlias",
    environmentName = "MOBILE_SLICER_RELEASE_KEY_ALIAS"
)
val releaseKeyPassword = projectOrEnvironmentValue(
    propertyName = "mobileSlicer.release.keyPassword",
    environmentName = "MOBILE_SLICER_RELEASE_KEY_PASSWORD"
)
val mobileSlicerVersionCode = projectOrEnvironmentValue(
    propertyName = "mobileSlicer.versionCode",
    environmentName = "MOBILE_SLICER_VERSION_CODE"
)?.toIntOrNull()?.takeIf { it > 0 } ?: 4
val mobileSlicerVersionName = projectOrEnvironmentValue(
    propertyName = "mobileSlicer.versionName",
    environmentName = "MOBILE_SLICER_VERSION_NAME"
)?.takeIf { it.isNotBlank() } ?: "0.1.1-beta"
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val includeX86_64Abi = providers.gradleProperty("mobileSlicer.includeX86_64Abi")
    .map(String::toBoolean)
    .getOrElse(false)
val orcaAndroidDepsRoot = providers.environmentVariable("ORCA_ANDROID_DEPS_ROOT")
    .orNull
    ?: "/tmp/orca-deps-install"
val orcaAndroidDepsSrcRoot = providers.environmentVariable("ORCA_ANDROID_DEPS_SRC_ROOT")
    .orNull
    ?: "/tmp/orca-deps-src"
val orcaAndroidOcctPrefix = providers.environmentVariable("ORCA_ANDROID_OCCT_PREFIX")
    .orNull
    ?: providers.gradleProperty("orcaAndroid.occtPrefix").orNull
    ?: ""
val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val thingiverseAppToken = providers.environmentVariable("THINGIVERSE_APP_TOKEN").orNull
    ?: localProperties.getProperty("thingiverse.appToken")
    ?: providers.gradleProperty("thingiverse.appToken").orNull
val thingiverseClientId = providers.environmentVariable("THINGIVERSE_CLIENT_ID").orNull
    ?: localProperties.getProperty("thingiverse.clientId")
    ?: providers.gradleProperty("thingiverse.clientId").orNull
val thingiverseAuthBackendUrl = providers.environmentVariable("THINGIVERSE_AUTH_BACKEND_URL").orNull
    ?: localProperties.getProperty("thingiverse.authBackendUrl")
    ?: providers.gradleProperty("thingiverse.authBackendUrl").orNull
val thingiverseRedirectScheme = "mobileslicer"
val thingiverseRedirectHost = "thingiverse-auth"
val thingiverseRedirectUri = "$thingiverseRedirectScheme://$thingiverseRedirectHost"

android {
    namespace = "com.mobileslicer"
    compileSdk = 35

    lint {
        // Android 16/API 36 targeting needs a separate compatibility pass before
        // beta builds opt in to the new runtime behavior.
        disable += "OldTargetApi"
    }

    defaultConfig {
        applicationId = "com.mobileslicer"
        minSdk = 26
        targetSdk = 35
        versionCode = mobileSlicerVersionCode
        versionName = mobileSlicerVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += buildList {
                add("arm64-v8a")
                if (includeX86_64Abi) {
                    add("x86_64")
                }
            }
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fvisibility=hidden", "-ffunction-sections", "-fdata-sections")
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DORCA_SHIPPING_REQUIRE_REAL_LIBSLIC3R=ON",
                    "-DORCA_SHIPPING_ALLOW_REDUCED_WRAPPER=OFF",
                    "-DORCA_ANDROID_DEPS_ROOT=$orcaAndroidDepsRoot",
                    "-DORCA_ANDROID_DEPS_SRC_ROOT=$orcaAndroidDepsSrcRoot"
                )
                if (orcaAndroidOcctPrefix.isNotBlank()) {
                    arguments += "-DORCA_ANDROID_OCCT_PREFIX=$orcaAndroidOcctPrefix"
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            isJniDebuggable = true
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "AUTOMATION_ENABLED", "true")
            buildConfigField("boolean", "SCANNER_ENTRY_ENABLED", "true")
            buildConfigField("String", "VIEWER_BUILD_STAMP", "\"viewer-build: debug\"")
            buildConfigField("String", "THINGIVERSE_APP_TOKEN", (thingiverseAppToken ?: "").asBuildConfigString())
            buildConfigField("String", "THINGIVERSE_CLIENT_ID", (thingiverseClientId ?: "").asBuildConfigString())
            buildConfigField("String", "THINGIVERSE_AUTH_BACKEND_URL", (thingiverseAuthBackendUrl ?: "").asBuildConfigString())
            buildConfigField("String", "THINGIVERSE_REDIRECT_URI", thingiverseRedirectUri.asBuildConfigString())
            manifestPlaceholders["thingiverseRedirectScheme"] = thingiverseRedirectScheme
            manifestPlaceholders["thingiverseRedirectHost"] = thingiverseRedirectHost
            externalNativeBuild {
                cmake {
                    arguments += "-DMOBILE_SLICER_BUILD_NATIVE_PAINT_PROBES=ON"
                }
            }
        }
        create("perfDebug") {
            initWith(getByName("debug"))
            isJniDebuggable = false
            isMinifyEnabled = false
            matchingFallbacks += listOf("debug")
            versionNameSuffix = "-perf"
            buildConfigField("boolean", "AUTOMATION_ENABLED", "true")
            buildConfigField("boolean", "SCANNER_ENTRY_ENABLED", "true")
            buildConfigField("String", "VIEWER_BUILD_STAMP", "\"viewer-build: perf\"")
            buildConfigField("String", "THINGIVERSE_APP_TOKEN", (thingiverseAppToken ?: "").asBuildConfigString())
            buildConfigField("String", "THINGIVERSE_CLIENT_ID", (thingiverseClientId ?: "").asBuildConfigString())
            buildConfigField("String", "THINGIVERSE_AUTH_BACKEND_URL", (thingiverseAuthBackendUrl ?: "").asBuildConfigString())
            buildConfigField("String", "THINGIVERSE_REDIRECT_URI", thingiverseRedirectUri.asBuildConfigString())
            manifestPlaceholders["thingiverseRedirectScheme"] = thingiverseRedirectScheme
            manifestPlaceholders["thingiverseRedirectHost"] = thingiverseRedirectHost
            externalNativeBuild {
                cmake {
                    arguments += "-DMOBILE_SLICER_BUILD_NATIVE_PAINT_PROBES=ON"
                }
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "AUTOMATION_ENABLED", "false")
            buildConfigField("boolean", "SCANNER_ENTRY_ENABLED", "false")
            buildConfigField("String", "VIEWER_BUILD_STAMP", "\"viewer-build: release\"")
            buildConfigField("String", "THINGIVERSE_APP_TOKEN", "\"\"")
            buildConfigField("String", "THINGIVERSE_CLIENT_ID", (thingiverseClientId ?: "").asBuildConfigString())
            buildConfigField("String", "THINGIVERSE_AUTH_BACKEND_URL", (thingiverseAuthBackendUrl ?: "").asBuildConfigString())
            buildConfigField("String", "THINGIVERSE_REDIRECT_URI", thingiverseRedirectUri.asBuildConfigString())
            manifestPlaceholders["thingiverseRedirectScheme"] = thingiverseRedirectScheme
            manifestPlaceholders["thingiverseRedirectHost"] = thingiverseRedirectHost
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir(layout.buildDirectory.dir("generated/source/orcaSettingMetadata"))
            assets.srcDir(layout.buildDirectory.dir("generated/orcaPrinterAssets"))
            assets.srcDir(layout.buildDirectory.dir("generated/orcaFilamentAssets"))
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = true
        disable += listOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            // commons-net's FTPS implementation contains trust-manager bytecode that lint
            // flags even when our Bambu LAN self-signed trust path is source-scoped.
            "TrustAllX509TrustManager"
        )
        htmlReport = true
        textReport = true
        xmlReport = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        // Scanner is disabled in release builds; do not ship scanner-only native
        // payloads that can fail Android 16 KB page-size compatibility checks.
        variant.packaging.jniLibs.excludes.addAll(
            listOf(
                "**/libarcore_sdk_c.so",
                "**/libarcore_sdk_jni.so",
                "**/libc++_shared.so",
                "**/libimage_processing_util_jni.so",
                "**/libmediapipe_tasks_jni.so",
                "**/libmediapipe_tasks_vision_jni.so",
                "**/libopencv_java4.so",
                "**/libscanner_apriltag.so",
                "**/libscanner_pose_optimizer.so",
                "**/libscanner_pose_solver.so",
                "**/libsurface_util_jni.so"
            )
        )
    }
}

tasks.matching { task ->
    task.name in setOf("assembleRelease", "bundleRelease", "packageRelease")
}.configureEach {
    doFirst {
        if (!hasReleaseSigningConfig) {
            throw GradleException(
                "Release signing is required. Provide MOBILE_SLICER_RELEASE_STORE_FILE, " +
                    "MOBILE_SLICER_RELEASE_STORE_PASSWORD, MOBILE_SLICER_RELEASE_KEY_ALIAS, " +
                    "and MOBILE_SLICER_RELEASE_KEY_PASSWORD, or define the matching " +
                    "mobileSlicer.release.* values in android-app/release-signing.properties."
            )
        }
    }
}

gradle.taskGraph.whenReady {
    val releaseArtifactRequested = allTasks.any { task ->
        task.name in setOf("assembleRelease", "bundleRelease", "packageRelease")
    }
    if (releaseArtifactRequested && !hasReleaseSigningConfig) {
        throw GradleException(
            "Release signing is required. Provide MOBILE_SLICER_RELEASE_STORE_FILE, " +
                "MOBILE_SLICER_RELEASE_STORE_PASSWORD, MOBILE_SLICER_RELEASE_KEY_ALIAS, " +
                "and MOBILE_SLICER_RELEASE_KEY_PASSWORD, or define the matching " +
                "mobileSlicer.release.* values in android-app/release-signing.properties."
        )
    }
}

val generateOrcaSettingMetadata by tasks.registering(Exec::class) {
    val repoRoot = rootProject.projectDir.parentFile
    val outputFile = layout.buildDirectory.file("generated/source/orcaSettingMetadata/com/mobileslicer/profiles/GeneratedOrcaSettingMetadata.kt")
    workingDir = repoRoot
    commandLine(
        "python3",
        "scripts/generate_orca_setting_metadata.py",
        "--repo-root",
        repoRoot.absolutePath,
        "--output",
        outputFile.get().asFile.absolutePath
    )
    inputs.files(
        repoRoot.resolve("vendor/orcaslicer/src/libslic3r/PrintConfig.cpp"),
        repoRoot.resolve("vendor/orcaslicer/src/slic3r/GUI/Tab.cpp"),
        repoRoot.resolve("scripts/generate_orca_setting_metadata.py")
    )
    outputs.file(outputFile)
}

val generateOrcaPrinterAssets by tasks.registering(Exec::class) {
    val repoRoot = rootProject.projectDir.parentFile
    val outputDir = layout.buildDirectory.dir("generated/orcaPrinterAssets/orca-printers")
    workingDir = repoRoot
    commandLine(
        "python3",
        "scripts/generate_orca_printer_assets.py",
        "--repo-root",
        repoRoot.absolutePath,
        "--output-dir",
        outputDir.get().asFile.absolutePath
    )
    inputs.files(
        project.fileTree(repoRoot.resolve("vendor/orcaslicer/resources/profiles")) {
            include("**/machine/**/*.json")
            include("**/process/**/*.json")
            include("**/*.stl")
            include("**/*.STL")
            include("**/*.svg")
            include("**/*.png")
            include("**/*_cover.png")
        },
        repoRoot.resolve("scripts/generate_orca_printer_assets.py")
    )
    outputs.dir(outputDir)
}

val generateOrcaFilamentAssets by tasks.registering(Exec::class) {
    val repoRoot = rootProject.projectDir.parentFile
    val outputDir = layout.buildDirectory.dir("generated/orcaFilamentAssets/orca-filaments")
    workingDir = repoRoot
    commandLine(
        "python3",
        "scripts/generate_orca_filament_assets.py",
        "--repo-root",
        repoRoot.absolutePath,
        "--output-dir",
        outputDir.get().asFile.absolutePath
    )
    inputs.files(
        project.fileTree(repoRoot.resolve("vendor/orcaslicer/resources/profiles")) {
            include("**/filament/*.json")
        },
        repoRoot.resolve("scripts/generate_orca_filament_assets.py")
    )
    outputs.dir(outputDir)
}

tasks.named("preBuild") {
    dependsOn(generateOrcaSettingMetadata)
    dependsOn(generateOrcaPrinterAssets)
    dependsOn(generateOrcaFilamentAssets)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.google.ar.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material3)
    implementation(libs.google.material)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.opencv)
    implementation(libs.paho.mqtt)
    implementation(libs.commons.net)

    testImplementation(libs.junit)
    testImplementation(libs.json)

    debugImplementation(libs.androidx.ui.tooling)
}
