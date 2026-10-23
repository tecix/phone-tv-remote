package io.github.tecix.textv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tecix.textv.TvKey
import io.github.tecix.textv.data.MacroAction
import io.github.tecix.textv.ui.components.rememberHaptic
import java.util.UUID

@Composable
fun MacroActionsSection(
    actions: List<MacroAction>,
    enabled: Boolean,
    onRun: (String) -> Unit,
    onSave: (MacroAction) -> Unit,
    onDelete: (String) -> Unit,
    editorOnly: Boolean = false,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MacroAction?>(null) }
    val haptic = rememberHaptic()
    val editMode = editorOnly || editing

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Macro actions", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!editorOnly && actions.isNotEmpty()) {
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Edit") }
            }
            TextButton(onClick = { selected = MacroAction(UUID.randomUUID().toString(), "", "") }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        if (actions.isEmpty()) {
            Text("Add an action to send a sequence of remote keys.", style = MaterialTheme.typography.bodyMedium)
        } else if (editMode) {
            Text("Tap an action to edit or delete it.", style = MaterialTheme.typography.bodySmall)
        }
        actions.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { action ->
                    FilledTonalButton(
                        onClick = {
                            haptic(false)
                            if (editMode) selected = action else onRun(action.id)
                        },
                        enabled = editMode || enabled,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        val icon = when {
                            editMode -> Icons.Rounded.Edit
                            action.id == "captions" -> Icons.Rounded.ClosedCaption
                            action.id == "speed" -> Icons.Rounded.Speed
                            else -> Icons.Rounded.PlayArrow
                        }
                        Icon(icon, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(action.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    selected?.let { action ->
        MacroActionDialog(
            action = action,
            existing = actions.any { it.id == action.id },
            onDismiss = { selected = null },
            onSave = { onSave(it); selected = null },
            onDelete = { onDelete(action.id); selected = null },
        )
    }
}

@Composable
private fun MacroActionDialog(
    action: MacroAction,
    existing: Boolean,
    onDismiss: () -> Unit,
    onSave: (MacroAction) -> Unit,
    onDelete: () -> Unit,
) {
    var name by rememberSaveable(action.id) { mutableStateOf(action.name) }
    var sequence by rememberSaveable(action.id) { mutableStateOf(action.sequence) }
    var showKeys by rememberSaveable(action.id) { mutableStateOf(false) }
    val validSequence = TvKey.parseMacro(sequence) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing) "Edit macro action" else "Add macro action") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sequence, onValueChange = { sequence = it },
                    label = { Text("Key sequence") },
                    placeholder = { Text("DOWN RIGHT*4 OK BACK") },
                    isError = sequence.isNotBlank() && !validSequence,
                    supportingText = {
                        Text(if (sequence.isNotBlank() && !validSequence) {
                            "Use valid key names; repeat a key with *1 to *20."
                        } else {
                            "Separate keys with spaces. For example: DOWN RIGHT*4 OK BACK"
                        })
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showKeys = !showKeys }) {
                    Text(if (showKeys) "Hide available keys" else "Show available keys")
                }
                if (showKeys) {
                    Text(
                        "UP, DOWN, LEFT, RIGHT, OK, CENTER, " + TvKey.entries.joinToString { it.name },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (action.id == "speed") {
                    Text(
                        "The default S key toggles SmartTube's last playback speed. Enable \"Remap S to speed toggle\" in SmartTube and select 1.5× once.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (existing) {
                    TextButton(onClick = onDelete) {
                        Text("Delete action", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(action.copy(name = name.trim(), sequence = sequence.trim())) },
                enabled = name.isNotBlank() && validSequence,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
