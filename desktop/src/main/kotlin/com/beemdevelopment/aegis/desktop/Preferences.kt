package com.beemdevelopment.aegis.desktop

import com.beemdevelopment.aegis.AccountNamePosition
import com.beemdevelopment.aegis.BackupsVersioningStrategy
import com.beemdevelopment.aegis.CopyBehavior
import com.beemdevelopment.aegis.PassReminderFreq
import com.beemdevelopment.aegis.SortCategory
import com.beemdevelopment.aegis.util.TempFiles
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.UUID

/**
 * The app's settings, stored as JSON next to the vault. Key names match the Android app's
 * `SharedPreferences` keys so a setting means the same thing in both codebases.
 */
class Preferences(private val file: Path) {
    private val values: JSONObject = load()

    var lockOnSessionLock: Boolean
        get() = getBoolean(LOCK_ON_SESSION_LOCK, true)
        set(value) = putBoolean(LOCK_ON_SESSION_LOCK, value)

    var lockOnSuspend: Boolean
        get() = getBoolean(LOCK_ON_SUSPEND, true)
        set(value) = putBoolean(LOCK_ON_SUSPEND, value)

    var lockOnMinimize: Boolean
        get() = getBoolean(LOCK_ON_MINIMIZE, false)
        set(value) = putBoolean(LOCK_ON_MINIMIZE, value)

    /** Lock after this long without interaction. [Duration.ZERO] disables it. */
    var idleLockTimeout: Duration
        get() = Duration.ofSeconds(getLong(IDLE_LOCK_TIMEOUT, 300))
        set(value) = putLong(IDLE_LOCK_TIMEOUT, value.seconds)

    var keychainUnlockEnabled: Boolean
        get() = getBoolean(KEYCHAIN_UNLOCK, false)
        set(value) = putBoolean(KEYCHAIN_UNLOCK, value)

    var keychainRequiresPresence: Boolean
        get() = getBoolean(KEYCHAIN_REQUIRES_PRESENCE, true)
        set(value) = putBoolean(KEYCHAIN_REQUIRES_PRESENCE, value)

    var copyBehavior: CopyBehavior
        get() = CopyBehavior.fromInteger(getInt(COPY_BEHAVIOR, CopyBehavior.SINGLETAP.ordinal))
        set(value) = putInt(COPY_BEHAVIOR, value.ordinal)

    /** How long a copied code stays on the clipboard. [Duration.ZERO] leaves it there. */
    var clipboardClearDelay: Duration
        get() = Duration.ofSeconds(getLong(CLIPBOARD_CLEAR_DELAY, 30))
        set(value) = putLong(CLIPBOARD_CLEAR_DELAY, value.seconds)

    var minimizeOnCopy: Boolean
        get() = getBoolean(MINIMIZE_ON_COPY, false)
        set(value) = putBoolean(MINIMIZE_ON_COPY, value)

    var theme: Theme
        get() = Theme.entries.getOrElse(getInt(THEME, Theme.SYSTEM.ordinal)) { Theme.SYSTEM }
        set(value) = putInt(THEME, value.ordinal)

    var viewMode: ViewMode
        get() = ViewMode.entries.getOrElse(getInt(VIEW_MODE, ViewMode.NORMAL.ordinal)) { ViewMode.NORMAL }
        set(value) = putInt(VIEW_MODE, value.ordinal)

    var sortCategory: SortCategory
        get() = SortCategory.fromInteger(getInt(SORT_CATEGORY, SortCategory.CUSTOM.ordinal))
        set(value) = putInt(SORT_CATEGORY, value.ordinal)

    var accountNamePosition: AccountNamePosition
        get() = AccountNamePosition.fromInteger(getInt(ACCOUNT_NAME_POSITION, AccountNamePosition.END.ordinal))
        set(value) = putInt(ACCOUNT_NAME_POSITION, value.ordinal)

    var showIcons: Boolean
        get() = getBoolean(SHOW_ICONS, true)
        set(value) = putBoolean(SHOW_ICONS, value)

    var showNextCode: Boolean
        get() = getBoolean(SHOW_NEXT_CODE, false)
        set(value) = putBoolean(SHOW_NEXT_CODE, value)

    /** Hide codes until the entry is clicked. */
    var tapToReveal: Boolean
        get() = getBoolean(TAP_TO_REVEAL, false)
        set(value) = putBoolean(TAP_TO_REVEAL, value)

    var tapToRevealTime: Int
        get() = getInt(TAP_TO_REVEAL_TIME, 30)
        set(value) = putInt(TAP_TO_REVEAL_TIME, value)

    /** Digits per group when displaying a code. 0 means no grouping. */
    var codeGroupSize: Int
        get() = getInt(CODE_GROUP_SIZE, 3)
        set(value) = putInt(CODE_GROUP_SIZE, value)

    var highlightEntry: Boolean
        get() = getBoolean(HIGHLIGHT_ENTRY, false)
        set(value) = putBoolean(HIGHLIGHT_ENTRY, value)

    var language: String?
        get() = values.optString(LANGUAGE, "").takeIf { it.isNotEmpty() }
        set(value) {
            if (value == null) values.remove(LANGUAGE) else values.put(LANGUAGE, value)
            save()
        }

    var searchBehaviorMask: Int
        get() = getInt(SEARCH_BEHAVIOR_MASK, SEARCH_IN_ISSUER or SEARCH_IN_NAME)
        set(value) = putInt(SEARCH_BEHAVIOR_MASK, value)

    var groupFilter: Set<UUID>
        get() = values.optJSONArray(GROUP_FILTER)
            ?.let { array -> (0 until array.length()).mapNotNull { runCatching { UUID.fromString(array.getString(it)) }.getOrNull() } }
            ?.toSet()
            ?: emptySet()
        set(value) {
            values.put(GROUP_FILTER, JSONArray(value.map { it.toString() }))
            save()
        }

    var backupsEnabled: Boolean
        get() = getBoolean(BACKUPS, false)
        set(value) = putBoolean(BACKUPS, value)

    var backupsLocation: Path?
        get() = values.optString(BACKUPS_LOCATION, "").takeIf { it.isNotEmpty() }?.let { Path.of(it) }
        set(value) {
            if (value == null) values.remove(BACKUPS_LOCATION) else values.put(BACKUPS_LOCATION, value.toString())
            save()
        }

    var backupsVersionCount: Int
        get() = getInt(BACKUPS_VERSIONS, 5)
        set(value) = putInt(BACKUPS_VERSIONS, value)

    var backupVersioningStrategy: BackupsVersioningStrategy
        get() = BackupsVersioningStrategy.entries.getOrElse(
            getInt(BACKUPS_STRATEGY, BackupsVersioningStrategy.MULTIPLE_BACKUPS.ordinal),
        ) { BackupsVersioningStrategy.MULTIPLE_BACKUPS }
        set(value) = putInt(BACKUPS_STRATEGY, value.ordinal)

    var isBackupReminderNeeded: Boolean
        get() = getBoolean(BACKUP_REMINDER_NEEDED, false)
        set(value) = putBoolean(BACKUP_REMINDER_NEEDED, value)

    var passwordReminderFrequency: PassReminderFreq
        get() = PassReminderFreq.fromInteger(getInt(PASSWORD_REMINDER_FREQ, PassReminderFreq.BIWEEKLY.ordinal))
        set(value) = putInt(PASSWORD_REMINDER_FREQ, value.ordinal)

    var passwordReminderTimestamp: Long
        get() = getLong(PASSWORD_REMINDER_TIMESTAMP, 0)
        set(value) = putLong(PASSWORD_REMINDER_TIMESTAMP, value)

    /** Whether the warning shown before writing an unencrypted export has been dismissed. */
    var plaintextExportWarningDisabled: Boolean
        get() = getBoolean(PLAINTEXT_WARNING_DISABLED, false)
        set(value) = putBoolean(PLAINTEXT_WARNING_DISABLED, value)

    var introDone: Boolean
        get() = getBoolean(INTRO, false)
        set(value) = putBoolean(INTRO, value)

    var usageCounts: Map<UUID, Int>
        get() = readUuidMap(USAGE_COUNT) { it.toInt() }
        set(value) = writeUuidMap(USAGE_COUNT, value)

    var lastUsedTimestamps: Map<UUID, Long>
        get() = readUuidMap(LAST_USED) { it }
        set(value) = writeUuidMap(LAST_USED, value)

    private fun <T : Number> readUuidMap(key: String, convert: (Long) -> T): Map<UUID, T> {
        val obj = values.optJSONObject(key) ?: return emptyMap()
        val result = HashMap<UUID, T>()
        for (name in obj.keys()) {
            val uuid = runCatching { UUID.fromString(name) }.getOrNull() ?: continue
            result[uuid] = convert(obj.optLong(name))
        }
        return result
    }

    private fun writeUuidMap(key: String, value: Map<UUID, Number>) {
        val obj = JSONObject()
        for ((uuid, number) in value) {
            obj.put(uuid.toString(), number)
        }
        values.put(key, obj)
        save()
    }

    private fun getBoolean(key: String, default: Boolean) = values.optBoolean(key, default)
    private fun getInt(key: String, default: Int) = values.optInt(key, default)
    private fun getLong(key: String, default: Long) = values.optLong(key, default)

    private fun putBoolean(key: String, value: Boolean) {
        values.put(key, value)
        save()
    }

    private fun putInt(key: String, value: Int) {
        values.put(key, value)
        save()
    }

    private fun putLong(key: String, value: Long) {
        values.put(key, value)
        save()
    }

    private fun load(): JSONObject = try {
        if (Files.isRegularFile(file)) {
            JSONObject(Files.readString(file))
        } else {
            JSONObject()
        }
    } catch (e: Exception) {
        // A corrupt settings file must not stop the app from opening the vault.
        JSONObject()
    }

    @Synchronized
    private fun save() {
        try {
            Files.createDirectories(file.parent)
            val temp = Files.createTempFile(file.parent, ".prefs-", ".tmp")
            TempFiles.restrictToOwner(temp)
            Files.writeString(temp, values.toString(2))
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            TempFiles.restrictToOwner(file)
        } catch (e: Exception) {
            // Losing a preference is survivable; crashing while the vault is open is not.
        }
    }

    companion object {
        const val SEARCH_IN_ISSUER = 1 shl 0
        const val SEARCH_IN_NAME = 1 shl 1
        const val SEARCH_IN_NOTE = 1 shl 2
        const val SEARCH_IN_GROUPS = 1 shl 3

        const val BACKUPS_VERSIONS_INFINITE = -1

        private const val LOCK_ON_SESSION_LOCK = "pref_lock_on_session_lock"
        private const val LOCK_ON_SUSPEND = "pref_lock_on_suspend"
        private const val LOCK_ON_MINIMIZE = "pref_lock_on_minimize"
        private const val IDLE_LOCK_TIMEOUT = "pref_idle_lock_timeout"
        private const val KEYCHAIN_UNLOCK = "pref_keychain_unlock"
        private const val KEYCHAIN_REQUIRES_PRESENCE = "pref_keychain_requires_presence"

        private const val COPY_BEHAVIOR = "pref_current_copy_behavior"
        private const val CLIPBOARD_CLEAR_DELAY = "pref_clipboard_clear_delay"
        private const val MINIMIZE_ON_COPY = "pref_minimize_on_copy"

        private const val THEME = "pref_current_theme"
        private const val VIEW_MODE = "pref_current_view_mode"
        private const val SORT_CATEGORY = "pref_current_sort_category"
        private const val ACCOUNT_NAME_POSITION = "pref_account_name_position"
        private const val SHOW_ICONS = "pref_show_icons"
        private const val SHOW_NEXT_CODE = "pref_show_next_code"
        private const val TAP_TO_REVEAL = "pref_tap_to_reveal"
        private const val TAP_TO_REVEAL_TIME = "pref_tap_to_reveal_time"
        private const val CODE_GROUP_SIZE = "pref_code_group_size_string"
        private const val HIGHLIGHT_ENTRY = "pref_highlight_entry"
        private const val LANGUAGE = "pref_lang"

        private const val SEARCH_BEHAVIOR_MASK = "pref_search_behavior_mask"
        private const val GROUP_FILTER = "pref_group_filter_uuids"

        private const val BACKUPS = "pref_backups"
        private const val BACKUPS_LOCATION = "pref_backups_location"
        private const val BACKUPS_VERSIONS = "pref_backups_versions"
        private const val BACKUPS_STRATEGY = "pref_backups_strategy"
        private const val BACKUP_REMINDER_NEEDED = "pref_backups_reminder_needed"
        private const val PASSWORD_REMINDER_FREQ = "pref_password_reminder_freq"
        private const val PASSWORD_REMINDER_TIMESTAMP = "pref_password_reminder"
        private const val PLAINTEXT_WARNING_DISABLED = "pref_plaintext_backup_warning_disabled"

        private const val INTRO = "pref_intro"
        private const val USAGE_COUNT = "pref_usage_count"
        private const val LAST_USED = "pref_last_used_timestamps"
    }
}

enum class Theme {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM,
    SYSTEM_AMOLED,
}

enum class ViewMode {
    NORMAL,
    COMPACT,
    SMALL,
    TILES,
}
