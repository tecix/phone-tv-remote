package io.github.tecix.richtvremote.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tecix.richtvremote.HaTile
import kotlinx.coroutines.delay

/**
 * Wide tile for Home Assistant mode: the TV's own screen as the background,
 * with the app in front and how long the TV has been up.
 */
@Composable
fun HaTileCard(
    tile: HaTile, haName: String, onRefresh: () -> Unit,
    shape: Shape = MaterialTheme.shapes.extraLarge,
) {
    // Uptime is shown in minutes, so a once-a-minute tick is enough.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Crossfade(tile.screenshot, label = "tv-screenshot") { shot ->
            if (shot != null) {
                Image(shot, contentDescription = "TV screen", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tv, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Scrim so the text stays readable on any screenshot.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f),
                    )
                ),
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(Icons.Rounded.Home, haName, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = onRefresh,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.4f),
                    contentColor = Color.White,
                ),
            ) { Icon(Icons.Rounded.Refresh, "Refresh") }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                when {
                    tile.standby -> "TV is off"
                    else -> tile.appName ?: "Home screen"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            tile.bootedAtMillis?.takeIf { !tile.standby }?.let { booted ->
                Pill(Icons.Rounded.Schedule, "Up ${formatUptime(now - booted)}")
            }
        }
    }
}

@Composable
private fun Pill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun formatUptime(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(0)
    val days = minutes / (24 * 60)
    val hours = minutes / 60 % 24
    val mins = minutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}
