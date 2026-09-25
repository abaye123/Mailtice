package co.abaye.mailtice.auth

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

/** Hand-off points between the suspend sign-in code and the activities the OS calls back into. */
internal object AuthBridge {
    @Volatile
    var pendingResolution: CompletableDeferred<Intent?>? = null

    @Volatile
    var pendingRedirect: CompletableDeferred<Uri>? = null
}

/**
 * Invisible activity that runs a Play-services consent screen (an IntentSender) and hands the
 * result back. Needed because the sign-in code is a suspend function, not an activity.
 */
class AuthResolutionActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        AuthBridge.pendingResolution?.complete(if (result.resultCode == Activity.RESULT_OK) result.data else null)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        @Suppress("DEPRECATION")
        val sender = intent.getParcelableExtra<IntentSender>(EXTRA_SENDER)
        if (sender == null) {
            AuthBridge.pendingResolution?.complete(null)
            finish()
            return
        }
        launcher.launch(IntentSenderRequest.Builder(sender).build())
    }

    companion object {
        const val EXTRA_SENDER = "sender"
    }
}

/** Receives co.abaye.mailtice://oauth?code=... from the browser (Custom Tabs flow). */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { AuthBridge.pendingRedirect?.complete(it) }
        // Back to the app, on top of the browser tab.
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        finish()
    }
}
