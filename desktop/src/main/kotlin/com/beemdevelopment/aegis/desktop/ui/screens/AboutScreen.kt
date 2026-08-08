package com.beemdevelopment.aegis.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.beemdevelopment.aegis.desktop.AppState
import com.beemdevelopment.aegis.desktop.BuildInfo
import com.beemdevelopment.aegis.desktop.i18n.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(state: AppState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings["about"]) },
                navigationIcon = {
                    IconButton(onClick = { state.back() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = Strings["back"])
                    }
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(Strings["app_name_desktop"], style = MaterialTheme.typography.headlineMedium)
            Text("${Strings["version"]} ${BuildInfo.VERSION}")

            Card {
                Text(
                    Strings["unofficial_port"],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Card {
                Text(
                    Strings["no_network_statement"],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
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
