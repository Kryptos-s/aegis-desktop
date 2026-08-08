package com.beemdevelopment.aegis.desktop.platform

import com.beemdevelopment.aegis.util.TempFiles
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Keeps a second Aegis from running against the same vault, using an OS-level lock on [lockFile].
 *
 * A loopback socket alongside it lets a second launch ask the first to raise its window. Its port
 * is written into the same owner-only lock file rather than being fixed.
 */
class SingleInstance(private val lockFile: Path) {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null
    private var listenerThread: Thread? = null

    /** Returns false if another instance already holds the lock. */
    fun acquire(): Boolean {
        return try {
            Files.createDirectories(lockFile.parent)
            val ch = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
            )
            TempFiles.restrictToOwner(lockFile)

            val fileLock = try {
                ch.tryLock()
            } catch (e: OverlappingFileLockException) {
                null
            }

            if (fileLock == null) {
                ch.close()
                return false
            }

            channel = ch
            lock = fileLock
            true
        } catch (e: IOException) {
            // A filesystem that cannot lock must not lock the user out of their own codes.
            true
        }
    }

    /** Starts listening for activation requests from later launches. */
    /**
     * @param onActivate called with the link the other launch carried, or null. The payload only
     *   pre-fills the entry editor for the user to confirm; it is never saved directly. The port
     *   lives in the 0600 lock file and the socket is bound to loopback, so nothing belonging to
     *   another user can reach it.
     */
    fun onActivationRequested(onActivate: (String?) -> Unit) {
        if (server != null) {
            return
        }

        val socket = try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        } catch (e: IOException) {
            return
        }
        server = socket
        writePort(socket.localPort)

        listenerThread = Thread({
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 2000
                        val line = client.getInputStream().bufferedReader().readLine() ?: return@use
                        if (!line.startsWith(ACTIVATE_TOKEN)) {
                            return@use
                        }
                        onActivate(line.removePrefix(ACTIVATE_TOKEN).trim().takeIf { it.isNotEmpty() })
                    }
                } catch (e: IOException) {
                    if (socket.isClosed) {
                        return@Thread
                    }
                }
            }
        }, "aegis-single-instance").apply { isDaemon = true }
        listenerThread?.start()
    }

    /** Asks the running instance to show its window. Best effort. */
    fun signalExistingInstance(payload: String? = null) {
        val port = readPort() ?: return
        // A newline would be read as a second message, so a payload containing one is dropped.
        val safe = payload?.takeIf { p -> p.none { it == '\n' || it == '\r' } }
        val message = if (safe != null) "$ACTIVATE_TOKEN $safe\n" else "$ACTIVATE_TOKEN\n"
        try {
            Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                socket.getOutputStream().write(message.toByteArray())
                socket.getOutputStream().flush()
            }
        } catch (e: IOException) {
            // The other instance may be starting up or shutting down.
        }
    }

    fun release() {
        runCatching { server?.close() }
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        server = null
        lock = null
        channel = null
    }

    private fun writePort(port: Int) {
        val ch = channel ?: return
        runCatching {
            ch.truncate(0)
            ch.position(0)
            ch.write(java.nio.ByteBuffer.wrap("$port\n".toByteArray()))
            ch.force(false)
        }
    }

    private fun readPort(): Int? = runCatching {
        Files.readString(lockFile).trim().toIntOrNull()
    }.getOrNull()

    private companion object {
        const val ACTIVATE_TOKEN = "aegis-activate"
    }
}
