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
)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
val mobileSlicerVersionName = projectOrEnvironmentValue(
    propertyName = "mobileSlicer.versionName",
    environmentName = "MOBILE_SLICER_VERSION_NAME"
)?.takeIf { it.isNotBlank() } ?: "0.1.0"
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val includeX86_64Abi = providers.gradleProperty("mobileSlicer.includeX86_64Abi")
    .map(String::toBoolean)
    .getOrElse(false)

android {
    namespace = "com.mobileslicer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mobileslicer"
        minSdk = 26
        targetSdk = 34
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
                    "-DORCA_SHIPPING_ALLOW_REDUCED_WRAPPER=OFF"
                )
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
            versionNameSuffix = "-workspace-surface-v1"
            buildConfigField("boolean", "AUTOMATION_ENABLED", "true")
            buildConfigField("String", "VIEWER_BUILD_STAMP", "\"viewer-build: surface-thread-workspace-v1\"")
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
            buildConfigField("String", "VIEWER_BUILD_STAMP", "\"viewer-build: perf\"")
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
            buildConfigField("String", "VIEWER_BUILD_STAMP", "\"viewer-build: release\"")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material3)
    implementation(libs.google.material)
    implementation(libs.paho.mqtt)
    implementation(libs.commons.net)

    testImplementation(libs.junit)
    testImplementation(libs.json)

    debugImplementation(libs.androidx.ui.tooling)
}
