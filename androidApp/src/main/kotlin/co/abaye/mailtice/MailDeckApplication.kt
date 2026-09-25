package co.abaye.mailtice

import android.app.Application
import co.abaye.mailtice.platform.bindAndroidContext
import co.abaye.mailtice.sync.BackgroundSync

/** Binds the context before anything else runs - the worker and the receiver can start without UI. */
class MailticeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        bindAndroidContext(this)
        BackgroundSync.schedule(this)
    }
}
