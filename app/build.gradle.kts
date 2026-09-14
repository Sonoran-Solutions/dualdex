import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------------------
// Release signing (issue #38).
//
// Production signing secrets MUST live outside this repository. They are read
// from environment variables (DUALDEX_*) or, optionally, a gitignored local
// `signing.properties` file at the repository root. No secret value is ever
// embedded in Gradle source and no keystore is committed.
//
// The release build type is fail-closed: if the required credentials are
// missing, the release build fails with a clear, understandable error instead
// of silently producing an unsigned or debug-signed APK. Debug builds and
// canonical CI (`./ci.sh test|build|all`) never require these credentials.
// ---------------------------------------------------------------------------

val signingProperties = Properties().apply {
    val file = rootProject.file("signing.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

// Environment variables take precedence over signing.properties.
fun releaseSecret(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: signingProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val releaseSigningConfigured: Boolean =
    listOf(
        "DUALDEX_KEYSTORE_PATH",
        "DUALDEX_KEYSTORE_PASSWORD",
        "DUALDEX_KEY_ALIAS",
        "DUALDEX_KEY_PASSWORD",
    ).all { releaseSecret(it) != null }

val RELEASE_SIGNING_ERROR: String =
    "Production signing credentials are not configured.\n" +
        "Set DUALDEX_KEYSTORE_PATH, DUALDEX_KEYSTORE_PASSWORD,\n" +
        "DUALDEX_KEY_ALIAS, and DUALDEX_KEY_PASSWORD.\n" +
        "See RELEASE_ENGINEERING.md for the release-signing workflow."

android {
    namespace = "com.dualdex"
    compileSdk = 34
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.dualdex"
        minSdk = 28
        targetSdk = 34

        // Monotonic versionCode policy (see RELEASE_ENGINEERING.md):
        // every distributed Android build increments versionCode by 1 and it
        // must never decrease or be reused. versionName carries the human
        // semantic version.
        //
        // DualDex is still pre-beta: 0.9.0-dev is the current development
        // identity. The planned first public beta is 0.9.0-beta.1, set at the
        // actual release cut. versionCode stays 1 until a build is distributed.
        versionCode = 1
        versionName = "0.9.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                cFlags("-std=c11", "-Wall", "-Wextra")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    buildFeatures {
        // Generate BuildConfig so About/diagnostics can read VERSION_NAME,
        // VERSION_CODE, BUILD_TYPE, and APPLICATION_ID from Android-generated
        // build metadata instead of a duplicated hand-maintained string.
        buildConfig = true
    }

    signingConfigs {
        // The production release signing config. Only created when real
        // external credentials are available; otherwise release stays unsigned
        // and the validateReleaseSigning task fails the build first.
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseSecret("DUALDEX_KEYSTORE_PATH")!!)
                storePassword = releaseSecret("DUALDEX_KEYSTORE_PASSWORD")
                keyAlias = releaseSecret("DUALDEX_KEY_ALIAS")
                keyPassword = releaseSecret("DUALDEX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Never fall back to debug signing for a release build.
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
            isJniDebuggable = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Fail-closed gate for production releases. Wired to `preReleaseBuild`, the
// lifecycle task that every release-variant task depends on, so an intended
// public release fails fast (before any native build) with a clear message
// when credentials are absent instead of silently producing an unsigned or
// debug-signed APK.
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "Fails unless production release-signing credentials are configured."
    doLast {
        if (!releaseSigningConfigured) {
            throw GradleException(RELEASE_SIGNING_ERROR)
        }
        val keystorePath = releaseSecret("DUALDEX_KEYSTORE_PATH")!!
        if (!file(keystorePath).exists()) {
            throw GradleException("Configured DualDex production keystore does not exist: $keystorePath")
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.0")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
