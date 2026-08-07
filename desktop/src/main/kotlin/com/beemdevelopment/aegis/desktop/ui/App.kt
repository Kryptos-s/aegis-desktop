package com.beemdevelopment.aegis.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.beemdevelopment.aegis.desktop.AppState
import com.beemdevelopment.aegis.desktop.Screen
import com.beemdevelopment.aegis.desktop.ui.screens.AboutScreen
import com.beemdevelopment.aegis.desktop.ui.screens.EditEntryScreen
import com.beemdevelopment.aegis.desktop.ui.screens.EntriesScreen
import com.beemdevelopment.aegis.desktop.ui.screens.GroupsScreen
import com.beemdevelopment.aegis.desktop.ui.screens.ImportScreen
import com.beemdevelopment.aegis.desktop.ui.screens.IntroScreen
import com.beemdevelopment.aegis.desktop.ui.screens.PreferencesScreen
import com.beemdevelopment.aegis.desktop.ui.screens.UnlockScreen

/** The root of the UI. The pointer handler here is what feeds the idle lock timer. */
@Composable
fun App(state: AppState) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.status) {
        state.status?.let {
            snackbar.showSnackbar(it.text)
            state.status = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press || event.type == PointerEventType.Move) {
                            state.vaultManager.onUserInteraction()
                        }
                    }
                }
            },
    ) {
        when (val screen = state.screen) {
            is Screen.Intro -> IntroScreen(state)
            is Screen.Unlock -> UnlockScreen(state)
            is Screen.Entries -> EntriesScreen(state)
            is Screen.EditEntry -> EditEntryScreen(state, screen.entryUuid, screen.prefill)
            is Screen.Preferences -> PreferencesScreen(state)
            is Screen.Groups -> GroupsScreen(state)
            is Screen.Import -> ImportScreen(state)
            is Screen.About -> AboutScreen(state)
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
