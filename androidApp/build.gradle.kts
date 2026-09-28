import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
}

/** Tag-driven in CI (`v1.2.3` → `1.2.3`), plain default for a local build. */
val releaseVersion =
    System.getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() && it.first().isDigit() }
        ?: "1.0.0"

/** Play and the package manager only ever compare this, so it just has to keep going up. */
val releaseVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

/**
 * Signing material never lives in the repository. Locally it comes from `signing.local.properties`
 * (gitignored) so a release build needs no shell setup; in CI the same four names arrive as
 * environment variables from repository secrets. The file wins when both are present.
 */
val signingProperties: Map<String, String> =
    rootProject.file("signing.local.properties")
        .takeIf { it.isFile }
        ?.readLines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        ?.associate { line ->
            val separator = line.indexOf('=')
            line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }
        .orEmpty()

fun signingValue(name: String): String? =
    signingProperties[name]?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val keystorePath: String? = signingValue("ANDROID_KEYSTORE_FILE")

android {
    namespace = "co.abaye.mailtice"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37

        // Locked forever once the app is published: Play identifies an app by this string, and
        // changing it later means a new listing with no users, ratings or install base.
        applicationId = "co.abaye.mailtice"
        versionCode = releaseVersionCode
        versionName = releaseVersion
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingValue("ANDROID_KEY_ALIAS")
                keyPassword = signingValue("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Without a keystore the build still succeeds and leaves the APK unsigned, so a
            // contributor can produce one locally without holding the release key.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Jakarta Mail, Angus Mail and Angus Activation each ship the same Eclipse NOTICE and LICENSE
    // files; the app needs none of them at run time and the merge refuses duplicates.
    packaging {
        resources {
            excludes += listOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/NOTICE", "META-INF/LICENSE")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.work.runtime)
    // Contributes res/raw/netfree_*.crt and a manifest entry pointing at
    // @xml/network_security_config. It carries no code, which is why it sits here rather than in
    // :shared: what it changes is how this application trusts TLS, not what any Kotlin calls.
    // Traffic on a NetFree line is re-signed by NetFree's own CA, and an Android app trusts only the
    // system store by default, so without this every TLS connection (Gmail API, IMAP, OAuth) fails to
    // handshake on a filtered device - including one where the certificate is installed at user
    // level, which API 24 and above ignore.
    implementation(libs.netfreetools.certificates)
}
