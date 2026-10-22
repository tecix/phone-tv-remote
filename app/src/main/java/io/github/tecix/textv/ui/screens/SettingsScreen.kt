package io.github.tecix.textv.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.tecix.textv.HaState
import io.github.tecix.textv.TvKey
import io.github.tecix.textv.data.DEFAULT_CAPTION_MACRO
import io.github.tecix.textv.data.Settings
import io.github.tecix.textv.ha.HaConfig
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: Settings,
    ha: HaState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSaveHa: (HaConfig) -> Unit,
    onTestHa: suspend (HaConfig) -> Result<String>,
    onVolumeKeys: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onCaptionMacro: (String) -> Unit,
    onForgetIdentity: () -> Unit,
) {
    var url by remember { mutableStateOf(settings.ha.url) }
    var token by remember { mutableStateOf(settings.ha.token) }
    var entity by remember { mutableStateOf(settings.ha.entityId.ifEmpty { "remote." }) }
    var mediaEntity by remember { mutableStateOf(settings.ha.mediaEntityId) }
    var macro by remember { mutableStateOf(settings.captionMacro) }
    var showToken by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }
    var confirmForget by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val draft = HaConfig(url, token, entity, mediaEntity)
    val dirty = draft != settings.ha

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Group("Home Assistant") {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Buttons go through the Android Debug Bridge integration (remote.send_command). " +
                                "Create a long-lived access token in your Home Assistant profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = url, onValueChange = { url = it; testResult = null },
                            label = { Text("Server URL") },
                            placeholder = { Text("http://homeassistant.local:8123") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = token, onValueChange = { token = it; testResult = null },
                            label = { Text("Access token") },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        if (showToken) "Hide token" else "Show token",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = entity, onValueChange = { entity = it; testResult = null },
                            label = { Text("Remote entity") },
                            placeholder = { Text("remote.living_room_tv") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = mediaEntity, onValueChange = { mediaEntity = it; testResult = null },
                            label = { Text("Media player entity (for the TV tile)") },
                            placeholder = { Text(draft.mediaPlayer) },
                            supportingText = { Text("Leave empty to use ${draft.mediaPlayer}. Turn on screencap in the integration's options for the screenshot.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val shown = testResult ?: when (ha) {
                            is HaState.Ready -> if (!dirty) Result.success(ha.name) else null
                            is HaState.Failed -> if (!dirty) Result.failure(Exception(ha.message)) else null
                            else -> null
                        }
                        shown?.let { result ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (result.isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
                                    tint = if (result.isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    result.fold({ "Connected to $it" }, { it.message ?: "Failed" }),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    testing = true
                                    scope.launch {
                                        testResult = onTestHa(draft)
                                        testing = false
                                    }
                                },
                                enabled = draft.isComplete && !testing,
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Test") }
                            Button(
                                onClick = {
                                    onSaveHa(draft)
                                    testResult = null
                                    scope.launch { snackbar.showSnackbar("Home Assistant settings saved") }
                                },
                                enabled = dirty,
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Save") }
                            if (testing) LoadingIndicator(Modifier.size(40.dp))
                        }
                    }
                }

                Group("SmartTube") {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "1.5× speed sends the S key. In SmartTube, turn on Settings › General › \"Remap S to speed toggle\" " +
                                "and pick 1.5× once; the button then switches between 1× and 1.5×.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val macroValid = TvKey.parseMacro(macro) != null
                        OutlinedTextField(
                            value = macro,
                            onValueChange = { macro = it },
                            label = { Text("Captions key sequence") },
                            isError = !macroValid,
                            supportingText = {
                                Text(
                                    if (macroValid) "Played from the SmartTube player. Keys: UP DOWN LEFT RIGHT OK BACK MENU, repeat with *n"
                                    else "Unknown key. Use UP DOWN LEFT RIGHT OK BACK MENU, e.g. DOWN RIGHT*2 OK BACK"
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { macro = DEFAULT_CAPTION_MACRO },
                                enabled = macro != DEFAULT_CAPTION_MACRO,
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Default") }
                            Button(
                                onClick = {
                                    onCaptionMacro(macro)
                                    scope.launch { snackbar.showSnackbar("Captions sequence saved") }
                                },
                                enabled = macroValid && macro.trim() != settings.captionMacro,
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Save") }
                        }
                    }
                }

                Group("Controls") {
                    SwitchRow(
                        "Phone volume buttons",
                        "Change the TV volume with your phone's volume keys while the app is open",
                        settings.volumeKeys, onVolumeKeys,
                    )
                    SwitchRow("Vibrate on press", "Haptic feedback for remote buttons", settings.haptics, onHaptics)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchRow("Dynamic color", "Use colors from your wallpaper", settings.dynamicColor, onDynamicColor)
                    }
                }

                Group("Direct connection") {
                    ListItem(
                        supportingContent = { Text("Creates a new certificate. Every TV must be paired again.") },
                        trailingContent = {
                            TextButton(onClick = { confirmForget = true }) { Text("Reset") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) { Text("Reset pairing identity") }
                }

                Text(
                    "tex-TV Remote · Direct uses the Android TV Remote protocol v2 (ports 6466/6467)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Reset pairing?") },
            text = { Text("All your TVs will ask to be paired again.") },
            confirmButton = {
                TextButton(onClick = { confirmForget = false; onForgetIdentity() }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) { Text(title) }
}
