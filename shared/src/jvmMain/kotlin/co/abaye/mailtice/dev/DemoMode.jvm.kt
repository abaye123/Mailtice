package co.abaye.mailtice.dev

import co.abaye.mailtice.platform.Platform
import java.io.File

/** JVM system property (`-Dmailtice.demo=full`) that switches demo mode on. */
const val DEMO_PROPERTY = "mailtice.demo"

/** Environment variable alternative to [DEMO_PROPERTY], handy for IDE run configurations. */
const val DEMO_ENV = "MAILTICE_DEMO"

/**
 * Reads the demo switch and, when it is on, wipes the demo data directory so every launch starts
 * from the same fixture. Must run first thing in `main`, before anything touches [Platform.appDir].
 */
fun enableDemoModeFromEnvironment() {
    val scenario = DemoScenario.parse(System.getProperty(DEMO_PROPERTY) ?: System.getenv(DEMO_ENV))
    DemoMode.start(scenario)
    if (!DemoMode.enabled) return
    runCatching { File(Platform.appDir()).listFiles()?.forEach { it.deleteRecursively() } }
}
