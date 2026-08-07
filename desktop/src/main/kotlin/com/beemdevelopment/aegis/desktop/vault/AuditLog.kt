package com.beemdevelopment.aegis.desktop.vault

import com.beemdevelopment.aegis.EventType
import com.beemdevelopment.aegis.util.TempFiles
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * A capped record of security-relevant vault events: unlocks, failed unlocks, exports, backups.
 * Only event types and timestamps are stored. Android keeps the same log in a Room database.
 */
class AuditLog(private val file: Path) {
    private val events = ArrayDeque<Event>()

    init {
        load()
    }

    @Synchronized
    fun record(type: EventType) {
        events.addLast(Event(type, Instant.now()))
        while (events.size > MAX_EVENTS) {
            events.removeFirst()
        }
        save()
    }

    @Synchronized
    fun all(): List<Event> = events.toList().asReversed()

    @Synchronized
    fun clear() {
        events.clear()
        save()
    }

    private fun load() {
        if (!Files.isRegularFile(file)) {
            return
        }

        try {
            val array = JSONArray(Files.readString(file))
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = runCatching { EventType.valueOf(obj.getString("type")) }.getOrNull() ?: continue
                val at = runCatching { Instant.ofEpochSecond(obj.getLong("at")) }.getOrNull() ?: continue
                events.addLast(Event(type, at))
            }
        } catch (e: Exception) {
            // A damaged log is not a reason to refuse to start.
            events.clear()
        }
    }

    private fun save() {
        try {
            val array = JSONArray()
            for (event in events) {
                array.put(
                    JSONObject()
                        .put("type", event.type.name)
                        .put("at", event.at.epochSecond),
                )
            }

            Files.createDirectories(file.parent)
            val temp = Files.createTempFile(file.parent, ".audit-", ".tmp")
            TempFiles.restrictToOwner(temp)
            Files.writeString(temp, array.toString())
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            TempFiles.restrictToOwner(file)
        } catch (e: Exception) {
            // Never let logging break the operation being logged.
        }
    }

    data class Event(val type: EventType, val at: Instant)

    private companion object {
        const val MAX_EVENTS = 500
    }
}
