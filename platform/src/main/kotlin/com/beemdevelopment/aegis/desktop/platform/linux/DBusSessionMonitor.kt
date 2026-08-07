package com.beemdevelopment.aegis.desktop.platform.linux

import com.beemdevelopment.aegis.desktop.platform.SessionMonitor
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Watches D-Bus for screen lock and suspend, by parsing `gdbus monitor` output rather than linking
 * a D-Bus library. Only signal names cross the process boundary, never secrets.
 *
 * Both buses are watched: the screensaver is a session service, suspend is announced by logind on
 * the system bus.
 */
class DBusSessionMonitor : SessionMonitor {
    private val processes = CopyOnWriteArrayList<Process>()
    private val threads = CopyOnWriteArrayList<Thread>()

    @Volatile
    private var listener: SessionMonitor.Listener? = null

    @Volatile
    private var started = false

    override val isAvailable: Boolean
        get() = gdbusPath != null

    @Synchronized
    override fun start(listener: SessionMonitor.Listener) {
        if (started) {
            return
        }
        val gdbus = gdbusPath ?: return
        this.listener = listener
        started = true

        // Three screensaver interface names are in the wild; watching all is cheaper than probing.
        for (dest in SCREENSAVER_DESTINATIONS) {
            watch(listOf(gdbus, "monitor", "--session", "--dest", dest)) { line ->
                if (line.contains("ActiveChanged") && line.contains("true")) {
                    this.listener?.onSessionLocked()
                } else if (line.contains(".Lock ") || line.endsWith(".Lock")) {
                    this.listener?.onSessionLocked()
                }
            }
        }

        watch(listOf(gdbus, "monitor", "--system", "--dest", "org.freedesktop.login1")) { line ->
            when {
                // PrepareForSleep(true) fires before suspend, (false) after resume.
                line.contains("PrepareForSleep") && line.contains("true") ->
                    this.listener?.onSuspend()

                line.contains("PrepareForSleep") && line.contains("false") ->
                    this.listener?.onResume()

                line.contains("Session") && line.contains(".Lock") ->
                    this.listener?.onSessionLocked()

                line.contains("LockedHint") && line.contains("true") ->
                    this.listener?.onSessionLocked()
            }
        }
    }

    private fun watch(command: List<String>, onLine: (String) -> Unit) {
        val process = try {
            ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: Exception) {
            return
        }

        processes.add(process)
        val thread = Thread({
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!started) {
                            break
                        }
                        try {
                            onLine(line)
                        } catch (e: Exception) {
                            // A listener throwing must not kill the watcher.
                        }
                    }
                }
            } catch (e: Exception) {
                // The monitor died; carry on with one fewer lock trigger.
            }
        }, "aegis-dbus-monitor").apply { isDaemon = true }

        threads.add(thread)
        thread.start()
    }

    @Synchronized
    override fun stop() {
        started = false
        listener = null
        for (process in processes) {
            process.destroy()
        }
        processes.clear()
        threads.clear()
    }

    /**
     * Idle time from the screensaver interface. Usually null under Wayland, where idle time is
     * compositor state that most compositors do not put on D-Bus.
     */
    override fun systemIdleTime(): Duration? {
        val gdbus = gdbusPath ?: return null

        for (dest in SCREENSAVER_DESTINATIONS) {
            val path = "/" + dest.replace('.', '/')
            val output = runCommand(
                listOf(
                    gdbus, "call", "--session",
                    "--dest", dest,
                    "--object-path", path,
                    "--method", "$dest.GetSessionIdleTime",
                ),
            ) ?: continue

            // gdbus prints tuples like "(uint32 42,)".
            val seconds = Regex("(\\d+)").find(output)?.groupValues?.get(1)?.toLongOrNull()
            if (seconds != null) {
                return Duration.ofSeconds(seconds)
            }
        }

        return null
    }

    private fun runCommand(command: List<String>): String? = try {
        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0) {
            output
        } else {
            process.destroy()
            null
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        val SCREENSAVER_DESTINATIONS = listOf(
            "org.freedesktop.ScreenSaver",
            "org.gnome.ScreenSaver",
            "org.kde.screensaver",
        )

        val gdbusPath: String? by lazy {
            listOf("/usr/bin/gdbus", "/bin/gdbus", "/usr/local/bin/gdbus")
                .firstOrNull { java.io.File(it).canExecute() }
        }
    }
}
