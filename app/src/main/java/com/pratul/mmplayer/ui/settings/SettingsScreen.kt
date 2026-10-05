package com.pratul.mmplayer.ui.settings

import com.pratul.mmplayer.ui.theme.auroraTopBarColors
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.pratul.mmplayer.data.settings.ColorTheme
import com.pratul.mmplayer.data.settings.SettingKeys
import com.pratul.mmplayer.ui.theme.paletteFor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.glass
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pratul.mmplayer.data.repository.MediaRepository
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.SettingsRepository
import com.pratul.mmplayer.player.engine.CodecEntry
import com.pratul.mmplayer.player.engine.CodecPath
import com.pratul.mmplayer.player.engine.CodecReport
import com.pratul.mmplayer.player.engine.CodecSupport
import com.pratul.mmplayer.ui.modernMediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repository.set(key, value) }
    }

    fun run(action: SettingAction, onDone: (String) -> Unit) {
        viewModelScope.launch {
            when (action) {
                SettingAction.CLEAR_HISTORY -> {
                    mediaRepository.clearHistory()
                    onDone("Watch history cleared")
                }
            }
        }
    }
}

@Composable
private fun settingsViewModel(): SettingsViewModel =
    modernMediaViewModel { SettingsViewModel(it.settingsRepository, it.mediaRepository) }

/** Settings home: one row per category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenCategory: (String) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SettingsTopBar("Settings", onBack, scrollBehavior) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(SettingsCatalog.categories, key = { it.id }) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(24.dp))
                        .clickable { onOpenCategory(category.id) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GradientIconBadge(category.icon, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            category.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Keep the last card clear of the system navigation buttons.
            item(key = "nav-space") { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
        }
    }
}

/** One category page, rendered generically from [SettingsCatalog]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(
    categoryId: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    viewModel: SettingsViewModel = settingsViewModel(),
) {
    val category = SettingsCatalog.byId(categoryId) ?: return
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var openChoice by remember { mutableStateOf<ChoiceItem<*>?>(null) }

    Scaffold(

        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SettingsTopBar(category.title, onBack, scrollBehavior) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            category.items.forEach { item ->
                SettingRow(
                    item = item,
                    settings = settings,
                    onSet = { key, value -> viewModel.set(key, value) },
                    onOpenChoice = { openChoice = it },
                    onAction = { viewModel.run(it, onShowMessage) },
                )
            }
            when (category.extra) {
                CategoryExtra.CODECS -> CodecSection()
                CategoryExtra.ABOUT -> AboutDeveloperSection()
                CategoryExtra.THEMES -> ThemePicker(settings.colorTheme) { viewModel.set(SettingKeys.COLOR_THEME, it.name) }
                CategoryExtra.SECURITY -> AppLockSection()
                null -> Unit
            }
            // Keep the last row clear of the system navigation buttons.
            Spacer(Modifier.navigationBarsPadding().height(32.dp))
        }
    }

    openChoice?.let { item ->
        @Suppress("UNCHECKED_CAST")
        ChoiceDialog(
            item = item as ChoiceItem<Any?>,
            current = item.value(settings),
            onSelect = { value ->
                viewModel.set(item.key, value)
                openChoice = null
            },
            onDismiss = { openChoice = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        colors = auroraTopBarColors(),
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun SettingRow(
    item: SettingItem,
    settings: AppSettings,
    onSet: (Preferences.Key<Any?>, Any?) -> Unit,
    onOpenChoice: (ChoiceItem<*>) -> Unit,
    onAction: (SettingAction) -> Unit,
) {
    val enabled = item.comingIn == null && item.enabledWhen(settings)
    val colors = if (enabled) ListItemDefaults.colors(containerColor = Color.Transparent) else ListItemDefaults.colors(
        containerColor = Color.Transparent,
        headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    )
    val supporting: @Composable (() -> Unit)? = supportingText(item, settings)?.let { text -> { Text(text) } }

    @Suppress("UNCHECKED_CAST")
    when (item) {
        is ToggleItem -> {
            val checked = item.value(settings)
            ListItem(
                headlineContent = { Text(item.title) },
                supportingContent = supporting,
                trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
                colors = colors,
                modifier = Modifier.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { onSet(item.key as Preferences.Key<Any?>, it) },
                ),
            )
        }
        is ChoiceItem<*> -> ListItem(
            headlineContent = { Text(item.title) },
            supportingContent = supporting,
            colors = colors,
            modifier = Modifier.clickable(enabled = enabled) { onOpenChoice(item) },
        )
        is SliderItem -> SliderRow(item, settings, enabled, supporting) { onSet(item.key as Preferences.Key<Any?>, it) }
        is ActionItem -> ListItem(
            headlineContent = { Text(item.title) },
            supportingContent = supporting,
            colors = colors,
            modifier = Modifier.clickable(enabled = enabled) { onAction(item.action) },
        )
    }
}

private fun supportingText(item: SettingItem, settings: AppSettings): String? {
    val current = when (item) {
        is ChoiceItem<*> -> {
            val value = item.value(settings)
            item.options.firstOrNull { it.value == value }?.label
        }
        else -> null
    }
    val coming = item.comingIn?.let { "Coming in $it" }
    return listOfNotNull(current ?: item.summary, coming).joinToString(" · ").ifBlank { null }
}

@Composable
private fun SliderRow(
    item: SliderItem,
    settings: AppSettings,
    enabled: Boolean,
    supporting: @Composable (() -> Unit)?,
    onCommit: (Int) -> Unit,
) {
    val saved = item.value(settings)
    var dragging by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(saved.toFloat()) }
    if (!dragging) position = saved.toFloat()
    val steps = ((item.range.last - item.range.first) / item.step - 1).coerceAtLeast(0)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                supporting?.let {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    ) { it() }
                }
            }
            Text(item.format(position.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = position,
            onValueChange = {
                dragging = true
                position = it
            },
            onValueChangeFinished = {
                dragging = false
                onCommit(position.roundToInt())
            },
            valueRange = item.range.first.toFloat()..item.range.last.toFloat(),
            steps = steps,
            enabled = enabled,
        )
    }
}

@Composable
private fun ChoiceDialog(
    item: ChoiceItem<Any?>,
    current: Any?,
    onSelect: (Any?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(
                Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) {
                item.options.forEach { option ->
                    val selected = option.value == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(option.value) })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(option.label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** What this device decodes in hardware, what FFmpeg covers, and what goes to VLC. */
@Composable
private fun CodecSection() {
    val report by produceState<CodecReport?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { CodecSupport.report() }
    }
    HorizontalDivider(Modifier.padding(top = 8.dp))
    SectionTitle("Engines")
    ListItem(
        colors = clearListItem(),
        headlineContent = { Text("Media3 (ExoPlayer)") },
        supportingContent = { Text("Main engine, uses the device's hardware decoders") },
    )
    ListItem(
        colors = clearListItem(),
        headlineContent = { Text("FFmpeg software decoders") },
        supportingContent = { Text(report?.ffmpegVersion?.let { "Available · $it" } ?: if (report == null) "Checking…" else "Not available on this device") },
    )
    ListItem(
        colors = clearListItem(),
        headlineContent = { Text("VLC engine (libVLC 3.7)") },
        supportingContent = { Text("Fallback for formats the main engine cannot open") },
    )
    val current = report ?: return
    SectionTitle("Video codecs on this device")
    current.video.forEach { CodecRow(it) }
    SectionTitle("Audio codecs on this device")
    current.audio.forEach { CodecRow(it) }
    SectionTitle("File formats")
    current.containers.forEach { ListItem(headlineContent = { Text(it, style = MaterialTheme.typography.bodyMedium) }, colors = clearListItem()) }
}

@Composable
private fun CodecRow(entry: CodecEntry) {
    ListItem(
        colors = clearListItem(),
        headlineContent = { Text(entry.name) },
        trailingContent = {
            Text(
                entry.path.label,
                style = MaterialTheme.typography.labelMedium,
                color = when (entry.path) {
                    CodecPath.HARDWARE -> MaterialTheme.colorScheme.primary
                    CodecPath.FFMPEG -> MaterialTheme.colorScheme.secondary
                    CodecPath.VLC -> MaterialTheme.colorScheme.tertiary
                },
            )
        },
    )
}

@Composable
private fun clearListItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Colour themes as swatch cards: each shows its own gradient; the chosen one gets a neon edge. */
@Composable
private fun ThemePicker(selected: ColorTheme, onPick: (ColorTheme) -> Unit) {
    SectionTitle("Color theme")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ColorTheme.entries.forEach { theme ->
            val palette = paletteFor(theme)
            val shape = RoundedCornerShape(22.dp)
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .glass(shape)
                    .then(if (theme == selected) Modifier.border(2.dp, Brush.linearGradient(palette.gradient), shape) else Modifier)
                    .selectable(selected = theme == selected, role = Role.RadioButton, onClick = { onPick(theme) })
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(palette.gradient)),
                )
                Text(theme.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    Text(
        text = "Glass gives an Apple-style frosted look. Every theme works in dark and light mode.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
    Spacer(Modifier.height(32.dp))
}
