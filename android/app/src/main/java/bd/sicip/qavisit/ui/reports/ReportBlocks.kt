// generic renderers for the four template block types (fields/checklist/cards/flags) -- driven
// purely by the template's own data (labels, kinds, options), never a hardcoded question, so a
// future template version renders correctly without a UI code change. Shared by
// ui/reports/ReportSectionScreen.kt (one block per row on its own section screen); every editor
// here writes through the given ReportEditor (ui/reports/ReportEditing.kt), never touches Room
// directly, and calls editNow for discrete picks (segmented answer, dropdown, date/time picker,
// card add/remove, flag tick) or editDebounced for free-typed text (spec: debounce ~400ms).
package bd.sicip.qavisit.ui.reports

import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Warning
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.domain.report.AnswerOption
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.cardCompareMismatch
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.TimeField
import bd.sicip.qavisit.ui.common.showDatePicker
import bd.sicip.qavisit.ui.theme.LocalToneColors
import bd.sicip.qavisit.ui.theme.forToneId
import kotlinx.serialization.json.JsonObject

// N answer buttons across a row (4 for a checklist item's yes/no/partial/na, 2-3 for a choice
// field like rating/trainer-present) -- neutral outline until chosen, filled with the option's
// tone color once chosen, tapping the already-chosen one again clears it back to blank.
@Composable
fun AnswerButtons(options: List<AnswerOption>, selected: String, readOnly: Boolean, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val tones = LocalToneColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { opt ->
            val isOn = opt.id == selected
            val bg = if (isOn) tones.forToneId(opt.tone) else MaterialTheme.colorScheme.surface
            val fg = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(1.5.dp, if (isOn) bg else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .then(if (readOnly) Modifier else Modifier.clickable { onSelect(if (isOn) "" else opt.id) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(opt.label, color = fg, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

// one template Field rendered by its `kind` -- used both for a top-level `fields` block and for
// each field inside a `cards` entry (identical rendering rules either way).
@Composable
fun FieldEditor(
    field: Field,
    value: String,
    readOnly: Boolean,
    onImmediate: (String) -> Unit,
    onDebounced: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    when (field.kind) {
        "choice" -> Column(modifier.fillMaxWidth()) {
            Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            AnswerButtons(field.choiceOptions(), value, readOnly, onSelect = onImmediate)
        }

        "select" -> PickerDropdown(
            label = field.label,
            options = field.selectOptions(),
            selected = value,
            onSelect = onImmediate,
            modifier = modifier.fillMaxWidth(),
        )

        "date" -> OutlinedButton(
            onClick = { showDatePicker(context, value) { onImmediate(it) } },
            enabled = !readOnly,
            modifier = modifier.height(48.dp),
        ) { Text(if (value.isBlank()) field.label else "${field.label}: $value") }

        "time" -> Column(modifier.fillMaxWidth()) {
            Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            TimeField(value = value.ifBlank { "00:00:00" }, onChange = onImmediate)
        }

        "phone" -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onDebounced,
                label = { Text(field.label) },
                singleLine = true,
                readOnly = readOnly,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
            if (value.isNotBlank()) {
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$value"))) },
                    modifier = Modifier.height(48.dp),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call")
                }
            }
        }

        "number" -> OutlinedTextField(
            value = value,
            onValueChange = onDebounced,
            label = { Text(field.label) },
            singleLine = true,
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = modifier.fillMaxWidth(),
        )

        "longtext" -> OutlinedTextField(
            value = value,
            onValueChange = onDebounced,
            label = { Text(field.label) },
            readOnly = readOnly,
            minLines = 3,
            modifier = modifier.fillMaxWidth(),
        )

        else -> OutlinedTextField( // "text" and any unrecognised kind fall back to a plain single-line field
            value = value,
            onValueChange = onDebounced,
            label = { Text(field.label) },
            singleLine = true,
            readOnly = readOnly,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun FieldsBlockView(block: ReportBlock.Fields, data: ReportData, readOnly: Boolean, editor: ReportEditor, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        block.fields.forEach { field ->
            FieldEditor(
                field = field,
                value = data.field(field.key),
                readOnly = readOnly,
                onImmediate = { v -> editor.editNow(data.withField(field.key, v)) },
                onDebounced = { v -> editor.editDebounced(data.withField(field.key, v)) },
            )
        }
    }
}

// one checklist item: numbered question, 4 answer buttons (template.answers, always in that
// order regardless of how many options a `choice` field elsewhere has), remarks collapsed
// behind an "Add remarks" link until tapped or already non-blank.
@Composable
fun ChecklistBlockView(
    block: ReportBlock.Checklist,
    answers: List<AnswerOption>,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
    startIndex: Int = 1,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        block.heading?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        block.items.forEachIndexed { i, item ->
            val answer = data.checkAnswer(item.id)
            val remarks = data.checkRemarks(item.id)
            var remarksOpen by remember(item.id) { mutableStateOf(remarks.isNotBlank()) }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${startIndex + i}. ${item.text}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    AnswerButtons(
                        options = answers,
                        selected = answer,
                        readOnly = readOnly,
                        onSelect = { v -> editor.editNow(data.withCheck(item.id, answer = v)) },
                    )
                    if (remarksOpen) {
                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { v -> editor.editDebounced(data.withCheck(item.id, remarks = v)) },
                            label = { Text("Remarks") },
                            readOnly = readOnly,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (!readOnly) {
                        TextButton(onClick = { remarksOpen = true }) { Text("Add remarks") }
                    }
                }
            }
        }
    }
}

@Composable
fun CardsBlockView(block: ReportBlock.Cards, data: ReportData, readOnly: Boolean, editor: ReportEditor, modifier: Modifier = Modifier) {
    val entries = data.cards(block.key)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEachIndexed { index, entry ->
            CardEntryView(block, entry, index, data, readOnly, editor)
        }
        if (!readOnly) {
            OutlinedButton(
                onClick = { editor.editNow(data.withCardAdded(block.key)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add ${block.itemLabel}")
            }
        }
    }
}

@Composable
private fun CardEntryView(
    block: ReportBlock.Cards,
    entry: JsonObject,
    index: Int,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
) {
    val mismatch = block.compare?.let { cardCompareMismatch(it, entry) } ?: false
    // a filled card needs a confirm before removal; an empty seeded one goes straight away
    var confirmRemove by remember { mutableStateOf(false) }
    val hasContent = entry.values.any { it.toString().trim('"').isNotBlank() }
    // mismatch = one warning line under the header, not a red card: a tinted card body
    // hurts contrast outdoors (DESIGN.md sunlight rule)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${block.itemLabel} ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (!readOnly) {
                    IconButton(onClick = { if (hasContent) confirmRemove = true else editor.editNow(data.withCardRemoved(block.key, index)) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${block.itemLabel}")
                    }
                }
            }
            if (mismatch) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(
                        block.compare.message, // mismatch can only be true when block.compare is non-null
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            block.fields.forEach { field ->
                val value = data.cardField(block.key, index, field.key)
                FieldEditor(
                    field = field,
                    value = value,
                    readOnly = readOnly,
                    onImmediate = { v -> editor.editNow(data.withCardField(block.key, index, field.key, v)) },
                    onDebounced = { v -> editor.editDebounced(data.withCardField(block.key, index, field.key, v)) },
                )
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${block.itemLabel.lowercase()} ${index + 1}?") },
            text = { Text("Its answers will be deleted from this report.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    editor.editNow(data.withCardRemoved(block.key, index))
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun FlagsBlockView(block: ReportBlock.Flags, data: ReportData, readOnly: Boolean, editor: ReportEditor, modifier: Modifier = Modifier) {
    val ticked = data.flags()
    Column(modifier.fillMaxWidth()) {
        block.items.forEach { item ->
            val isTicked = item.id in ticked
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (readOnly) Modifier else Modifier.clickable { editor.editNow(data.withFlag(item.id, !isTicked)) })
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(checked = isTicked, onCheckedChange = if (readOnly) null else { v -> editor.editNow(data.withFlag(item.id, v)) })
                Text(item.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ReportBlockView(block: ReportBlock, answers: List<AnswerOption>, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    when (block) {
        is ReportBlock.Fields -> FieldsBlockView(block, data, readOnly, editor)
        is ReportBlock.Checklist -> ChecklistBlockView(block, answers, data, readOnly, editor)
        is ReportBlock.Cards -> CardsBlockView(block, data, readOnly, editor)
        is ReportBlock.Flags -> FlagsBlockView(block, data, readOnly, editor)
    }
}

// orange = actions only (DESIGN.md); the primary button of every report action bar
@Composable
fun actionButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.tertiary,
    contentColor = MaterialTheme.colorScheme.onTertiary,
)
