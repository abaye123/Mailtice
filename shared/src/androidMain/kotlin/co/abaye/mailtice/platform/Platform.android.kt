package co.abaye.mailtice.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import java.io.File
import java.lang.ref.WeakReference
import java.util.Locale

@SuppressLint("StaticFieldLeak")
private var appContext: Context? = null
private var activityRef: WeakReference<ComponentActivity>? = null

fun bindAndroidContext(context: Context) {
    appContext = context.applicationContext
    if (context is ComponentActivity) activityRef = WeakReference(context)
}

private fun ctx(): Context = requireNotNull(appContext) { "bindAndroidContext() must run first (Application.onCreate)" }

/** The foreground activity, when there is one: Google sign-in needs it to show its consent UI. */
internal fun currentActivity(): ComponentActivity? = activityRef?.get()

/** The application context, for the pieces that need one before any composable exists. */
internal fun androidContext(): Context = ctx()

internal actual object Platform {
    actual val osLabel: String = "Android"

    actual val appVersion: String
        get() = runCatching {
            ctx().packageManager.getPackageInfo(ctx().packageName, 0).versionName.orEmpty()
        }.getOrDefault("")

    actual fun appDir(): String = ctx().filesDir.absolutePath

    actual fun readText(path: String): String? = runCatching {
        File(path).takeIf { it.isFile }?.readText()
    }.getOrNull()

    actual fun writeText(path: String, content: String) {
        runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    actual fun delete(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    actual fun mkdir(path: String) {
        runCatching { File(path).mkdirs() }
    }

    actual fun now(): Long = System.currentTimeMillis()

    actual fun applyLocale(tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
    }

    // System resources, not Locale.getDefault() - applyLocale() overwrites the latter, and the user
    // can change the device language without the process being killed.
    actual fun systemLanguage(): String = android.content.res.Resources.getSystem().configuration.locales[0].language

    actual val isDesktop: Boolean = false

    // Phase 2: an in-app WebView. Until then the reader shows the text version on Android.
    actual fun openHtml(html: String) = Unit

    actual fun setLaunchAtLogin(enabled: Boolean): Boolean = false

    /** Android 10+: the shared Downloads collection (no permission needed). Older: the app's own downloads dir. */
    actual fun defaultDownloadRoot(): String = "Downloads/Mailtice"

    actual fun saveDownload(folder: String, fileName: String, bytes: ByteArray, root: String): String? = runCatching {
        val name = safeFileName(fileName, "attachment")
        val sub = safeFileName(folder, "")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    listOf(android.os.Environment.DIRECTORY_DOWNLOADS, "Mailtice", sub).filter { it.isNotEmpty() }.joinToString("/"),
                )
            }
            val resolver = ctx().contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching null
            uri.toString()
        } else {
            val base = ctx().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: ctx().filesDir
            val dir = File(base, sub).apply { mkdirs() }
            File(dir, name).apply { writeBytes(bytes) }.absolutePath
        }
    }.getOrNull()

    actual val canPickFiles: Boolean = false

    actual val canPickFolder: Boolean = false

    actual suspend fun pickFolder(title: String): String? = null

    actual val canOpenFiles: Boolean = false

    actual fun openFile(path: String): Boolean = false

    actual fun readBytes(path: String): ByteArray? = runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull()

    actual fun writeBytes(path: String, bytes: ByteArray) {
        runCatching {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    // Picking needs an activity-result launcher wired through the Activity; not there yet.
    actual suspend fun pickFiles(title: String): List<PickedFile> = emptyList()

    /** The system Downloads app shows new files at the top; nothing to open per file here. */
    actual fun revealDownload(location: String) = Unit

    actual fun openUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx().startActivity(intent)
        }
    }
}
