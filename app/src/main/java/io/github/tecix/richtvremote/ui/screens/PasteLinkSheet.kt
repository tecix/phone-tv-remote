package io.github.tecix.richtvremote.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

private val urlPattern = Regex("https?://[^\\s<>\"]+")

/** First http(s) link in [text] (a pasted "Title https://…" share works too), or null. */
internal fun firstLink(text: CharSequence?): String? = text?.let { urlPattern.find(it)?.value }

private fun Context.clipboardLink(): String? = runCatching {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    firstLink(clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this))
}.getOrNull()

/**
 * Paste or type a video/audio link to play on the TV. Continue hands the link to the same
 * Play on TV sheet a shared link opens, so destination, headers and errors behave identically.
 */
@Composable
fun PasteLinkSheet(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    var text by remember { mutableStateOf("") }
    var clip by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val link = firstLink(text)
    val invalid = text.isNotBlank() && link == null

    fun submit() {
        val url = link ?: return
        runCatching { url.toUri() }.getOrNull()?.takeIf { !it.host.isNullOrBlank() }?.let { onSubmit(url) }
    }

    LaunchedEffect(Unit) {
        clip = context.clipboardLink()
        focus.requestFocus()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialShapes.Cookie6Sided.toShape(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Rounded.Link, null, Modifier.padding(16.dp))
                }
                Column {
                    Text("Play a link", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Paste a video or audio link to play on the TV.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Media link") },
                placeholder = { Text("https://…") },
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    } else {
                        IconButton(onClick = { context.clipboardLink()?.let { text = it } }) {
                            Icon(Icons.Rounded.ContentPaste, "Paste from clipboard")
                        }
                    }
                },
                isError = invalid,
                supportingText = { if (invalid) Text("The link must start with http:// or https://") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            clip?.takeIf { text.isEmpty() }?.let { copied ->
                AssistChip(
                    onClick = { text = copied },
                    leadingIcon = { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)) },
                    label = { Text("Paste $copied", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = ::submit,
                enabled = link != null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Text("  Continue")
            }
        }
    }
}
