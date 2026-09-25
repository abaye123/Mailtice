package co.abaye.mailtice.data

import co.abaye.mailtice.domain.AppData
import co.abaye.mailtice.domain.UserSettings
import co.abaye.mailtice.platform.Platform
import co.abaye.mailtice.platform.joinPath
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

interface SettingsStore {
    fun load(): AppData
    fun save(data: AppData)
    fun clear()
}

class MemoryStore(initial: AppData = AppData()) : SettingsStore {
    private var data: AppData = initial
    override fun load(): AppData = data
    override fun save(data: AppData) {
        this.data = data
    }

    override fun clear() {
        data = AppData()
    }
}

@ContributesBinding(AppScope::class)
@Inject
class FileSettingsStore(private val dir: () -> String = { Platform.appDir() }) : SettingsStore {
    private val file get() = joinPath(dir(), "state.txt")

    override fun load(): AppData {
        val raw = Platform.readText(file)
        if (raw.isNullOrBlank()) return seedData()
        return runCatching { decodeSnapshot(raw) }.getOrElse { seedData() }
    }

    override fun save(data: AppData) {
        Platform.writeText(file, encodeSnapshot(data))
    }

    override fun clear() {
        Platform.delete(file)
    }
}

/** First launch: interface follows the OS language (English if unsupported), no accounts. */
fun seedData(): AppData = AppData(settings = UserSettings())
