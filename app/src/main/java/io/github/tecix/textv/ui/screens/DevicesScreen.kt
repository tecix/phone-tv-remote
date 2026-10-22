package io.github.tecix.textv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tecix.textv.data.SavedTv
import io.github.tecix.textv.discovery.DiscoveredTv

@Composable
fun DevicesScreen(
    saved: List<SavedTv>,
    selectedHost: String?,
    discovered: List<DiscoveredTv>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSelect: (SavedTv) -> Unit,
    onPair: (SavedTv) -> Unit,
    onRename: (SavedTv, String) -> Unit,
    onRemove: (SavedTv) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedTv?>(null) }
    val savedHosts = saved.map { it.host }.toSet()
    val nearby = discovered.filter { it.host !in savedHosts }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your TVs") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add by IP") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (saved.isNotEmpty()) {
                    item { SectionHeader("Saved") }
                    itemsIndexed(saved, key = { _, tv -> "saved-${tv.host}" }) { index, tv ->
                        var menu by remember { mutableStateOf(false) }
                        val selected = tv.host == selectedHost
                        SegmentedItem(index, saved.size, selected) {
                            ListItem(
                                supportingContent = { Text(tv.host) },
                                leadingContent = {
                                    Icon(
                                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Tv, null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Pair again") },
                                                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                                onClick = { menu = false; onPair(tv) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                                onClick = { menu = false; renaming = tv },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Remove") },
                                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                                onClick = { menu = false; onRemove(tv) },
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                onClick = { onSelect(tv) },
                            ) { Text(tv.name) }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("On this network", Modifier.weight(1f))
                        LoadingIndicator(Modifier.size(36.dp))
                    }
                }
                if (nearby.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.WifiFind, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    if (saved.isEmpty()) "Looking for Google TV and Android TV devices… Make sure your phone is on the same Wi-Fi."
                                    else "Looking for other TVs…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                itemsIndexed(nearby, key = { _, tv -> "found-${tv.host}" }) { index, tv ->
                    SegmentedItem(index, nearby.size, selected = false) {
                        ListItem(
                            supportingContent = { Text("${tv.host} · tap to pair") },
                            leadingContent = { Icon(Icons.Rounded.Tv, null, tint = MaterialTheme.colorScheme.tertiary) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            onClick = { onSelect(SavedTv(tv.name, tv.host)) },
                        ) { Text(tv.name) }
                    }
                }
            }
        }
    }

    if (adding) {
        TvDialog(
            title = "Add a TV",
            initialName = "",
            initialHost = "",
            confirm = "Add",
            onDismiss = { adding = false },
            onConfirm = { name, host ->
                adding = false
                onSelect(SavedTv(name.ifBlank { host }, host))
            },
        )
    }
    renaming?.let { tv ->
        TvDialog(
            title = "Rename TV",
            initialName = tv.name,
            initialHost = null,
            confirm = "Save",
            onDismiss = { renaming = null },
            onConfirm = { name, _ ->
                renaming = null
                onRename(tv, name)
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
    )
}

/** Expressive segmented list: the group has large outer corners, small ones between items. */
@Composable
private fun SegmentedItem(index: Int, count: Int, selected: Boolean, content: @Composable () -> Unit) {
    val big: Dp = 24.dp
    val small: Dp = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun TvDialog(
    title: String,
    initialName: String,
    initialHost: String?,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, host: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var host by remember { mutableStateOf(initialHost ?: "") }
    val hostValid = initialHost == null || host.trim().matches(Regex("""[A-Za-z0-9.\-:]+"""))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initialHost != null) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("IP address") },
                        placeholder = { Text("192.168.1.50") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Living room TV") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), host.trim()) }, enabled = hostValid && (initialHost == null || host.isNotBlank())) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
