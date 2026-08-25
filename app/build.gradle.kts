import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    // Renders Compose to PNG on the JVM, with no device and no emulator.
    // This project has neither, and shipping UI changes unseen does not work.
    alias(libs.plugins.paparazzi)
}

/**
 * Which commit a build came from, stamped into the APK.
 *
 * Every build used to call itself versionCode 1, versionName "1.0.0". The APK
 * is handed over as a file at a fixed URL, so with an identical version there
 * was no way — from the download, from the installer, or from Settings — to
 * tell a new build from the one already on the phone. "Is this the same APK?"
 * was a reasonable question with no available answer.
 *
 * Commit count is monotonic, which is what versionCode has to be for an
 * install to be treated as an update. The short SHA is what actually
 * identifies the source, and it is the half a person can check against the
 * repository.
 *
 * Falls back cleanly: a build from a source archive has no git metadata, and
 * that must not fail the build.
 */
fun git(vararg args: String): String? = runCatching {
    val result = providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
        // Without this, a non-zero exit is raised by Gradle when the provider
        // is realised — which, with the configuration cache on, happens
        // outside this runCatching and fails the build outright. A source
        // archive has no .git, git exits 128, and the fallback below never got
        // the chance to run despite the comment promising it would.
        isIgnoreExitValue = true
    }
    if (result.result.get().exitValue != 0) {
        null
    } else {
        result.standardOutput.asText.get().trim().ifBlank { null }
    }
}.getOrNull()

val gitCount = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val gitSha = git("rev-parse", "--short=7", "HEAD") ?: "nogit"

/**
 * A build secret, from an untracked properties file or from the environment.
 *
 * The file is for a person publishing from their own machine; the environment
 * is for a build server. Neither is in the repository and the build works
 * without both.
 */
val uploadProperties = Properties().apply {
    val file = rootProject.file("keystore/upload.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String? =
    uploadProperties.getProperty(name) ?: System.getenv(name)

android {
    namespace = "gr.agiosnektarios.village"
    /**
     * Deliberately one behind [targetSdk], which is not the usual arrangement.
     *
     * From 31 August 2026 Play refuses new apps and updates that target below
     * API 36, so targetSdk has to be 36 to publish at all. compileSdk 36 wants
     * a layoutlib that matches, and Paparazzi 1.3.5 ships one for API 34:
     * raising it makes every one of the 118 snapshot tests die inside
     * `Renderer.configureBuildProperties` before rendering a single pixel.
     *
     * Compiling against 35 while targeting 36 costs nothing here, because
     * nothing in this app calls an API that only exists in 36. What it buys is
     * the snapshot suite, which is the only thing that notices when a layout
     * silently changes. Raise this to 36 together with Paparazzi 2.0.0-alpha05
     * or later — and expect to re-record every golden when you do, because the
     * newer layoutlib renders text differently.
     */
    compileSdk = 35

    defaultConfig {
        applicationId = "gr.agiosnektarios.village"
        minSdk = 26
        // Android 16. Every behaviour change it turns on was already handled:
        // the activity calls enableEdgeToEdge, the manifest opts into the
        // predictive back callback, and no screen locks its orientation.
        targetSdk = 36
        versionCode = gitCount
        // The store shows this. The commit is still stamped into the app —
        // see BuildConfig.GIT_SHA and Settings > About — but a version name
        // with a hex string in it reads as a mistake on a store page.
        versionName = "1.0.$gitCount"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += setOf("en", "el")
    }

    signingConfigs {
        /**
         * The key that signs what goes to Google Play.
         *
         * Never in this repository, and the build must not require it: anyone
         * cloning this can build, test and sideload without holding the key
         * that publishes. So it is read from `keystore/upload.properties`, or
         * from the environment for a build server, and when neither is present
         * the release build simply comes out unsigned — which is a perfectly
         * good state for everything except uploading.
         *
         * Play re-signs with its own key when the bundle arrives (Play App
         * Signing), so this is the *upload* key: it proves the bundle came
         * from you. Losing it is recoverable by asking Google to reset it;
         * losing the app signing key would not be, which is why letting Play
         * hold that one is worth doing.
         */
        val uploadStore = secret("UPLOAD_STORE_FILE")
        if (uploadStore != null) {
            create("upload") {
                storeFile = rootProject.file(uploadStore)
                storePassword = secret("UPLOAD_STORE_PASSWORD")
                keyAlias = secret("UPLOAD_KEY_ALIAS")
                keyPassword = secret("UPLOAD_KEY_PASSWORD")
            }
        }

        /**
         * A debug key committed to the repository on purpose.
         *
         * The default is a per-machine key in ~/.android, so an APK built on one
         * machine refuses to install over one built on another — signature
         * mismatch — and its SHA-1 differs too, which breaks the Google sign-in
         * fingerprint registered in Firebase. Pinning it here means any build,
         * from anyone, installs as an update and matches one registered
         * fingerprint.
         *
         * This is a *debug* key: the password is public, it signs nothing that
         * reaches a store, and it is not a security boundary. Release builds
         * must use a real keystore that is never committed.
         */
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            // MapLibre ships a native renderer per ABI, which quadruples the
            // debug APK. Debug builds are sideloaded onto real phones, and
            // every Android phone in use is ARM — so the emulator-only x86
            // slices are dropped. Release keeps all four for the store.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
        /**
         * What gets handed to a tester over a chat window.
         *
         * Identical to debug in identity — same `.debug` applicationId, same
         * committed signing key, so it installs over a debug build and matches
         * the fingerprint registered in Firebase — but shrunk like a release.
         * An unminified debug APK is ~46 MB, most of it dex from Compose icons
         * and Firebase; R8 removes what the app never calls and takes it under
         * a size that can actually be sent to a phone.
         */
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // Set explicitly rather than trusted to initWith, which does not
            // carry them over: without these R8 never ran, and a build whose
            // whole purpose is being small was shipping its full dex.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-preview.pro",
            )
            // arm64 only: every Android phone sold since ~2017 is arm64, and
            // carrying a second copy of MapLibre's renderer costs 9 MB on a
            // build whose whole purpose is being small enough to send.
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }

        release {
            // Signed only if the upload key is present; see signingConfigs.
            // An unsigned release bundle still builds, which is what keeps a
            // fresh clone able to run `bundleRelease` without a secret.
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    implementation(libs.maplibre)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.accompanist.permissions)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

/**
 * A fresh JVM for every test class.
 *
 * The Paparazzi goldens are this app's only gate on layout and colour, and
 * they were not deterministic: recording `UnlookedTest` on its own and then
 * verifying the whole suite failed `chats_greek_max`, with "Μαρία Κ." drawn
 * regular in one and bold in the other on an unchanged tree. All four weights
 * come from a single variable font resource (see `ui/theme/Type.kt`), and
 * layoutlib's typeface cache is per-JVM, so which weight a given resource
 * resolves to depends on what else has already rendered in that worker.
 *
 * Forking per class costs a few seconds and buys a gate that means something.
 * Without it, four rounds of judging hierarchy from these images were judging
 * a weight the app might not draw.
 */
tasks.withType<Test>().configureEach {
    forkEvery = 1
}
