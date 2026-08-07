package com.beemdevelopment.aegis.desktop.platform.awt

import com.beemdevelopment.aegis.desktop.platform.SecureClipboard
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * The system clipboard, via AWT. The `x-kde-passwordManagerHint` marker is advisory: nothing forces
 * a clipboard manager to honour it, and any process in the session can read the code regardless.
 */
class AwtClipboard(
    private val supportsPrivateHint: Boolean,
) : SecureClipboard {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "aegis-clipboard").apply { isDaemon = true }
    }

    private var pendingClear: ScheduledFuture<*>? = null

    /** What we last put on the clipboard, so a clear does not eat someone else's content. */
    @Volatile
    private var lastCopied: String? = null

    override val copySensitiveIsPrivate: Boolean
        get() = supportsPrivateHint

    @Synchronized
    override fun copySensitive(text: String, clearAfter: Duration) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val contents: Transferable =
            if (supportsPrivateHint) SensitiveStringSelection(text) else StringSelection(text)

        clipboard.setContents(contents, null)
        lastCopied = text

        pendingClear?.cancel(false)
        pendingClear = if (!clearAfter.isZero && !clearAfter.isNegative) {
            scheduler.schedule({ clearIfOurs() }, clearAfter.toMillis(), TimeUnit.MILLISECONDS)
        } else {
            null
        }
    }

    override fun readText(): String? {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        return try {
            val text = clipboard.getData(DataFlavor.stringFlavor) as? String
            text?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: UnsupportedFlavorException) {
            null
        } catch (e: IllegalStateException) {
            // Another application is holding the clipboard.
            null
        } catch (e: java.io.IOException) {
            null
        }
    }

    @Synchronized
    override fun clearIfOurs() {
        val ours = lastCopied ?: return
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard

        val current = try {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } catch (e: Exception) {
            null
        }

        if (current == ours) {
            // Empty string rather than null: null contents throws, and on X11 ownership cannot be
            // relinquished without another client taking it.
            clipboard.setContents(StringSelection(""), null)
        }
        lastCopied = null
        pendingClear?.cancel(false)
        pendingClear = null
    }

    /** A string plus the hint that asks clipboard managers not to record it. */
    private class SensitiveStringSelection(private val text: String) : Transferable {
        private val hintFlavor = DataFlavor(
            "application/octet-stream;class=java.io.InputStream",
            PASSWORD_MANAGER_HINT,
        )

        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(
            DataFlavor.stringFlavor,
            DataFlavor.getTextPlainUnicodeFlavor(),
            hintFlavor,
        )

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
            transferDataFlavors.any { it.match(flavor) }

        override fun getTransferData(flavor: DataFlavor): Any = when {
            flavor.isMimeTypeEqual(hintFlavor) -> ByteArrayInputStream(SECRET_HINT)
            flavor == DataFlavor.stringFlavor -> text
            flavor.isRepresentationClassInputStream ->
                ByteArrayInputStream(text.toByteArray(charset(flavor)))
            flavor.representationClass == String::class.java -> text
            else -> throw UnsupportedFlavorException(flavor)
        }

        private fun charset(flavor: DataFlavor): java.nio.charset.Charset =
            flavor.getParameter("charset")?.let {
                runCatching { java.nio.charset.Charset.forName(it) }.getOrNull()
            } ?: Charsets.UTF_8

        private companion object {
            const val PASSWORD_MANAGER_HINT = "x-kde-passwordManagerHint"
            val SECRET_HINT = "secret".toByteArray(Charsets.UTF_8)
        }
    }
}
