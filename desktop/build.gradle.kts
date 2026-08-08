import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

val appVersion = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":platform"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)

    testImplementation(libs.junit)
    testImplementation(compose.desktop.uiTestJUnit4)
    // The vault test vectors written by the Android app live in :core's test resources.
    testImplementation(testFixtures(project(":core")))
}

compose.desktop {
    application {
        mainClass = "com.beemdevelopment.aegis.desktop.MainKt"

        jvmArgs += listOf(
            // A heap dump or an attached debugger would expose the vault key and every secret in
            // it. Neither is something a released build has any use for.
            "-XX:-HeapDumpOnOutOfMemoryError",
            "-XX:+DisableAttachMechanism",
            // Keep secrets out of a core file. This is belt and braces: the launcher also lowers
            // RLIMIT_CORE where the platform allows it.
            "-XX:-UsePerfData",
            "-Dapple.awt.application.appearance=system",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Dmg)

            packageName = "aegis"
            packageVersion = appVersion
            description = "Unofficial desktop port of Aegis Authenticator"
            copyright = "Copyright (C) Beem Development and contributors. GPL-3.0."
            vendor = "Beem Development"
            licenseFile = rootProject.file("LICENSE")

            // The app is offline by design. Trimming the runtime to the modules it actually uses
            // keeps the network stack, RMI and the attach API out of the shipped image entirely.
            modules(
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.prefs",
                "java.sql",
                "jdk.unsupported",
            )

            linux {
                packageName = "aegis"
                debMaintainer = "noreply@beemdevelopment.com"
                menuGroup = "Utility;Security"
                appCategory = "utils"
                iconFile.set(project.file("src/main/resources/icons/aegis.png"))
                shortcut = true
                // Installed so the polkit action exists; without it the app reports user-presence
                // checks as unavailable rather than skipping them.
                debPackageVersion = appVersion
            }

            windows {
                menuGroup = "Aegis"
                shortcut = true
                dirChooser = true
                // Stable so that upgrades replace the install rather than sitting beside it.
                upgradeUuid = "5f8b0a1c-6d3e-4b7a-9c2f-1e4d8a6b3c05"
            }

            macOS {
                bundleID = "com.beemdevelopment.aegis"
                appCategory = "public.app-category.utilities"
            }
        }
    }
}
