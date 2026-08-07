package com.beemdevelopment.aegis.desktop.platform

import com.beemdevelopment.aegis.util.TempFiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Where the app keeps its files. */
class AppPaths(
    val dataDir: Path,
    val configDir: Path,
    val cacheDir: Path,
) {
    /** Same filename as on Android, so a synced vault needs no renaming. */
    val vaultFile: Path get() = dataDir.resolve("aegis.json")

    val iconsDir: Path get() = dataDir.resolve("icons")

    val preferencesFile: Path get() = configDir.resolve("preferences.json")

    val auditLogFile: Path get() = dataDir.resolve("audit-log.json")

    val lockFile: Path get() = dataDir.resolve("aegis.lock")

    fun createDirectories() {
        for (dir in listOf(dataDir, configDir, cacheDir)) {
            Files.createDirectories(dir)
            TempFiles.restrictToOwner(dir)
        }
    }

    companion object {
        private const val APP_NAME = "aegis"

        fun forCurrentOs(os: OperatingSystem = OperatingSystem.current): AppPaths {
            System.getenv("AEGIS_HOME")?.takeIf { it.isNotBlank() }?.let {
                val home = Paths.get(it).toAbsolutePath()
                return AppPaths(home, home, home.resolve("cache"))
            }

            val home = Paths.get(System.getProperty("user.home"))
            return when (os) {
                OperatingSystem.WINDOWS -> {
                    val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                        ?.let { Paths.get(it) }
                        ?: home.resolve("AppData").resolve("Roaming")
                    val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                        ?.let { Paths.get(it) }
                        ?: home.resolve("AppData").resolve("Local")
                    AppPaths(
                        dataDir = localAppData.resolve("Aegis"),
                        configDir = appData.resolve("Aegis"),
                        cacheDir = localAppData.resolve("Aegis").resolve("cache"),
                    )
                }

                OperatingSystem.MACOS -> {
                    val appSupport = home.resolve("Library").resolve("Application Support").resolve("Aegis")
                    AppPaths(
                        dataDir = appSupport,
                        configDir = appSupport,
                        cacheDir = home.resolve("Library").resolve("Caches").resolve("Aegis"),
                    )
                }

                else -> AppPaths(
                    dataDir = xdgDir("XDG_DATA_HOME", home.resolve(".local").resolve("share")).resolve(APP_NAME),
                    configDir = xdgDir("XDG_CONFIG_HOME", home.resolve(".config")).resolve(APP_NAME),
                    cacheDir = xdgDir("XDG_CACHE_HOME", home.resolve(".cache")).resolve(APP_NAME),
                )
            }
        }

        private fun xdgDir(variable: String, fallback: Path): Path {
            val value = System.getenv(variable)
            // The spec says a relative XDG path must be ignored, not resolved against the cwd.
            return if (!value.isNullOrBlank() && Paths.get(value).isAbsolute) {
                Paths.get(value)
            } else {
                fallback
            }
        }
    }
}
