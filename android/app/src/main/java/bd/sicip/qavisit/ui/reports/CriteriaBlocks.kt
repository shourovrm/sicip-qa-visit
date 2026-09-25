// QA report `criteria` block UI (spec §8) -- one Annex-3 table rendered as a scrolling list of
// item cards on the section's own screen (ui/reports/ReportSectionScreen.kt), same
// whole-section-per-screen architecture every other block type in this app already uses (the
// mockup's "one criterion per screen" figure is an illustration of the interaction, not a
// mandated navigation shape -- spec §8's actual text only requires the pieces this file renders).
// A heading item (no options) prints as a plain numbered heading line; a real item is a Card with
// its evidence hint, one OptionRow per option (3-way Seen/Not seen/N/A, an optional detail box, a
// collapsible remark box), the item's own Evidence-seen/Other-remarks boxes, and a live Remarks
// preview built by domain/report/Remarks.kt's printedRemarks -- the exact same fn the PDF
// (pdf/QaReportHtml.kt) prints from, so what the officer sees here is never out of sync with the
// output.
//
// Every text box has an Improve wording button (same as the surprise report). The Remarks
// preview has Edit: the officer rewrites the final remarks by hand (with Improve wording too);
// the edit is stored as the item's `ai` entry (source = the bullets it replaced), so changing an
// answer later makes it stale and the preview says so. SectionRemarksDialog lists one section's
// remarks with Edit on each -- opened from the end of the section and from Review.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bd.sicip.qavisit.domain.report.CriteriaItem
import bd.sicip.qavisit.domain.report.CriteriaOption
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.buildRemarks
import bd.sicip.qavisit.domain.report.printedRemarks
import bd.sicip.qavisit.ui.theme.LocalToneColors

@Composable
fun CriteriaBlockView(block: ReportBlock.Criteria, data: ReportData, readOnly: Boolean, editor: ReportEditor, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        block.intro?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        block.items.forEach { item ->
            if (item.heading) {
                Text(
                    "${item.no.orEmpty()} ${item.text}".trim(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                CriteriaItemCard(item, data, readOnly, editor)
            }
        }
    }
}

@Composable
private fun CriteriaItemCard(item: CriteriaItem, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${item.no.orEmpty()} ${item.text}".trim(), style = MaterialTheme.typography.bodyLarge)
            item.evidence?.takeIf { it.isNotBlank() }?.let { evidence ->
                Text(
                    buildString { append("Evidence (Annex-3): "); append(evidence) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.options.forEach { option ->
                OptionRow(item.id, option, data, readOnly, editor)
            }
            OutlinedTextField(
                value = data.criteriaEvidence(item.id),
                onValueChange = { v -> editor.editDebounced(data.withCriteriaEvidence(item.id, v)) },
                label = { Text("Evidence seen (documents, photos)") },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            ImproveWordingButton(
                text = data.criteriaEvidence(item.id),
                label = "Evidence seen: ${item.text}",
                readOnly = readOnly,
                onApply = { v -> editor.editNow(editor.data.withCriteriaEvidence(item.id, v)) },
            )
            OutlinedTextField(
                value = data.criteriaNote(item.id),
                onValueChange = { v -> editor.editDebounced(data.withCriteriaNote(item.id, v)) },
                label = { Text("Other remarks") },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            ImproveWordingButton(
                text = data.criteriaNote(item.id),
                label = item.text,
                readOnly = readOnly,
                onApply = { v -> editor.editNow(editor.data.withCriteriaNote(item.id, v)) },
            )
            RemarksPreview(item, data, readOnly, editor)
        }
    }
}

// one Annex-3 evidence point (an option) -- 3-way Seen/Not seen/N/A (tap-the-chosen-one-again
// clears it, same convention as AnswerButtons elsewhere in this app), a detail box that only
// shows once Seen is chosen AND the template defines a detail placeholder for this option, and a
// remark box collapsed behind "Add remark" until tapped or already non-blank.
@Composable
private fun OptionRow(itemId: String, option: CriteriaOption, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    val value = data.criteriaOptValue(itemId, option.id)
    val remark = data.criteriaOptRemark(itemId, option.id)
    var remarkOpen by remember(itemId, option.id) { mutableStateOf(remark.isNotBlank()) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(option.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            option.src.forEach { SourceTag(it) }
        }
        ThreeWayButtons(
            selected = value,
            readOnly = readOnly,
            onSelect = { v -> editor.editNow(data.withCriteriaOpt(itemId, option.id, v = v)) },
        )
        if (value == "seen" && !option.detail.isNullOrBlank()) {
            OutlinedTextField(
                value = data.criteriaOptDetail(itemId, option.id),
                onValueChange = { v -> editor.editDebounced(data.withCriteriaOpt(itemId, option.id, detail = v)) },
                placeholder = { Text(option.detail) },
                readOnly = readOnly,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (remarkOpen) {
            OutlinedTextField(
                value = remark,
                onValueChange = { v -> editor.editDebounced(data.withCriteriaOpt(itemId, option.id, remark = v)) },
                label = { Text("Remark on this point") },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            ImproveWordingButton(
                text = remark,
                label = option.label,
                readOnly = readOnly,
                onApply = { v -> editor.editNow(editor.data.withCriteriaOpt(itemId, option.id, remark = v)) },
            )
        } else if (!readOnly) {
            TextButton(onClick = { remarkOpen = true }) { Text("Add remark") }
        }
    }
}

// small "A3"/"CL"/"FC" source tag next to an option's label (spec §2's src list, mockup's
// ".obs-src" pills) -- purely informational, tells the officer which paper document this point
// came from.
@Composable
private fun SourceTag(src: String, modifier: Modifier = Modifier) {
    Text(
        src,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

// Seen / Not seen / N/A -- deliberately its own small control (not AnswerButtons, which is typed
// to a template AnswerOption+tone list) since a criteria option's 3 states are a fixed shape the
// template never varies.
@Composable
private fun ThreeWayButtons(selected: String, readOnly: Boolean, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val tones = LocalToneColors.current
    val choices = listOf("seen" to "Seen", "not" to "Not seen", "na" to "N/A")
    val toneFor = mapOf("seen" to tones.yes, "not" to tones.no, "na" to tones.na)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (id, label) ->
            val isOn = id == selected
            val bg = if (isOn) toneFor.getValue(id) else MaterialTheme.colorScheme.surface
            val fg = if (isOn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.5.dp, if (isOn) bg else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .then(if (readOnly) Modifier else Modifier.clickable { onSelect(if (isOn) "" else id) }),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = fg, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1) }
        }
    }
}

@Composable
private fun RemarksPreview(item: CriteriaItem, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    var editing by remember(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Remarks preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!readOnly && printedRemarks(item, data).isNotEmpty()) {
                TextButton(onClick = { editing = true }) { Text("Edit") }
            }
        }
        RemarksBullets(item, data)
    }
    if (editing) RemarksEditDialog(item, editor, onDismiss = { editing = false })
}

// printed bullets + a note when a hand edit no longer matches the answers
@Composable
private fun RemarksBullets(item: CriteriaItem, data: ReportData) {
    val bullets = printedRemarks(item, data)
    if (bullets.isEmpty()) {
        Text(
            "Nothing yet. Mark an option to add a sentence.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    bullets.forEach { bullet ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(bullet, style = MaterialTheme.typography.bodySmall)
        }
    }
    val edited = data.criteriaAiText(item.id)
    val stale = edited.isNotBlank() && data.criteriaAiSource(item.id) != buildRemarks(item, data).joinToString("\n")
    if (stale) {
        Text(
            "Answers changed after your edit, so the remarks were rebuilt. Tap Edit to write them again.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}


// hand edit of one criterion's final remarks, one point per line. Save stores it against the
// bullets it replaces (withCriteriaAi); Reset drops it and prints the built bullets again.
@Composable
private fun RemarksEditDialog(item: CriteriaItem, editor: ReportEditor, onDismiss: () -> Unit) {
    var text by remember(item.id) { mutableStateOf(printedRemarks(item, editor.data).joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit remarks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${item.no.orEmpty()} ${item.text}".trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("One point per line") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ImproveWordingButton(text = text, label = item.text, readOnly = false, onApply = { text = it })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val source = buildRemarks(item, editor.data).joinToString("\n")
                    editor.editNow(editor.data.withCriteriaAi(item.id, source, text.trim()))
                    onDismiss()
                },
                colors = actionButtonColors(),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (editor.data.criteriaAiText(item.id).isNotBlank()) {
                    TextButton(onClick = {
                        editor.editNow(editor.data.withCriteriaAiCleared(item.id))
                        onDismiss()
                    }) { Text("Reset") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// one section's remarks, criterion by criterion, each with Edit (end of section + Review)
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SectionRemarksDialog(sectionTitle: String, section: ReportSection, editor: ReportEditor, onDismiss: () -> Unit) {
    val items = section.blocks.filterIsInstance<ReportBlock.Criteria>().flatMap { it.items }.filter { !it.heading }
    var editingItem by remember { mutableStateOf<CriteriaItem?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Column { Text("Section remarks"); Text(sectionTitle, style = MaterialTheme.typography.labelMedium) } },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding() + 8.dp, 16.dp, innerPadding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${item.no.orEmpty()} ${item.text}".trim(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (!editor.readOnly && printedRemarks(item, editor.data).isNotEmpty()) {
                                    TextButton(onClick = { editingItem = item }) { Text("Edit") }
                                }
                            }
                            RemarksBullets(item, editor.data)
                        }
                    }
                }
            }
        }
    }
    editingItem?.let { item -> RemarksEditDialog(item, editor, onDismiss = { editingItem = null }) }
}
