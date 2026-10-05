package com.pratul.mmplayer.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pratul.mmplayer.utils.MediaAccess
import com.pratul.mmplayer.utils.MediaPermissions

@Stable
class MediaAccessState internal constructor(
    private val context: Context,
    initialAccess: MediaAccess,
) {
    var access by mutableStateOf(initialAccess)
        private set

    internal var deniedAfterRequest by mutableStateOf(false)

    /** True once the system stops showing the dialog; the user must then use app settings. */
    val permanentlyDenied: Boolean
        get() = deniedAfterRequest && access == MediaAccess.NONE

    internal var launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>? = null

    fun request() {
        launcher?.launch(MediaPermissions.requestPermissions)
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    internal fun refresh() {
        access = MediaPermissions.currentAccess(context)
    }
}

/** Tracks media read access, re-checking on every resume (the user may change it in Settings). */
@Composable
fun rememberMediaAccessState(onAccessChanged: (MediaAccess) -> Unit = {}): MediaAccessState {
    val context = LocalContext.current
    val state = remember { MediaAccessState(context, MediaPermissions.currentAccess(context)) }

    state.launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.refresh()
        val activity = context.findActivity()
        // After a denial, no rationale for any permission means the system will not ask again.
        state.deniedAfterRequest = state.access == MediaAccess.NONE && activity != null &&
            MediaPermissions.requestPermissions.none { activity.shouldShowRequestPermissionRationale(it) }
        onAccessChanged(state.access)
    }

    LifecycleResumeEffect(state) {
        val before = state.access
        state.refresh()
        if (state.access != before) onAccessChanged(state.access)
        onPauseOrDispose { }
    }
    return state
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
