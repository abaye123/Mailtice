package co.abaye.mailtice.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Finds the profiles of the Chromium browsers installed for this user (Chrome, Edge, Brave) and opens
 * a URL in one of them. Profiles are listed in each browser's "Local State" file; nothing is read
 * beyond the profile names and the signed-in address shown in the browser's own profile menu.
 */
internal object BrowserProfiles {
    private class Browser(val label: String, val dataDirs: List<File>, val launch: (profileDir: String, url: String) -> List<String>?)

    private val os = System.getProperty("os.name").orEmpty().lowercase()
    private val home = System.getProperty("user.home").orEmpty()

    private val browsers: List<Browser> by lazy {
        when {
            os.contains("win") -> {
                val local = System.getenv("LOCALAPPDATA").orEmpty()
                val programs = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), local)
                fun exe(vararg relative: String) = programs.flatMap { base -> relative.map { File(base, it) } }.firstOrNull { it.isFile }
                listOf(
                    windowsBrowser("Chrome", File(local, "Google/Chrome/User Data"), exe("Google/Chrome/Application/chrome.exe")),
                    windowsBrowser("Edge", File(local, "Microsoft/Edge/User Data"), exe("Microsoft/Edge/Application/msedge.exe")),
                    windowsBrowser("Brave", File(local, "BraveSoftware/Brave-Browser/User Data"), exe("BraveSoftware/Brave-Browser/Application/brave.exe")),
                )
            }
            os.contains("mac") -> {
                val support = File(home, "Library/Application Support")
                listOf(
                    macBrowser("Chrome", File(support, "Google/Chrome"), "Google Chrome"),
                    macBrowser("Edge", File(support, "Microsoft Edge"), "Microsoft Edge"),
                    macBrowser("Brave", File(support, "BraveSoftware/Brave-Browser"), "Brave Browser"),
                )
            }
            else -> {
                val config = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
                listOf(
                    linuxBrowser("Chrome", File(config, "google-chrome"), "google-chrome"),
                    linuxBrowser("Edge", File(config, "microsoft-edge"), "microsoft-edge"),
                    linuxBrowser("Brave", File(config, "BraveSoftware/Brave-Browser"), "brave-browser"),
                )
            }
        }
    }

    private fun windowsBrowser(label: String, data: File, exe: File?) = Browser(label, listOf(data)) { dir, url ->
        exe?.let { listOf(it.absolutePath, "--profile-directory=$dir", url) }
    }

    private fun macBrowser(label: String, data: File, app: String) = Browser(label, listOf(data)) { dir, url ->
        listOf("open", "-na", app, "--args", "--profile-directory=$dir", url)
    }

    private fun linuxBrowser(label: String, data: File, command: String) = Browser(label, listOf(data)) { dir, url ->
        onPath(command)?.let { listOf(it, "--profile-directory=$dir", url) }
    }

    private fun onPath(command: String): String? =
        System.getenv("PATH").orEmpty().split(File.pathSeparator).map { File(it, command) }.firstOrNull { it.canExecute() }?.absolutePath

    /** Every profile of every installed browser, in the order the browsers list them. */
    fun list(): List<BrowserProfile> = browsers.flatMap { browser ->
        // A profile only counts if the browser can actually be started.
        if (browser.launch("Default", "about:blank") == null) return@flatMap emptyList()
        browser.dataDirs.flatMap { dir -> read(File(dir, "Local State"), browser.label) }
    }

    private fun read(localState: File, label: String): List<BrowserProfile> = runCatching {
        if (!localState.isFile) return emptyList()
        val root = Json.parseToJsonElement(localState.readText()).jsonObject
        val cache = (root["profile"] as? JsonObject)?.get("info_cache") as? JsonObject ?: return emptyList()
        cache.entries.map { (dir, value) ->
            val info = value.jsonObject
            fun text(key: String) = runCatching { info[key]?.jsonPrimitive?.content }.getOrNull().orEmpty()
            BrowserProfile(browser = label, directory = dir, name = text("name").ifBlank { dir }, email = text("user_name"))
        }.sortedBy { if (it.directory == "Default") "" else it.directory }
    }.getOrDefault(emptyList())

    /** Opens [url] in [profile]; false when that browser cannot be started (the caller falls back). */
    fun open(profile: BrowserProfile, url: String): Boolean = runCatching {
        val browser = browsers.firstOrNull { it.label == profile.browser } ?: return false
        val command = browser.launch(profile.directory, url) ?: return false
        ProcessBuilder(command).start()
        true
    }.getOrDefault(false)
}
