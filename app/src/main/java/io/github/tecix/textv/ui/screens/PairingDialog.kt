package io.github.tecix.textv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tecix.textv.PairingState

@Composable
fun PairingDialog(
    state: PairingState,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == PairingState.Hidden) return
    var code by remember(state is PairingState.Starting) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val canSubmit = state is PairingState.EnterCode && code.length == 6

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Link, null) },
        title = {
            Text(
                when (state) {
                    is PairingState.Starting -> "Contacting ${state.tv.name}…"
                    is PairingState.EnterCode -> "Enter the code"
                    is PairingState.Verifying -> "Pairing…"
                    is PairingState.Failed -> "Pairing failed"
                    PairingState.Hidden -> ""
                },
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state) {
                    is PairingState.Starting, is PairingState.Verifying -> Box(Modifier.padding(16.dp)) {
                        ContainedLoadingIndicator(Modifier.size(72.dp))
                    }
                    is PairingState.EnterCode -> {
                        Text(
                            "${state.tvName} is showing a 6-character code.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedTextField(
                            value = code,
                            onValueChange = { v ->
                                code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                            },
                            singleLine = true,
                            isError = state.error != null,
                            supportingText = state.error?.let { { Text(it) } },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 28.sp,
                                letterSpacing = 8.sp,
                                textAlign = TextAlign.Center,
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false,
                            ),
                            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit(code) }),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.focusRequester(focus),
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    }
                    is PairingState.Failed -> Text(state.message, textAlign = TextAlign.Center)
                    PairingState.Hidden -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is PairingState.Failed -> TextButton(onClick = onRetry) { Text("Try again") }
                else -> TextButton(onClick = { onSubmit(code) }, enabled = canSubmit) { Text("Pair") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
