package io.github.tecix.richtvremote.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.github.tecix.richtvremote.ui.components.rememberHaptic
import io.github.tecix.richtvremote.voice.VoiceCommandParser

@Composable
fun VoiceSearchSheet(
    route: String,
    preferredLanguage: String,
    onLanguageChange: (String) -> Unit,
    canSend: Boolean,
    onDismiss: () -> Unit,
    onSend: (String, Boolean) -> Boolean,
    startWithVoice: Boolean = true,
    onListeningChange: (Boolean) -> Unit = {},
) {
    var requested by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var recognized by rememberSaveable { mutableStateOf("") }
    var listening by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }
    val deviceLanguage = LocalConfiguration.current.locales[0].language
    val english = preferredLanguage.ifEmpty { deviceLanguage }.startsWith("en")
    val haptic = rememberHaptic()
    val queryFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        onListeningChange(false)
        if (result.resultCode == Activity.RESULT_CANCELED) {
            feedback = "Voice search cancelled. You can type a query."
        } else if (result.resultCode != Activity.RESULT_OK) {
            feedback = "Speech recognition failed. Try again or type a query."
        } else {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }.orEmpty()
            recognized = text
            val command = VoiceCommandParser.parse(text)
            if (command == null) {
                feedback = "No search query recognized. Try again or type below."
            } else {
                query = command.query
                if (!canSend) {
                    feedback = "Connect to the TV, then choose Search."
                } else if (onSend(command.query, false)) {
                    onDismiss()
                } else {
                    feedback = "Command could not be queued. Check the connection and query."
                }
            }
        }
    }
    val startListening: () -> Unit = {
        keyboard?.hide()
        haptic(false)
        try {
            listening = true
            onListeningChange(true)
            feedback = "Listening…"
            launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (english) "en-US" else "vi-VN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Mở Tom và Jerry tập 5")
            })
        } catch (_: ActivityNotFoundException) {
            listening = false
            onListeningChange(false)
            feedback = "No speech recognition service installed. Type a query below."
        } catch (_: SecurityException) {
            listening = false
            onListeningChange(false)
            feedback = "Speech recognition is unavailable. Type a query below."
        }
    }
    DisposableEffect(Unit) { onDispose { onListeningChange(false) } }
    LaunchedEffect(Unit) {
        if (!requested) {
            requested = true
            if (startWithVoice) startListening() else queryFocus.requestFocus()
        }
    }
    val send: (Boolean) -> Unit = { play ->
        haptic(false)
        keyboard?.hide()
        if (onSend(query, play)) onDismiss()
        else feedback = "Command could not be queued. Check the connection and query."
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SmartDisplay, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("SmartTube", style = MaterialTheme.typography.headlineSmall)
                    Text("Search or play · ${if (route == "DIRECT") "Direct" else "Home Assistant"}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close search") }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilledIconButton(onClick = startListening, enabled = !listening,
                            modifier = Modifier.size(64.dp), shapes = IconButtonDefaults.shapes()) {
                            if (listening) ContainedLoadingIndicator(Modifier.size(40.dp))
                            else Icon(Icons.Rounded.Mic, "Speak", Modifier.size(32.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (listening) "Listening…" else "Speak naturally", style = MaterialTheme.typography.titleMedium)
                            Text(if (listening) "Use the speech window on your phone" else "Speech searches SmartTube immediately",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !english, onClick = { onLanguageChange("vi-VN") },
                        leadingIcon = { if (!english) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        label = { Text("🇻🇳 Tiếng Việt") })
                    FilterChip(selected = english, onClick = { onLanguageChange("en-US") },
                        leadingIcon = { if (english) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        label = { Text("🇺🇸 English") })
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; feedback = "" },
                    label = { Text("Search query") },
                    placeholder = { Text("Video, song or artist") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; feedback = "" }) {
                            Icon(Icons.Rounded.Close, "Clear query")
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (canSend && query.isNotBlank()) send(false) }),
                    minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth().focusRequester(queryFocus),
                )
                if (recognized.isNotEmpty()) Text("Recognized: $recognized", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!canSend) Text("Selected remote route is not connected.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
                if (feedback.isNotEmpty()) Text(feedback, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text("Your phone’s speech service may need internet access.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = canSend && query.isNotBlank(), onClick = { send(false) },
                    shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search")
                }
                Button(enabled = canSend && query.isNotBlank(), onClick = { send(true) },
                    shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play")
                }
            }
        }
    }
}
