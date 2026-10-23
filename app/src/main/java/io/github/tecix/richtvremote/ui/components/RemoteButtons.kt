package io.github.tecix.richtvremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Whether buttons should buzz on press; follows the user's setting. */
val LocalHaptics = compositionLocalOf { true }

@Composable
fun rememberHaptic(): (long: Boolean) -> Unit {
    val view = LocalView.current
    val enabled by rememberUpdatedState(LocalHaptics.current)
    return remember(view) {
        { long ->
            if (enabled) {
                view.performHapticFeedback(
                    if (long) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
                )
            }
        }
    }
}

/**
 * How a remote button reacts to being held:
 * - [Tap]: one press on release.
 * - [Repeat]: press immediately, then keep pressing while held (D-pad, volume).
 * - [LongPress]: a tap, or a long press after holding (OK, Home, Back).
 */
enum class HoldBehavior { Tap, Repeat, LongPress }

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 110L
private const val LONG_PRESS_MS = 500L

/**
 * A filled icon button with Expressive shape morphing on press, and press/hold handling
 * built on its interaction source so the ripple and shape animation stay intact.
 */
@Composable
fun RemoteIconButton(
    icon: ImageVector,
    contentDescription: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    iconSize: Dp = 28.dp,
    hold: HoldBehavior = HoldBehavior.Tap,
    onLongPress: (() -> Unit)? = null,
    enabled: Boolean = true,
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
) = RemoteButton(onPress, modifier, size, hold, onLongPress, enabled, shapes, colors) {
    Icon(icon, contentDescription, Modifier.size(iconSize))
}

@Composable
fun RemoteButton(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    hold: HoldBehavior = HoldBehavior.Tap,
    onLongPress: (() -> Unit)? = null,
    enabled: Boolean = true,
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit,
) {
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val currentPress by rememberUpdatedState(onPress)
    val currentLong by rememberUpdatedState(onLongPress)
    // Set once the hold timer has sent something, so the click on release doesn't send it again.
    // A quick tap can end before the timer starts; then the click sends instead.
    val handled = remember { booleanArrayOf(false) }

    // Repeat and long-press timers run while the button is held down.
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed, hold) {
        if (!pressed) return@LaunchedEffect
        handled[0] = false
        when (hold) {
            HoldBehavior.Repeat -> {
                handled[0] = true
                haptic(false)
                currentPress()
                delay(REPEAT_DELAY_MS)
                while (true) {
                    currentPress()
                    delay(REPEAT_INTERVAL_MS)
                }
            }
            HoldBehavior.LongPress -> {
                delay(LONG_PRESS_MS)
                handled[0] = true
                haptic(true)
                (currentLong ?: currentPress)()
            }
            HoldBehavior.Tap -> Unit
        }
    }

    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press-scale")
    FilledIconButton(
        onClick = {
            if (!handled[0]) {
                haptic(false)
                currentPress()
            }
            handled[0] = false
        },
        shapes = shapes,
        colors = colors,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        content = content,
    )
}

@Composable
fun accentColors() = IconButtonDefaults.filledIconButtonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
)
