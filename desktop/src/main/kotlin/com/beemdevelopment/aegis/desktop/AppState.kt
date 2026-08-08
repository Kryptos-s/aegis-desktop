package com.beemdevelopment.aegis.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beemdevelopment.aegis.SortCategory
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.platform.Platform
import com.beemdevelopment.aegis.desktop.vault.AuditLog
import com.beemdevelopment.aegis.desktop.vault.VaultManager
import com.beemdevelopment.aegis.vault.VaultEntry
import com.beemdevelopment.aegis.vault.VaultRepository
import com.beemdevelopment.aegis.vault.VaultStore
import java.util.UUID

sealed interface Screen {
    data object Intro : Screen

    data object Unlock : Screen

    data object Entries : Screen

    data class EditEntry(val entryUuid: UUID?, val prefill: VaultEntry? = null) : Screen

    data object Preferences : Screen

    data object Groups : Screen

    data object Import : Screen

    data object About : Screen
}

/** Everything the UI reads, kept in step with the vault's lock state. */
class AppState(
    val paths: com.beemdevelopment.aegis.desktop.platform.AppPaths,
    val prefs: Preferences,
    val platform: Platform,
) {
    val auditLog = AuditLog(paths.auditLogFile)
    val vaultManager = VaultManager(VaultStore(paths.vaultFile), prefs, auditLog, platform)

    var screen: Screen by mutableStateOf(initialScreen())
        private set

    var busyMessage: String? by mutableStateOf(null)

    var status: StatusMessage? by mutableStateOf(null)

    var entries: List<VaultEntry> by mutableStateOf(emptyList())
        private set

    var searchQuery: String by mutableStateOf("")

    /**
     * An otpauth:// link the app was launched or activated with. It is held until the vault is
     * open, then opens the entry editor pre-filled. Nothing is saved without the user confirming.
     */
    private var pendingUri: String? = null

    var groupFilter: Set<UUID> by mutableStateOf(prefs.groupFilter)
        private set

    var revealed: Set<UUID> by mutableStateOf(emptySet())

    init {
        vaultManager.addListener(object : VaultManager.Listener {
            override fun onUnlocked(repository: VaultRepository) {
                refreshEntries()
                screen = Screen.Entries
                consumePendingUri()
            }

            override fun onBackupResult(error: String?) {
                if (error != null) {
                    showStatus(com.beemdevelopment.aegis.desktop.i18n.Strings
                        .format("backup_failed", error), isError = true)
                }
            }

            override fun onLocked(userInitiated: Boolean) {
                entries = emptyList()
                searchQuery = ""
                screen = Screen.Unlock
            }
        })
    }

    private fun initialScreen(): Screen =
        if (vaultManager.isVaultInitNeeded || !prefs.introDone) Screen.Intro else Screen.Unlock

    /** Navigates, bouncing to the unlock screen for any target that reads a locked vault. */
    fun navigate(screen: Screen) {
        if (!vaultManager.isUnlocked && screen !is Screen.Unlock && screen !is Screen.Intro) {
            showStatus(com.beemdevelopment.aegis.desktop.i18n.Strings["vault_locked"], isError = true)
            screen.let { this.screen = Screen.Unlock }
            return
        }
        this.screen = screen
    }

    fun goToUnlock() {
        screen = Screen.Unlock
    }

    fun back() {
        screen = if (vaultManager.isUnlocked) Screen.Entries else Screen.Unlock
    }

    fun refreshEntries() {
        if (!vaultManager.isUnlocked) {
            entries = emptyList()
            return
        }
        entries = vaultManager.vault.entries.toList()
    }

    fun visibleEntries(): List<VaultEntry> {
        var result = entries.asSequence()

        if (groupFilter.isNotEmpty()) {
            result = result.filter { entry ->
                if (NO_GROUP in groupFilter && entry.groups.isEmpty()) {
                    true
                } else {
                    entry.groups.any { it in groupFilter }
                }
            }
        }

        val query = searchQuery.trim()
        if (query.isNotEmpty()) {
            val mask = prefs.searchBehaviorMask
            result = result.filter { entry -> entry.matches(query, mask) }
        }

        val sorted = result.toMutableList()
        val sortCategory = prefs.sortCategory
        if (sortCategory != SortCategory.CUSTOM) {
            sortCategory.comparator?.let { sorted.sortWith(it) }
        }

        // Favourites float to the top whatever the sort order, as on Android.
        sorted.sortWith(compareByDescending { it.isFavorite })
        return sorted
    }

    private fun VaultEntry.matches(query: String, mask: Int): Boolean {
        val needle = query.lowercase()
        if (mask and Preferences.SEARCH_IN_ISSUER != 0 && issuer.lowercase().contains(needle)) {
            return true
        }
        if (mask and Preferences.SEARCH_IN_NAME != 0 && name.lowercase().contains(needle)) {
            return true
        }
        if (mask and Preferences.SEARCH_IN_NOTE != 0 && note.lowercase().contains(needle)) {
            return true
        }
        if (mask and Preferences.SEARCH_IN_GROUPS != 0 && vaultManager.isUnlocked) {
            val groupNames = groups.mapNotNull { uuid ->
                vaultManager.vault.findGroupByUUID(uuid)?.name?.lowercase()
            }
            if (groupNames.any { it.contains(needle) }) {
                return true
            }
        }
        return false
    }

    fun applyGroupFilter(filter: Set<UUID>) {
        groupFilter = filter
        prefs.groupFilter = filter
    }

    /** Queues a link, opening the editor straight away if the vault is already unlocked. */
    fun offerUri(uri: String) {
        pendingUri = uri
        if (vaultManager.isUnlocked) {
            consumePendingUri()
        }
    }

    private fun consumePendingUri() {
        val uri = pendingUri ?: return
        pendingUri = null

        val info = try {
            com.beemdevelopment.aegis.otp.GoogleAuthInfo.parseUri(uri.trim())
        } catch (e: Exception) {
            showStatus(e.message ?: Strings["error_occurred"], isError = true)
            return
        }

        screen = Screen.EditEntry(null, VaultEntry(info))
    }

    fun showStatus(message: String, isError: Boolean = false) {
        status = StatusMessage(message, isError)
    }

    fun shutdown() {
        vaultManager.shutdown()
    }

    companion object {
        /** Sentinel the group filter uses to mean "entries in no group". */
        val NO_GROUP: UUID = UUID(0, 0)
    }
}

data class StatusMessage(val text: String, val isError: Boolean)
