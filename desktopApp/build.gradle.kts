import dev.nucleusframework.desktop.application.dsl.CompressionLevel
import dev.nucleusframework.desktop.application.dsl.NativeImageMarch
import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.ReleaseChannel
import dev.nucleusframework.desktop.application.dsl.ReleaseType
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.nucleus)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_25) }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.decorated.window.tao)
    implementation(libs.nucleus.decorated.window.material3)
    implementation(libs.nucleus.autolaunch)
    // Unread count on the taskbar / launcher icon (TaskbarBadge.kt).
    implementation(libs.nucleus.launcher.windows)
    implementation(libs.nucleus.launcher.linux)
    implementation(libs.composenativetray)
}

val releaseVersion =
    System.getenv("RELEASE_VERSION")
        ?.removePrefix("v")
        ?.takeIf { it.isNotBlank() && it.first().isDigit() }
        ?: "1.0.0"

val nativePackageVersion = releaseVersion.substringBefore("-")

nucleus.application {
    mainClass = "MainKt"
    // Nucleus `run` forks `javaHome`, not the Java plugin toolchain. Point it at JDK 25
    // so a JBR 21 Gradle daemon (typical from IDEA) does not launch class-file 69 bytecode.
    javaHome =
        javaToolchains
            .launcherFor(java.toolchain)
            .get()
            .metadata.installationPath.asFile.absolutePath

    graalvm {
        isEnabled = true
        imageName = "Mailtice"
        optimization = NativeImageOptimization.SIZE
        // Without this, native-image bakes in whatever instruction set the build machine happens
        // to have, and the binary dies with SIGILL on any older CPU that downloads it. The release
        // runners are newer than plenty of machines the app is meant to run on, so the ceiling has
        // to be set here rather than left to the builder.
        march = NativeImageMarch.COMPATIBILITY
    }

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Zip, TargetFormat.Nsis, TargetFormat.Deb)
        packageName = "Mailtice"
        packageVersion = releaseVersion
        vendor = "abaye"
        cleanupNativeLibs = true
        compressionLevel = CompressionLevel.Ultra
        // electron-builder refuses to build a .deb without it: "Please specify project homepage".
        homepage = "https://github.com/abaye123/Mailtice"

        // Where the app looks for its own updates. GitHubProvider in DesktopUpdate.kt reads the
        // same coordinates; both have to name the repository the release workflow publishes to.
        publish {
            github {
                enabled = true
                owner = "abaye123"
                repo = "Mailtice"
                channel = ReleaseChannel.Latest
                releaseType = ReleaseType.Release
            }
        }

        linux {
            iconFile.set(project.file("appIcons/LinuxIcon.png"))
            debPackageVersion = releaseVersion
            // Likewise mandatory for .deb; the address only has to be well-formed.
            debMaintainer = "abaye <abaye123@users.noreply.github.com>"

            // A .deb can only install itself without a password prompt if it is signed, so this is
            // what makes the Linux self-update silent rather than a sudo dialog. Inert until the
            // LINUX_GPG_* secrets exist: the CI step below writes the key settings into
            // gradle.properties only when the secret is present, and this block reads them from
            // there. Windows and macOS need none of it.
            signing {
                enabled.set(true)
                silentUpdate.set(true)
            }
        }
        windows {
            iconFile.set(project.file("appIcons/WindowsIcon.ico"))
            packageVersion = nativePackageVersion
            upgradeUuid = "e9d51331-8ef0-4840-9e13-73219ee9df55"
        }
        macOS {
            iconFile.set(project.file("appIcons/MacOsIcon.icns"))
            packageVersion = nativePackageVersion
            bundleID = "co.abaye.mailtice"
        }
    }
}

// Demo mode: fake accounts and mail, no network, and a separate data directory that is wiped on
// every launch (see DemoMode.kt). Hot reload runs start in the full demo by default; the regular
// `run` task uses real data. Either can be switched with -Pdemo=full|empty|off (no dot in the
// name: PowerShell splits unquoted arguments at a dot).
val demoScenario = providers.gradleProperty("demo")
tasks.withType<JavaExec>().configureEach {
    val fallback = if (name.startsWith("hotRun")) "full" else "off"
    systemProperty("mailtice.demo", demoScenario.getOrElse(fallback))
}
