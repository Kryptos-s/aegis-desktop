package com.beemdevelopment.aegis.desktop

import com.beemdevelopment.aegis.desktop.platform.OperatingSystem
import java.io.File

/** Closes accidental leak routes (core files, heap dumps). Not a defence against local code. */
object Hardening {

    fun apply() {
        disableCoreDumps()
        assertNoProxyConfigured()
    }

    // The JVM exposes no setrlimit, so shell out to prlimit. Best effort; the packaged launcher
    // sets the limit too.
    private fun disableCoreDumps() {
        if (OperatingSystem.current != OperatingSystem.LINUX) {
            return
        }

        val pid = ProcessHandle.current().pid()
        runCatching {
            ProcessBuilder("prlimit", "--pid", pid.toString(), "--core=0")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor()
        }
    }

    // Aegis is offline; this stops a stray call silently picking up the system proxy.
    private fun assertNoProxyConfigured() {
        System.setProperty("java.net.useSystemProxies", "false")
    }

    /** Whether the process looks like it is being debugged. Used to warn, not to refuse to run. */
    fun isBeingDebugged(): Boolean {
        val runtime = java.lang.management.ManagementFactory.getRuntimeMXBean()
        if (runtime.inputArguments.any { it.startsWith("-agentlib:jdwp") }) {
            return true
        }

        if (OperatingSystem.current == OperatingSystem.LINUX) {
            val status = File("/proc/self/status")
            if (status.canRead()) {
                val tracer = status.readLines()
                    .firstOrNull { it.startsWith("TracerPid:") }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.toIntOrNull()
                if (tracer != null && tracer != 0) {
                    return true
                }
            }
        }

        return false
    }
}
