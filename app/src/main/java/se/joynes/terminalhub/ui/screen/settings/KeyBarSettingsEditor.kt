package se.joynes.terminalhub.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.terminalhub.data.settings.KeyBarKeyDefinition
import se.joynes.terminalhub.data.settings.KeyBarLayoutConfig
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.theme.MegaDriveBg
import se.joynes.terminalhub.ui.theme.MegaDriveDim
import se.joynes.terminalhub.ui.theme.MegaDriveOnSurface
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MegaDriveSurface
import se.joynes.terminalhub.ui.theme.MonoFontFamily

private data class KeyPickerTarget(val rowIndex: Int, val keyIndex: Int?)

@Composable
fun KeyBarSettingsEditor(
    rows: List<List<String>>,
    onRowsChange: (List<List<String>>) -> Unit
) {
    val normalizedRows = KeyBarLayoutConfig.normalize(rows)
    var pickerTarget by remember { mutableStateOf<KeyPickerTarget?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        normalizedRows.forEachIndexed { rowIndex, row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MegaDriveDim)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ROW ${rowIndex + 1} · ${row.size}/${KeyBarLayoutConfig.MAX_KEYS_PER_ROW} KEYS",
                    color = MegaDriveOnSurface,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEachIndexed { keyIndex, keyId ->
                        KeyChip(
                            label = KeyBarLayoutConfig.definition(keyId)?.label ?: keyId,
                            onClick = { pickerTarget = KeyPickerTarget(rowIndex, keyIndex) }
                        )
                    }
                }
                RetroButton(
                    text = "+ KEY",
                    onClick = { pickerTarget = KeyPickerTarget(rowIndex, null) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = row.size < KeyBarLayoutConfig.MAX_KEYS_PER_ROW
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RetroButton(
                        text = "UP",
                        onClick = { onRowsChange(KeyBarLayoutConfig.moveRow(normalizedRows, rowIndex, rowIndex - 1)) },
                        modifier = Modifier.weight(1f),
                        enabled = rowIndex > 0
                    )
                    RetroButton(
                        text = "DOWN",
                        onClick = { onRowsChange(KeyBarLayoutConfig.moveRow(normalizedRows, rowIndex, rowIndex + 1)) },
                        modifier = Modifier.weight(1f),
                        enabled = rowIndex < normalizedRows.lastIndex
                    )
                }
                RetroButton(
                    text = "DELETE ROW",
                    onClick = { onRowsChange(KeyBarLayoutConfig.removeRow(normalizedRows, rowIndex)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = normalizedRows.size > 1
                )
            }
        }

        RetroButton(
            text = "+ ADD ROW",
            onClick = { onRowsChange(KeyBarLayoutConfig.addRow(normalizedRows)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = normalizedRows.size < KeyBarLayoutConfig.MAX_ROWS
        )
        RetroButton(
            text = "RESET DEFAULT",
            onClick = { onRowsChange(KeyBarLayoutConfig.defaultRows) },
            modifier = Modifier.fillMaxWidth(),
            enabled = normalizedRows != KeyBarLayoutConfig.defaultRows
        )
    }

    pickerTarget?.let { target ->
        KeyPickerDialog(
            canRemove = target.keyIndex != null && normalizedRows[target.rowIndex].size > 1,
            onPick = { keyId ->
                val updated = if (target.keyIndex == null) {
                    KeyBarLayoutConfig.addKey(normalizedRows, target.rowIndex, keyId)
                } else {
                    KeyBarLayoutConfig.replaceKey(normalizedRows, target.rowIndex, target.keyIndex, keyId)
                }
                onRowsChange(updated)
                pickerTarget = null
            },
            onRemove = {
                target.keyIndex?.let { keyIndex ->
                    onRowsChange(KeyBarLayoutConfig.removeKey(normalizedRows, target.rowIndex, keyIndex))
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null }
        )
    }
}

@Composable
private fun KeyChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(38.dp)
            .widthIn(min = 48.dp)
            .background(MegaDriveBg)
            .border(1.dp, MegaDrivePrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MegaDrivePrimary, fontFamily = MonoFontFamily, fontSize = 11.sp)
    }
}

@Composable
private fun KeyPickerDialog(
    canRemove: Boolean,
    onPick: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MegaDriveSurface,
        title = {
            Text("CHOOSE KEY", color = MegaDrivePrimary, fontFamily = MonoFontFamily)
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                KeyBarLayoutConfig.availableKeys.groupBy(KeyBarKeyDefinition::group).forEach { (group, keys) ->
                    item(key = "group-$group") {
                        Text(
                            group.uppercase(),
                            color = MegaDriveDim,
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(keys, key = KeyBarKeyDefinition::id) { key ->
                        Text(
                            text = key.label,
                            color = MegaDriveOnSurface,
                            fontFamily = MonoFontFamily,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(key.id) }
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = MegaDrivePrimary, fontFamily = MonoFontFamily)
            }
        },
        dismissButton = if (canRemove) {
            {
                TextButton(onClick = onRemove) {
                    Text("REMOVE KEY", color = MegaDrivePrimary, fontFamily = MonoFontFamily)
                }
            }
        } else null
    )
}
