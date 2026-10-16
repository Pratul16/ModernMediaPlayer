package com.pratul.mmplayer.media.network

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.MediaBrowser

/** A folder or playable file on a network server. */
data class NetworkItem(val uri: Uri, val title: String, val isFolder: Boolean)

/** A server the user added by address, e.g. smb://192.168.1.10/Movies. */
@Serializable
data class SavedServer(val name: String, val address: String, val username: String = "", val password: String = "")

/** VLC asks for a username and password for a share (answer with [NetworkBrowser.login]). */
data class LoginRequest(val title: String, val text: String, val defaultUsername: String)

/**
 * Finds and browses media on the local network through VLC: Windows/Samba shares (SMB) and
 * UPnP/DLNA media servers. One instance per screen; [release] when done.
 */
class NetworkBrowser(context: Context) {

    private val libVlc = LibVLC(context.applicationContext, arrayListOf("--no-audio", "--no-video"))
    private val main = Handler(Looper.getMainLooper())

    private val _servers = MutableStateFlow<List<NetworkItem>>(emptyList())
    /** Servers found on the Wi-Fi so far. */
    val servers: StateFlow<List<NetworkItem>> = _servers.asStateFlow()

    private val _items = MutableStateFlow<List<NetworkItem>>(emptyList())
    /** Contents of the folder being browsed. */
    val items: StateFlow<List<NetworkItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _login = MutableStateFlow<LoginRequest?>(null)
    val login: StateFlow<LoginRequest?> = _login.asStateFlow()
    private var pendingLogin: Dialog.LoginDialog? = null

    /** Credentials entered for a host, reused when playing files from it. */
    private val credentials = mutableMapOf<String, Pair<String, String>>()

    private var discoverer: MediaBrowser? = null
    private var folderBrowser: MediaBrowser? = null

    init {
        Dialog.setCallbacks(libVlc, object : Dialog.Callbacks {
            override fun onDisplay(dialog: Dialog.ErrorMessage) = Unit
            override fun onDisplay(dialog: Dialog.LoginDialog) {
                pendingLogin = dialog
                _login.value = LoginRequest(dialog.title ?: "Sign in", dialog.text ?: "", dialog.defaultUsername ?: "")
            }
            override fun onDisplay(dialog: Dialog.QuestionDialog) {
                // E.g. "accept this server's certificate?": take the affirmative answer.
                runCatching { dialog.postAction(1) }
            }
            override fun onDisplay(dialog: Dialog.ProgressDialog) = Unit
            override fun onCanceled(dialog: Dialog?) {
                if (dialog === pendingLogin) {
                    pendingLogin = null
                    _login.value = null
                }
            }
            override fun onProgressUpdate(dialog: Dialog.ProgressDialog) = Unit
        })
    }

    /** Look for SMB shares and UPnP/DLNA servers on the network. */
    fun discover() {
        discoverer?.release()
        _servers.value = emptyList()
        discoverer = MediaBrowser(libVlc, object : MediaBrowser.EventListener {
            override fun onMediaAdded(index: Int, media: IMedia) {
                val item = media.toItem() ?: return
                _servers.value = (_servers.value.filterNot { it.uri == item.uri } + item).sortedBy { it.title.lowercase() }
            }
            override fun onMediaRemoved(index: Int, media: IMedia) {
                val uri = media.uri ?: return
                _servers.value = _servers.value.filterNot { it.uri == uri }
            }
            override fun onBrowseEnd() = Unit
        }, main).also { it.discoverNetworkShares() }
    }

    /** List the folder at [uri] into [items]. */
    fun browse(uri: Uri) {
        folderBrowser?.release()
        _items.value = emptyList()
        _loading.value = true
        val found = mutableListOf<NetworkItem>()
        folderBrowser = MediaBrowser(libVlc, object : MediaBrowser.EventListener {
            override fun onMediaAdded(index: Int, media: IMedia) {
                media.toItem()?.takeIf { it.isFolder || it.title.isMedia() }?.let { found += it }
            }
            override fun onMediaRemoved(index: Int, media: IMedia) = Unit
            override fun onBrowseEnd() {
                // Folders first, then files, each in natural order.
                _items.value = found.sortedWith(compareBy<NetworkItem>({ !it.isFolder }, { it.title.lowercase() }))
                _loading.value = false
            }
        }, main).also { it.browse(withCredentials(uri), MediaBrowser.Flag.Interact) }
    }

    fun login(username: String, password: String) {
        val dialog = pendingLogin ?: return
        pendingLogin = null
        _login.value = null
        runCatching { dialog.postLogin(username, password, false) }
        lastHost?.let { credentials[it] = username to password }
    }

    fun cancelLogin() {
        runCatching { pendingLogin?.dismiss() }
        pendingLogin = null
        _login.value = null
        _loading.value = false
    }

    /** Remember a saved server's login so browsing and playback don't ask for it. */
    fun useCredentials(server: SavedServer) {
        val host = Uri.parse(server.address.normalizedAddress()).host ?: return
        if (server.username.isNotBlank()) credentials[host] = server.username to server.password
    }

    private var lastHost: String? = null

    /**
     * The address to play [uri] with: logins entered for its host are added to the address
     * (smb://user:password@host/...), since the player has no way to ask again.
     */
    fun withCredentials(uri: Uri): Uri {
        val host = uri.host ?: return uri
        lastHost = host
        val (user, pass) = credentials[host] ?: return uri
        if (uri.userInfo != null) return uri
        val userInfo = Uri.encode(user) + if (pass.isNotEmpty()) ":" + Uri.encode(pass) else ""
        return uri.buildUpon().encodedAuthority("$userInfo@${uri.encodedAuthority}").build()
    }

    fun release() {
        discoverer?.release()
        folderBrowser?.release()
        discoverer = null
        folderBrowser = null
        runCatching { Dialog.setCallbacks(libVlc, null) }
        libVlc.release()
    }

    private fun IMedia.toItem(): NetworkItem? {
        val uri = uri ?: return null
        val title = getMeta(IMedia.Meta.Title)?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: uri.host ?: return null
        val folder = type == IMedia.Type.Directory
        return NetworkItem(uri, title, folder)
    }

    companion object {
        private val MEDIA_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts", "m2ts", "mts", "mpg", "mpeg", "vob", "ogv", "divx", "rmvb", "asf",
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "alac", "ape", "mka", "amr",
        )

        fun String.isMedia(): Boolean = substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS

        /** "192.168.1.10/Movies" → "smb://192.168.1.10/Movies"; addresses with a scheme are kept. */
        fun String.normalizedAddress(): String {
            val clean = trim().trimEnd('/')
            return if ("://" in clean) clean else "smb://${clean.removePrefix("\\\\").replace('\\', '/')}"
        }
    }
}

/** Saved servers and recently played links, kept in the app's private storage. */
class NetworkStore(context: Context) {
    private val prefs = context.getSharedPreferences("network", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _servers = MutableStateFlow(load<List<SavedServer>>(KEY_SERVERS) ?: emptyList())
    val servers: StateFlow<List<SavedServer>> = _servers.asStateFlow()

    private val _recentLinks = MutableStateFlow(load<List<String>>(KEY_LINKS) ?: emptyList())
    val recentLinks: StateFlow<List<String>> = _recentLinks.asStateFlow()

    fun saveServer(server: SavedServer) {
        _servers.value = (_servers.value.filterNot { it.address == server.address } + server).sortedBy { it.name.lowercase() }
        prefs.edit().putString(KEY_SERVERS, json.encodeToString(_servers.value)).apply()
    }

    fun removeServer(server: SavedServer) {
        _servers.value = _servers.value - server
        prefs.edit().putString(KEY_SERVERS, json.encodeToString(_servers.value)).apply()
    }

    fun rememberLink(link: String) {
        _recentLinks.value = (listOf(link) + _recentLinks.value.filterNot { it == link }).take(MAX_LINKS)
        prefs.edit().putString(KEY_LINKS, json.encodeToString(_recentLinks.value)).apply()
    }

    fun forgetLink(link: String) {
        _recentLinks.value = _recentLinks.value - link
        prefs.edit().putString(KEY_LINKS, json.encodeToString(_recentLinks.value)).apply()
    }

    private inline fun <reified T> load(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private companion object {
        const val KEY_SERVERS = "servers"
        const val KEY_LINKS = "recent_links"
        const val MAX_LINKS = 10
    }
}
