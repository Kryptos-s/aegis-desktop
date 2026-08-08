package com.beemdevelopment.aegis.desktop.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beemdevelopment.aegis.SortCategory
import com.beemdevelopment.aegis.desktop.AppState
import com.beemdevelopment.aegis.desktop.CopyBehaviorExt.shouldCopyOnClick
import com.beemdevelopment.aegis.desktop.CopyBehaviorExt.shouldCopyOnDoubleClick
import com.beemdevelopment.aegis.desktop.Screen
import com.beemdevelopment.aegis.desktop.ViewMode
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.ui.components.ConfirmDialog
import com.beemdevelopment.aegis.desktop.ui.components.EntryRow
import com.beemdevelopment.aegis.desktop.ui.components.QrCodeDialog
import com.beemdevelopment.aegis.desktop.ui.groupCode
import com.beemdevelopment.aegis.desktop.ui.otpStateFor
import com.beemdevelopment.aegis.desktop.ui.rememberOtpClock
import com.beemdevelopment.aegis.desktop.ui.rememberRevealState
import com.beemdevelopment.aegis.desktop.ui.theme.Spacing
import com.beemdevelopment.aegis.otp.HotpInfo
import com.beemdevelopment.aegis.vault.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EntriesScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val prefs = state.prefs
    val clock by rememberOtpClock()
    val reveal = rememberRevealState(prefs.tapToRevealTime)
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    var selectedIndex by remember { mutableStateOf(-1) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var contextMenuFor by remember { mutableStateOf<VaultEntry?>(null) }
    var qrForEntry by remember { mutableStateOf<VaultEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<VaultEntry?>(null) }

    val visible = state.visibleEntries()

    LaunchedEffect(state.vaultManager.isUnlocked) {
        if (!state.vaultManager.isUnlocked) {
            reveal.hideAll()
        }
    }

    fun copyCode(entry: VaultEntry) {
        val otp = otpStateFor(entry, System.currentTimeMillis(), includeNext = false)
        if (otp.error != null) {
            state.showStatus(otp.error, isError = true)
            return
        }

        state.platform.clipboard.copySensitive(otp.code, prefs.clipboardClearDelay)

        // The clipboard privacy hint is advisory: on some platforms a clipboard manager can still
        // record the code, so the message says so rather than promising it was private.
        val seconds = prefs.clipboardClearDelay.seconds.toInt()
        val message = buildString {
            append(if (seconds > 0) Strings.format("copied_clears_in", seconds) else Strings["copied_to_clipboard"])
            if (!state.platform.clipboard.copySensitiveIsPrivate) {
                append(' ')
                append(Strings["clipboard_not_private"])
            }
        }
        state.showStatus(message)

        prefs.usageCounts = prefs.usageCounts.toMutableMap().apply {
            this[entry.uuid] = (this[entry.uuid] ?: 0) + 1
        }
        prefs.lastUsedTimestamps = prefs.lastUsedTimestamps.toMutableMap().apply {
            this[entry.uuid] = System.currentTimeMillis()
        }
    }

    fun onEntryClicked(entry: VaultEntry) {
        if (prefs.tapToReveal && !reveal.isRevealed(entry.uuid)) {
            reveal.reveal(entry.uuid)
            return
        }
        if (prefs.copyBehavior.shouldCopyOnClick()) {
            copyCode(entry)
        }
    }

    fun toggleFavorite(entry: VaultEntry) {
        scope.launch {
            withContext(Dispatchers.IO) {
                state.vaultManager.vault.editEntry(entry) { it.setIsFavorite(!it.isFavorite) }
                state.vaultManager.saveAndBackup()
            }
            state.refreshEntries()
        }
    }

    fun refreshHotp(entry: VaultEntry) {
        scope.launch {
            withContext(Dispatchers.IO) {
                state.vaultManager.vault.editEntry(entry) {
                    val info = it.info
                    if (info is HotpInfo) {
                        info.incrementCounter()
                    }
                }
                state.vaultManager.saveAndBackup()
            }
            state.refreshEntries()
        }
    }

    fun deleteEntry(entry: VaultEntry) {
        scope.launch {
            withContext(Dispatchers.IO) {
                state.vaultManager.vault.removeEntry(entry)
                state.vaultManager.saveAndBackup()
            }
            state.refreshEntries()
            state.showStatus(Strings["entry_deleted"])
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings["app_name"], style = MaterialTheme.typography.titleLarge) },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showGroupMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = Strings["filter_by_group"])
                        }
                        GroupFilterMenu(state, showGroupMenu) { showGroupMenu = false }
                    }

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = Strings["sort_by"])
                        }
                        DropdownMenu(showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortCategory.entries.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(category)) },
                                    onClick = {
                                        prefs.sortCategory = category
                                        showSortMenu = false
                                        state.refreshEntries()
                                    },
                                )
                            }
                        }
                    }

                    IconButton(onClick = { state.vaultManager.lock(userInitiated = true) }) {
                        Icon(Icons.Default.Lock, contentDescription = Strings["lock_vault"])
                    }

                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text(Strings["groups"]) },
                                onClick = { showOverflow = false; state.navigate(Screen.Groups) },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings["import_label"]) },
                                onClick = { showOverflow = false; state.navigate(Screen.Import) },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings["preferences"]) },
                                onClick = { showOverflow = false; state.navigate(Screen.Preferences) },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings["about"]) },
                                onClick = { showOverflow = false; state.navigate(Screen.About) },
                            )
                            ViewMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text("${Strings["view_mode"]}: ${viewModeLabel(mode)}") },
                                    onClick = { prefs.viewMode = mode; showOverflow = false },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showAddMenu = true }) {
                    Icon(Icons.Default.Add, contentDescription = Strings["add_entry"])
                }
                AddEntryMenu(state, showAddMenu) { showAddMenu = false }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when {
                    event.isCtrlPressed && event.key == Key.F -> {
                        searchFocus.requestFocus(); true
                    }

                    event.isCtrlPressed && event.key == Key.L -> {
                        state.vaultManager.lock(userInitiated = true); true
                    }

                    event.isCtrlPressed && event.key == Key.N -> {
                        state.navigate(Screen.EditEntry(null)); true
                    }

                    event.key == Key.Escape && state.searchQuery.isNotEmpty() -> {
                        state.searchQuery = ""; true
                    }

                    event.key == Key.DirectionDown -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(visible.lastIndex); true
                    }

                    event.key == Key.DirectionUp -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0); true
                    }

                    event.key == Key.Enter && selectedIndex in visible.indices -> {
                        copyCode(visible[selectedIndex]); true
                    }

                    else -> false
                }
            },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { state.searchQuery = it },
                placeholder = { Text(Strings["search_entries"]) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { state.searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = Strings["cancel"])
                        }
                    }
                },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small)
                    .focusRequester(searchFocus),
            )

            if (state.groupFilter.isNotEmpty()) {
                ActiveFilterRow(state)
            }

            if (visible.isEmpty()) {
                EmptyState(
                    title = if (state.entries.isEmpty()) Strings["no_entries_yet"] else Strings["no_search_results"],
                    message = if (state.entries.isEmpty()) Strings["no_entries_yet_message"] else null,
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            LazyColumn(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.small,
                    end = Spacing.small,
                    top = Spacing.small,
                    // Room for the floating button, which would otherwise cover the last entry.
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.tight),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.uuid }) { entry ->
                    val index = visible.indexOf(entry)
                    val otp = otpStateFor(entry, clock, includeNext = prefs.showNextCode)
                    val hidden = prefs.tapToReveal && !reveal.isRevealed(entry.uuid)
                    val displayed = if (hidden) {
                        "•".repeat(entry.info.digits)
                    } else {
                        groupCode(otp.code, prefs.codeGroupSize)
                    }

                    Box {
                        EntryRow(
                            entry = entry,
                            otp = otp,
                            displayedCode = displayed,
                            revealed = !hidden,
                            selected = index == selectedIndex,
                            showIcon = prefs.showIcons,
                            viewMode = prefs.viewMode,
                            accountNamePosition = prefs.accountNamePosition,
                            onClick = { selectedIndex = index; onEntryClicked(entry) },
                            onDoubleClick = {
                                if (prefs.copyBehavior.shouldCopyOnDoubleClick()) copyCode(entry)
                            },
                            onToggleFavorite = { toggleFavorite(entry) },
                            onRefreshHotp = { refreshHotp(entry) },
                            modifier = Modifier.combinedClickable(
                                onClick = { selectedIndex = index; onEntryClicked(entry) },
                                onDoubleClick = {
                                    if (prefs.copyBehavior.shouldCopyOnDoubleClick()) copyCode(entry)
                                },
                                onLongClick = { contextMenuFor = entry },
                            ),
                        )

                        DropdownMenu(
                            expanded = contextMenuFor?.uuid == entry.uuid,
                            onDismissRequest = { contextMenuFor = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(Strings["copy"]) },
                                onClick = { contextMenuFor = null; copyCode(entry) },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings["edit"]) },
                                onClick = {
                                    contextMenuFor = null
                                    state.navigate(Screen.EditEntry(entry.uuid))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings["toggle_favorite"]) },
                                onClick = { contextMenuFor = null; toggleFavorite(entry) },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings["show_qr_code"]) },
                                onClick = { contextMenuFor = null; qrForEntry = entry },
                            )
                            DropdownMenuItem(
                                text = { Text(Strings["delete"]) },
                                onClick = { contextMenuFor = null; confirmDelete = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    qrForEntry?.let { entry ->
        QrCodeDialog(entry) { qrForEntry = null }
    }

    confirmDelete?.let { entry ->
        ConfirmDialog(
            title = Strings["delete"],
            message = Strings.format("confirm_delete_entry", entry.issuer, entry.name),
            confirmLabel = Strings["delete"],
            destructive = true,
            onConfirm = { confirmDelete = null; deleteEntry(entry) },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun EmptyState(title: String, message: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Spacing.medium))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(Spacing.small))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(340.dp),
            )
        }
    }
}

@Composable
private fun ActiveFilterRow(state: AppState) {
    val name = remember(state.groupFilter, state.entries) {
        val uuid = state.groupFilter.firstOrNull()
        when {
            uuid == null -> null
            uuid == AppState.NO_GROUP -> Strings["no_group"]
            state.vaultManager.isUnlocked -> state.vaultManager.vault.findGroupByUUID(uuid)?.name
            else -> null
        }
    } ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.tight),
    ) {
        androidx.compose.material3.AssistChip(
            onClick = { state.applyGroupFilter(emptySet()) },
            label = { Text(name) },
            trailingIcon = {
                Icon(
                    Icons.Default.Close,
                    contentDescription = Strings["all"],
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }
}

@Composable
private fun GroupFilterMenu(state: AppState, expanded: Boolean, onDismiss: () -> Unit) {
    val groups = if (state.vaultManager.isUnlocked) state.vaultManager.vault.groups.toList() else emptyList()

    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(Strings["all"]) },
            onClick = { state.applyGroupFilter(emptySet()); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(Strings["no_group"]) },
            onClick = { state.applyGroupFilter(setOf(AppState.NO_GROUP)); onDismiss() },
        )
        groups.forEach { group ->
            DropdownMenuItem(
                text = { Text(group.name) },
                onClick = { state.applyGroupFilter(setOf(group.uuid)); onDismiss() },
            )
        }
    }
}

private fun sortLabel(category: SortCategory): String = when (category) {
    SortCategory.CUSTOM -> Strings["sort_custom"]
    SortCategory.ACCOUNT -> Strings["sort_alphabetically_name"]
    SortCategory.ACCOUNT_REVERSED -> Strings["sort_alphabetically_name_reverse"]
    SortCategory.ISSUER -> Strings["sort_alphabetically"]
    SortCategory.ISSUER_REVERSED -> Strings["sort_alphabetically_reverse"]
    SortCategory.USAGE_COUNT -> Strings["sort_usage_count"]
    SortCategory.LAST_USED -> Strings["sort_last_used"]
}

private fun viewModeLabel(mode: ViewMode): String = when (mode) {
    ViewMode.NORMAL -> Strings["normal_viewmode_title"]
    ViewMode.COMPACT -> Strings["compact_mode_title"]
    ViewMode.SMALL -> Strings["small_mode_title"]
    ViewMode.TILES -> Strings["tiles_mode_title"]
}
