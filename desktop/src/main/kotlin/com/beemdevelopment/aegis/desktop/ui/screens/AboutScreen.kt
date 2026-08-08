package com.beemdevelopment.aegis.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.beemdevelopment.aegis.desktop.AppState
import com.beemdevelopment.aegis.desktop.BuildInfo
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.ui.components.DetailPage
import com.beemdevelopment.aegis.desktop.ui.theme.Spacing

@Composable
fun AboutScreen(state: AppState) {
    DetailPage(title = Strings["about"], onBack = { state.back() }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(Strings["app_name_desktop"], style = MaterialTheme.typography.headlineMedium)
            Text("${Strings["version"]} ${BuildInfo.VERSION}")

            Card {
                Text(
                    Strings["unofficial_port"],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.medium),
                )
            }

            Card {
                Text(
                    Strings["no_network_statement"],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.medium),
                )
            }

            // The URL is plain text, not a link: nothing in this app launches an external program.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Aegis Authenticator\n")
                    }
                    append("https://github.com/beemdevelopment/Aegis\n\n")
                    append(
                        "Licensed under the GNU General Public License version 3. " +
                            "The full text is in the LICENSE file distributed with this program.",
                    )
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Text(Strings["third_party_libraries"], style = MaterialTheme.typography.titleMedium)
            Text(
                LIBRARIES.joinToString("\n") { "• $it" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val LIBRARIES = listOf(
    "Bouncy Castle — Argon2, PBKDF2 and ASN.1 (MIT)",
    "ZXing — QR code encoding and decoding (Apache 2.0)",
    "org.json — vault serialization (Public Domain)",
    "Protocol Buffers — Google Authenticator export payloads (BSD-3-Clause)",
    "zip4j — password-protected zip archives (Apache 2.0)",
    "SQLite JDBC — third-party authenticator databases (Apache 2.0)",
    "SimpleFlatMapper — CSV parsing (Apache 2.0)",
    "JNA — OS keychain and secret store integration (Apache 2.0 / LGPL 2.1)",
    "Compose Multiplatform — user interface (Apache 2.0)",
    "JSpecify — nullness annotations (Apache 2.0)",
)
