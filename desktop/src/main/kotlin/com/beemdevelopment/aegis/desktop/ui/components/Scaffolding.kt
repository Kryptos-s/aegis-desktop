package com.beemdevelopment.aegis.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.beemdevelopment.aegis.desktop.i18n.Strings
import com.beemdevelopment.aegis.desktop.ui.theme.Sizes
import com.beemdevelopment.aegis.desktop.ui.theme.Spacing

/** A page with a back arrow and a title: every screen other than the entry list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: androidx.compose.ui.unit.Dp = Sizes.wideContentMaxWidth,
    scrollable: Boolean = true,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Strings["back"],
                        )
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val column = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.medium)

            if (scrollable) {
                Column(modifier = column.verticalScroll(rememberScrollState()), content = content)
            } else {
                Column(modifier = column, content = content)
            }
        }
    }
}

typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

/** A page whose content is capped and centred in the window: the intro and unlock screens. */
@Composable
fun CenteredPage(
    modifier: Modifier = Modifier,
    maxWidth: androidx.compose.ui.unit.Dp = Sizes.contentMaxWidth,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.medium),
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(Spacing.page),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = verticalArrangement,
            modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun AppIcon(size: androidx.compose.ui.unit.Dp = 64.dp, modifier: Modifier = Modifier) {
    val painter: Painter? = remember {
        runCatching {
            AppIconHolder::class.java.getResourceAsStream("/icons/aegis.png")!!.use {
                BitmapPainter(loadImageBitmap(it))
            }
        }.getOrNull()
    }

    if (painter != null) {
        Image(painter = painter, contentDescription = null, modifier = modifier.size(size))
    }
}

private object AppIconHolder

@Composable
fun PageHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.small))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

val DialogContentPadding = PaddingValues(top = Spacing.small)
