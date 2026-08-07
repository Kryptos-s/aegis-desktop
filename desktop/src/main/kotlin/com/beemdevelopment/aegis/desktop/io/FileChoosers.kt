package com.beemdevelopment.aegis.desktop.io

import com.beemdevelopment.aegis.desktop.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/** Native file and folder pickers. */
object FileChoosers {

    suspend fun openFile(state: AppState, title: String, filter: Filter? = null): Path? =
        choose(state) { owner -> showChooser(owner, title, save = false, filter = filter) }

    suspend fun saveFile(
        state: AppState,
        title: String,
        suggestedName: String,
        filter: Filter? = null,
    ): Path? = choose(state) { owner ->
        showChooser(owner, title, save = true, suggestedName = suggestedName, filter = filter)
    }

    suspend fun chooseDirectory(state: AppState, title: String): Path? =
        choose(state) { owner -> showChooser(owner, title, save = false, directories = true) }

    private suspend fun choose(state: AppState, block: (Window?) -> Path?): Path? {
        state.vaultManager.setAutoLockBlocked(true)
        return try {
            // Must wait from a background thread: a Swing modal opened on the EDT nests its own
            // event pump inside a continuation Compose is still waiting on, and the app freezes.
            withContext(Dispatchers.IO) {
                var result: Path? = null
                val owner = activeWindow()
                SwingUtilities.invokeAndWait { result = block(owner) }
                result
            }
        } catch (e: Exception) {
            null
        } finally {
            state.vaultManager.setAutoLockBlocked(false)
        }
    }

    // Without a real owner window, Wayland compositors can leave the dialog unmapped.
    private fun activeWindow(): Window? =
        Frame.getFrames().firstOrNull { it.isVisible }

    private fun showChooser(
        owner: Window?,
        title: String,
        save: Boolean,
        suggestedName: String? = null,
        directories: Boolean = false,
        filter: Filter? = null,
    ): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = if (directories) {
                JFileChooser.DIRECTORIES_ONLY
            } else {
                JFileChooser.FILES_ONLY
            }
            isMultiSelectionEnabled = false
            if (suggestedName != null) {
                selectedFile = File(suggestedName)
            }
            if (filter != null && !directories) {
                val extensionFilter = FileNameExtensionFilter(filter.description, *filter.extensions)
                addChoosableFileFilter(extensionFilter)
                fileFilter = extensionFilter
                isAcceptAllFileFilterUsed = true
            }
        }

        val result = if (save) {
            chooser.showSaveDialog(owner)
        } else {
            chooser.showOpenDialog(owner)
        }

        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
    }

    /** A file type to offer in the chooser. The user can still pick another. */
    data class Filter(val description: String, val extensions: Array<String>) {
        override fun equals(other: Any?): Boolean =
            other is Filter && description == other.description &&
                extensions.contentEquals(other.extensions)

        override fun hashCode(): Int = 31 * description.hashCode() + extensions.contentHashCode()

        companion object {
            val VAULT = Filter("Aegis vault (*.json)", arrayOf("json"))
            val IMAGE = Filter("Images", arrayOf("png", "jpg", "jpeg", "gif", "bmp", "webp"))
        }
    }
}
