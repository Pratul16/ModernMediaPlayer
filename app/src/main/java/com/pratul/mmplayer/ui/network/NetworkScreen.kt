package com.pratul.mmplayer.ui.network

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.media.network.NetworkBrowser
import com.pratul.mmplayer.media.network.NetworkBrowser.Companion.normalizedAddress
import com.pratul.mmplayer.media.network.NetworkItem
import com.pratul.mmplayer.media.network.SavedServer
import com.pratul.mmplayer.player.PlayerActivity
import com.pratul.mmplayer.ui.components.SectionHeader
import com.pratul.mmplayer.ui.theme.GlowButton
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.auroraTopBarColors
import com.pratul.mmplayer.ui.theme.glass

class NetworkViewModel(app: Application) : AndroidViewModel(app) {
    val browser = NetworkBrowser(app)
    val store = (app as ModernMediaApp).container.networkStore

    override fun onCleared() = browser.release()
}

/** One step in the folder path being browsed. */
private data class Crumb(val uri: Uri, val title: String)

/**
 * Network: play a link, find shared folders and media servers on the Wi-Fi, or add a server by
 * address. Browsing goes folder by folder; tapping a file plays it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onBack: () -> Unit, viewModel: NetworkViewModel = viewModel()) {
    val context = LocalContext.current
    val browser = viewModel.browser
    val discovered by browser.servers.collectAsStateWithLifecycle()
    val items by browser.items.collectAsStateWithLifecycle()
    val loading by browser.loading.collectAsStateWithLifecycle()
    val login by browser.login.collectAsStateWithLifecycle()
    val saved by viewModel.store.servers.collectAsStateWithLifecycle()
    val recentLinks by viewModel.store.recentLinks.collectAsStateWithLifecycle()
    val path = remember { mutableStateListOf<Crumb>() }
    var link by rememberSaveable { mutableStateOf("") }
    var addingServer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { browser.discover() }

    fun open(crumb: Crumb) {
        path += crumb
        browser.browse(crumb.uri)
    }

    fun goUp() {
        path.removeAt(path.lastIndex)
        path.lastOrNull()?.let { browser.browse(it.uri) }
    }

    fun playLink(text: String) {
        val clean = text.trim()
        val uri = Uri.parse(clean)
        if (uri.scheme.isNullOrBlank() || clean.length < 8) {
            Toast.makeText(context, "Enter a full link, e.g. https://…/video.m3u8", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.store.rememberLink(clean)
        context.play(uri)
    }

    BackHandler(enabled = path.isNotEmpty()) { goUp() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopBarColors(),
                title = { Text(path.lastOrNull()?.title ?: "Network", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (path.isNotEmpty()) goUp() else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (path.isEmpty()) {
                        IconButton(onClick = browser::discover) { Icon(Icons.Rounded.Refresh, contentDescription = "Search the network again") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (path.isNotEmpty()) {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
                    if (!loading && items.isEmpty()) {
                        item { Hint("No videos or music in this folder.") }
                    }
                    items(items, key = { it.uri.toString() }) { item ->
                        EntryRow(
                            icon = if (item.isFolder) Icons.Rounded.Folder else Icons.Rounded.Movie,
                            title = item.title,
                            onClick = {
                                if (item.isFolder) open(Crumb(item.uri, item.title)) else context.play(browser.withCredentials(item.uri))
                            },
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .glass(RoundedCornerShape(24.dp))
                                .padding(16.dp),
                        ) {
                            Text("Play a link", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Video or audio links, live streams (HLS .m3u8, DASH), RTSP and more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.size(10.dp))
                            OutlinedTextField(
                                value = link,
                                onValueChange = { link = it },
                                placeholder = { Text("https://…") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { playLink(link) }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.size(10.dp))
                            GlowButton(text = "Play", icon = Icons.Rounded.PlayArrow, onClick = { playLink(link) })
                        }
                    }
                    if (recentLinks.isNotEmpty()) {
                        item { SectionHeader("Recent links") }
                        items(recentLinks, key = { "link:$it" }) { recent ->
                            EntryRow(Icons.Rounded.Link, recent, onClick = { playLink(recent) }, onRemove = { viewModel.store.forgetLink(recent) })
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { SectionHeader("Saved servers") }
                            TextButton(onClick = { addingServer = true }) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add server")
                            }
                        }
                    }
                    if (saved.isEmpty()) item { Hint("Add a shared folder by its address, e.g. 192.168.1.10/Movies") }
                    items(saved, key = { "saved:${it.address}" }) { server ->
                        EntryRow(
                            icon = Icons.Rounded.Dns,
                            title = server.name,
                            subtitle = server.address,
                            onClick = {
                                browser.useCredentials(server)
                                open(Crumb(Uri.parse(server.address.normalizedAddress()), server.name))
                            },
                            onRemove = { viewModel.store.removeServer(server) },
                        )
                    }

                    item { SectionHeader("On this Wi-Fi") }
                    if (discovered.isEmpty()) item { Hint("Searching for shared folders and media servers… Make sure you're on the same Wi-Fi.") }
                    items(discovered, key = { "found:${it.uri}" }) { server ->
                        EntryRow(Icons.Rounded.Computer, server.title, subtitle = server.uri.scheme?.uppercase(), onClick = { open(Crumb(server.uri, server.title)) })
                    }
                }
            }
        }
    }

    login?.let { request ->
        LoginDialog(
            title = request.title,
            text = request.text,
            defaultUser = request.defaultUsername,
            onDismiss = browser::cancelLogin,
            onLogin = browser::login,
        )
    }
    if (addingServer) {
        AddServerDialog(
            onDismiss = { addingServer = false },
            onSave = { server ->
                viewModel.store.saveServer(server)
                addingServer = false
            },
        )
    }
}

private fun Context.play(uri: Uri) {
    startActivity(PlayerActivity.intent(this, uri, null))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun EntryRow(icon: ImageVector, title: String, onClick: () -> Unit, subtitle: String? = null, onRemove: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIconBadge(icon, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Remove $title", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun LoginDialog(title: String, text: String, defaultUser: String, onDismiss: () -> Unit, onLogin: (String, String) -> Unit) {
    var user by remember { mutableStateOf(defaultUser) }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onLogin(user, pass) }) { Text("Sign in") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddServerDialog(onDismiss: () -> Unit, onSave: (SavedServer) -> Unit) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("192.168.1.10/Movies") },
                    supportingText = { Text("Shared folder (SMB) address, or ftp://, nfs://, sftp://") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username (if needed)") }, singleLine = true)
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password (if needed)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = address.isNotBlank(),
                onClick = {
                    val full = address.normalizedAddress()
                    onSave(SavedServer(name.ifBlank { Uri.parse(full).host ?: full }, full, user.trim(), pass))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
