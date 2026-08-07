package com.beemdevelopment.aegis.desktop.platform.linux

import com.beemdevelopment.aegis.desktop.platform.UserPresence
import java.util.concurrent.TimeUnit

/**
 * Asks polkit to confirm the user is present, so the prompt goes through the desktop's own
 * authentication agent and whatever the PAM stack accepts. The credential never reaches this app.
 *
 * Requires the shipped `com.beemdevelopment.aegis.policy` to be installed; without it pkcheck
 * reports the action as unknown and this reports itself unavailable.
 */
class PolkitUserPresence : UserPresence {

    override val isAvailable: Boolean by lazy {
        pkcheckPath != null && actionIsKnown()
    }

    override val name: String
        get() = "polkit"

    override fun authenticate(reason: String): Boolean {
        val pkcheck = pkcheckPath ?: return false

        return try {
            val process = ProcessBuilder(
                pkcheck,
                "--action-id", ACTION_ID,
                "--process", ProcessHandle.current().pid().toString(),
                "--allow-user-interaction",
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

            if (!process.waitFor(AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                return false
            }

            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Runs pkcheck without interaction to see whether the action is registered. A registered action
     * needing authentication exits 2, so only exit code 127 means polkit does not know it.
     */
    private fun actionIsKnown(): Boolean {
        val pkcheck = pkcheckPath ?: return false
        return try {
            val process = ProcessBuilder(
                pkcheck,
                "--action-id", ACTION_ID,
                "--process", ProcessHandle.current().pid().toString(),
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroy()
                return false
            }

            process.exitValue() != ACTION_UNKNOWN_EXIT
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val ACTION_ID = "com.beemdevelopment.aegis.unlock"
        const val ACTION_UNKNOWN_EXIT = 127
        const val AUTH_TIMEOUT_SECONDS = 120L

        val pkcheckPath: String? by lazy {
            listOf("/usr/bin/pkcheck", "/bin/pkcheck", "/usr/local/bin/pkcheck")
                .firstOrNull { java.io.File(it).canExecute() }
        }
    }
}
