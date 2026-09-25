package co.abaye.mailtice.di

import co.abaye.mailtice.app.AppViewModel
import co.abaye.mailtice.data.MailRepository
import co.abaye.mailtice.data.SettingsStore
import co.abaye.mailtice.notify.MailNotifications
import co.abaye.mailtice.sync.SyncEngine
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

@DependencyGraph(AppScope::class)
interface AppGraph {
    val viewModelFactory: AppViewModel.Factory
    val store: SettingsStore
    val repository: MailRepository
    val sync: SyncEngine
    val notifications: MailNotifications
}

/**
 * One graph per process. On Android the UI, the background worker and the notification receiver
 * must share the same database driver and sync engine, so nobody may build a second one.
 */
object AppGraphHolder {
    val graph: AppGraph by lazy { createGraph<AppGraph>() }
}

fun createAppGraph(): AppGraph = AppGraphHolder.graph
