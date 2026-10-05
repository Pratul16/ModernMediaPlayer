package com.pratul.mmplayer.ui.settings

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.security.LockDelay
import com.pratul.mmplayer.security.LockMethod
import com.pratul.mmplayer.ui.lock.PinSetupDialog
import com.pratul.mmplayer.ui.lock.confirmWithPhoneLock
import com.pratul.mmplayer.ui.lock.rememberOwnerCheck
import kotlinx.coroutines.launch

/**
 * App lock settings: on/off (off by default), unlock with the phone's lock or an app PIN, and how
 * long the app may stay in the background before locking. Turning it off or changing how it
 * unlocks needs the owner's confirmation first.
 */
@Composable
fun AppLockSection() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lock = (context.applicationContext as ModernMediaApp).container.appLock
    val config by lock.config.collectAsStateWithLifecycle()
    val ownerCheck = rememberOwnerCheck(lock)
    val scope = rememberCoroutineScope()
    // Non-null while choosing a PIN; the value says what to do once it's set.
    var pinFor by remember { mutableStateOf<PinPurpose?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun turnOn() {
        if (lock.isDeviceSecure() && activity != null) {
            activity.confirmWithPhoneLock("Turn on app lock", "Confirm it's you") { ok ->
                if (ok) {
                    lock.enable(LockMethod.SYSTEM)
                    toast("App lock is on")
                }
            }
        } else {
            // No screen lock on the phone: an app PIN is the only option.
            pinFor = PinPurpose.ENABLE
        }
    }

    fun useMethod(method: LockMethod) {
        if (method == config.method) return
        when (method) {
            LockMethod.PIN -> ownerCheck.confirm("Change how the app unlocks") { pinFor = PinPurpose.SWITCH }
            LockMethod.SYSTEM -> if (!lock.isDeviceSecure()) {
                toast("Set a screen lock in your phone's settings first")
            } else {
                activity?.confirmWithPhoneLock("Unlock with phone lock", "Confirm it's you") { ok -> if (ok) lock.setMethod(LockMethod.SYSTEM) }
            }
        }
    }

    Column {
        Text(
            "App lock",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
        )
        ListItem(
            headlineContent = { Text("Lock the app") },
            supportingContent = { Text(if (config.enabled) "Asks for ${if (config.method == LockMethod.PIN) "your app PIN" else "fingerprint, face or phone PIN"} when opened" else "Off · anyone with your phone can open the app") },
            leadingContent = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            trailingContent = {
                Switch(checked = config.enabled, onCheckedChange = { on ->
                    if (on) turnOn() else ownerCheck.confirm("Turn off app lock") { lock.disable(); toast("App lock is off") }
                })
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable {
                if (!config.enabled) turnOn() else ownerCheck.confirm("Turn off app lock") { lock.disable(); toast("App lock is off") }
            },
        )
        if (config.enabled) {
            Text("Unlock with", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
            FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LockMethod.entries.forEach { method ->
                    FilterChip(
                        selected = config.method == method,
                        onClick = { useMethod(method) },
                        label = { Text(if (method == LockMethod.SYSTEM) "Fingerprint / phone lock" else "App PIN") },
                    )
                }
            }
            if (config.method == LockMethod.PIN) {
                ListItem(
                    headlineContent = { Text("Change app PIN") },
                    leadingContent = { Icon(Icons.Rounded.Password, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { ownerCheck.confirm("Change app PIN") { pinFor = PinPurpose.CHANGE } },
                )
            }
            ListItem(
                headlineContent = { Text("Lock after leaving the app") },
                supportingContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LockDelay.entries.forEach { delay ->
                            FilterChip(selected = config.delay == delay, onClick = { lock.setDelay(delay) }, label = { Text(delay.label) })
                        }
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    pinFor?.let { purpose ->
        PinSetupDialog(
            onDismiss = { pinFor = null },
            onPinChosen = { pin ->
                pinFor = null
                scope.launch {
                    lock.setPin(pin)
                    when (purpose) {
                        PinPurpose.ENABLE -> {
                            lock.enable(LockMethod.PIN)
                            toast("App lock is on")
                        }
                        PinPurpose.SWITCH -> {
                            lock.setMethod(LockMethod.PIN)
                            toast("The app now unlocks with your PIN")
                        }
                        PinPurpose.CHANGE -> toast("PIN changed")
                    }
                }
            },
        )
    }
}

private enum class PinPurpose { ENABLE, SWITCH, CHANGE }
