package com.beemdevelopment.aegis.desktop.ui.theme

import androidx.compose.ui.unit.dp

/** The spacing scale. Every gap in the app comes from here rather than being picked per screen. */
object Spacing {
    val tight = 4.dp
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val section = 32.dp
    val page = 32.dp
}

object Sizes {
    val contentMaxWidth = 460.dp

    /** Wider cap for pages that are lists rather than prose, like settings. */
    val wideContentMaxWidth = 720.dp

    val entryIcon = 40.dp
    val entryIconCompact = 32.dp
    val countdown = 22.dp

    val minWindowWidth = 560.dp
    val minWindowHeight = 620.dp
}
