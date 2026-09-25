// generic renderers for the four template block types (fields/checklist/cards/flags) -- driven
// purely by the template's own data (labels, kinds, options), never a hardcoded question, so a
// future template version renders correctly without a UI code change. Shared by
// ui/reports/ReportSectionScreen.kt (one block per row on its own section screen); every editor
// here writes through the given ReportEditor (ui/reports/ReportEditing.kt), never touches Room
// directly, and calls editNow for discrete picks (segmented answer, dropdown, date/time picker,
// card add/remove, flag tick) or editDebounced for free-typed text (spec: debounce ~400ms).
package bd.sicip.qavisit.ui.reports

import java.time.LocalTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Warning
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import bd.sicip.qavisit.domain.report.ChecklistItem
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.cardCompareMismatch
import bd.sicip.qavisit.domain.report.cardCountedProgress
import bd.sicip.qavisit.domain.report.courseIds
import bd.sicip.qavisit.ui.common.PickerDropdown
import bd.sicip.qavisit.ui.common.TimeField
import bd.sicip.qavisit.ui.common.showDatePicker
import bd.sicip.qavisit.ui.theme.LocalToneColors
import bd.sicip.qavisit.ui.theme.forToneId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// N answer buttons across a row (4 for a checklist item's yes/no/partial/na, 2-3 for a choice
// field like overall_rating/attendance's identity "result") -- neutral outline until chosen,
// filled with the option's tone color once chosen, tapping the already-chosen one again clears
// it back to blank.
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

// courseRef dropdown options: "<course> · <batch>" (or just <course> if batch is blank) for
// every entry in the source cards block (field.optionsFrom), in that block's own order --
// spec's new field kind (D identity's "Course / batch"). Keeping an existing value that no
// longer matches any option isn't this function's job: PickerDropdown already shows whatever
// text it's given regardless of whether it's in `options`, which is exactly "keeps an existing
// value even if not in the list" (spec).
fun courseRefOptions(data: ReportData, field: Field): List<String> {
    val sourceKey = field.optionsFrom ?: return emptyList()
    return data.cards(sourceKey).mapNotNull { card ->
        val course = card["course"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val batch = card["batch"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        when {
            course.isBlank() -> null
            batch.isBlank() -> course
            else -> "$course · $batch"
        }
    }
}

// one template Field rendered by its `kind` -- used both for a top-level `fields` block and for
// each field inside a `cards` entry (identical rendering rules either way). `courseOptions` only
// matters for kind=="courseRef" (see courseRefOptions above); every other kind ignores it.
@Composable
fun FieldEditor(
    field: Field,
    value: String,
    readOnly: Boolean,
    onImmediate: (String) -> Unit,
    onDebounced: (String) -> Unit,
    modifier: Modifier = Modifier,
    courseOptions: List<String> = emptyList(),
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

        "courseRef" -> PickerDropdown(
            label = field.label,
            options = courseOptions,
            selected = value,
            onSelect = onImmediate,
            onTextChange = onImmediate,
            searchable = true,
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
            // blank must LOOK blank: a default 12:00 reads as an answer the officer never gave
            if (value.isBlank()) {
                OutlinedButton(
                    onClick = { onImmediate(LocalTime.now().withSecond(0).withNano(0).toString() + ":00") },
                    enabled = !readOnly,
                    modifier = Modifier.height(48.dp),
                ) { Text("Set to now") }
            } else {
                TimeField(value = value, onChange = onImmediate)
            }
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

        "longtext" -> Column(modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = onDebounced,
                label = { Text(field.label) },
                readOnly = readOnly,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            // template opts a longtext field in per-field (spec: never address/officers) --
            // see domain/report/ReportTemplate.kt's Field.rewrite.
            if (field.rewrite) {
                ImproveWordingButton(text = value, label = field.label, readOnly = readOnly, onApply = onImmediate)
            }
        }

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
        block.heading?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        block.fields.forEach { field ->
            FieldEditor(
                field = field,
                value = data.field(field.key),
                readOnly = readOnly,
                onImmediate = { v -> editor.editNow(data.withField(field.key, v)) },
                onDebounced = { v -> editor.editDebounced(data.withField(field.key, v)) },
                courseOptions = if (field.kind == "courseRef") courseRefOptions(data, field) else emptyList(),
            )
        }
    }
}

// one checklist item: numbered question, 4 answer buttons (template.answers, always in that
// order regardless of how many options a `choice` field elsewhere has), remarks collapsed
// behind an "Add remarks" link until tapped or already non-blank. A `perCourse` item (spec
// CHANGE SET 3) switches to one answer row per today's course -- see coursesForPerCourseRows
// below for the 2+-courses threshold -- with a single shared remarks box underneath.
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
    // computed once per recomposition of the whole block, not per item -- every perCourse item
    // in this block (and there can be several) shares the same "is per-course active" answer.
    val perCourseIds = courseIds(data).takeIf { it.size >= 2 } ?: emptyList()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        block.heading?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        block.items.forEachIndexed { i, item ->
            val number = startIndex + i
            if (item.perCourse && perCourseIds.isNotEmpty()) {
                PerCourseChecklistItemCard(item, number, perCourseIds, answers, data, readOnly, editor)
            } else {
                SingleAnswerChecklistItemCard(item, number, answers, data, readOnly, editor)
            }
        }
    }
}

@Composable
private fun SingleAnswerChecklistItemCard(
    item: ChecklistItem,
    number: Int,
    answers: List<AnswerOption>,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
) {
    val answer = data.checkAnswer(item.id)
    val remarks = data.checkRemarks(item.id)
    var remarksOpen by remember(item.id) { mutableStateOf(remarks.isNotBlank()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$number. ${item.text}", style = MaterialTheme.typography.bodyLarge)
            AnswerButtons(
                options = answers,
                selected = answer,
                readOnly = readOnly,
                onSelect = { v -> editor.editNow(data.withCheck(item.id, answer = v)) },
            )
            ChecklistRemarksField(item, remarks, remarksOpen, readOnly, data, editor) { remarksOpen = true }
        }
    }
}

// spec: "such an item shows ONE ROW PER COURSE ... plus one shared remarks box; a small 'Per
// course' tag next to the question. Writes go to checks.<item>.courses.<courseCardId>" --
// normalize() (ReportLinks.kt's syncPerCourse) derives the item's overall answer from these on
// the next pass, this view never computes or writes that derived value itself.
@Composable
private fun PerCourseChecklistItemCard(
    item: ChecklistItem,
    number: Int,
    courseCardIds: List<String>,
    answers: List<AnswerOption>,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
) {
    val coursesByid = remember(data) { data.cards("courses").associateBy { it["_id"]?.jsonPrimitive?.contentOrNull } }
    val perCourseAnswers = data.checkCourses(item.id)
    val remarks = data.checkRemarks(item.id)
    var remarksOpen by remember(item.id) { mutableStateOf(remarks.isNotBlank()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$number. ${item.text}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                PerCourseTag()
            }
            courseCardIds.forEach { courseId ->
                val course = coursesByid[courseId]
                val courseLabel = course?.get("course")?.jsonPrimitive?.contentOrNull.orEmpty()
                val batch = course?.get("batch")?.jsonPrimitive?.contentOrNull.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column {
                        Text(courseLabel, style = MaterialTheme.typography.bodyMedium)
                        if (batch.isNotBlank()) {
                            Text("Batch $batch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AnswerButtons(
                        options = answers,
                        selected = perCourseAnswers[courseId] ?: "",
                        readOnly = readOnly,
                        onSelect = { v -> editor.editNow(data.withCheckCourse(item.id, courseId, v)) },
                    )
                }
            }
            ChecklistRemarksField(item, remarks, remarksOpen, readOnly, data, editor) { remarksOpen = true }
        }
    }
}

// the one remarks box, shared by both the single-answer and per-course checklist item cards --
// spec: perCourse items still get "one shared remarks box", not one per course.
@Composable
private fun ChecklistRemarksField(
    item: ChecklistItem,
    remarks: String,
    remarksOpen: Boolean,
    readOnly: Boolean,
    data: ReportData,
    editor: ReportEditor,
    onOpen: () -> Unit,
) {
    if (remarksOpen) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = remarks,
                onValueChange = { v -> editor.editDebounced(data.withCheck(item.id, remarks = v)) },
                label = { Text("Remarks") },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            // every checklist remarks box gets the button, unconditionally (spec) -- unlike
            // longtext template fields there is no per-item opt-out here.
            ImproveWordingButton(
                text = remarks,
                label = item.text,
                readOnly = readOnly,
                onApply = { v -> editor.editNow(data.withCheck(item.id, remarks = v)) },
            )
        }
    } else if (!readOnly) {
        TextButton(onClick = onOpen) { Text("Add remarks") }
    }
}

// small "PER COURSE" tag next to a perCourse item's question (spec).
@Composable
fun PerCourseTag(modifier: Modifier = Modifier) {
    Text(
        "PER COURSE",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// the section whose OWN cards block a `linkFrom` block follows (e.g. "courses" lives in
// section A) -- used for the empty-state "Add courses in section A" jump, generic over any
// future linkFrom pairing instead of hardcoding "section A".
private fun sourceSectionAndBlock(template: ReportTemplate, sourceCardsKey: String): Pair<ReportSection, ReportBlock.Cards>? {
    template.sections.forEach { section ->
        section.blocks.filterIsInstance<ReportBlock.Cards>().forEach { block ->
            if (block.key == sourceCardsKey) return section to block
        }
    }
    return null
}

@Composable
fun CardsBlockView(
    block: ReportBlock.Cards,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
    template: ReportTemplate,
    onOpenSection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = data.cards(block.key)
    val link = block.linkFrom
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // a section can hold several card blocks (A: persons + courses) -- the heading says which
        block.heading?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
        block.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (block.display == "tabs") {
            // section I's interviews: one course tab at a time instead of a stacked list --
            // spec CHANGE SET 3.
            if (entries.isNotEmpty()) InterviewTabsView(block, entries, template.answers, data, readOnly, editor)
        } else {
            entries.forEachIndexed { index, entry ->
                CardEntryView(block, entry, index, data, readOnly, editor)
            }
        }
        when {
            // linked cards are entirely derived (syncLinks) -- officer never adds/removes one
            // directly, spec: "linked cards have no Add/Remove".
            link != null && entries.isEmpty() -> {
                val source = sourceSectionAndBlock(template, link.cards)
                Text(
                    if (source != null) "Add ${source.second.itemLabel.lowercase()}s in section ${source.first.letter}" else "Nothing to show yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (source != null && !readOnly) {
                    TextButton(onClick = { onOpenSection(source.first.key) }) {
                        Text("Go to section ${source.first.letter}")
                    }
                }
            }
            link == null && !readOnly -> {
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
}

// section I: a course tab strip (one tab per linked interview card, label = course · batch from
// linkedCardHeader, "x/y answered" subtitle, green once that card's own counted fields are all
// answered) showing one course's card at a time -- spec CHANGE SET 3's `"display": "tabs"`.
@Composable
private fun InterviewTabsView(
    block: ReportBlock.Cards,
    entries: List<JsonObject>,
    templateAnswers: List<AnswerOption>,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
) {
    var selectedIndex by remember(block.key) { mutableStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, entries.size - 1)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                val (answered, total) = cardCountedProgress(block, entry)
                InterviewTab(
                    title = linkedCardHeader(block, entry),
                    subtitle = "$answered/$total answered",
                    selected = index == safeIndex,
                    done = total > 0 && answered == total,
                    onClick = { selectedIndex = index },
                )
            }
        }
        val selectedEntry = entries.getOrNull(safeIndex)
        if (selectedEntry != null) {
            InterviewCardFields(block, selectedEntry, safeIndex, templateAnswers, data, readOnly, editor)
        }
    }
}

@Composable
private fun InterviewTab(title: String, subtitle: String, selected: Boolean, done: Boolean, onClick: () -> Unit) {
    val tones = LocalToneColors.current
    val bg = when {
        done -> tones.yes
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        done -> Color.White
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.5.dp, if (selected || done) bg else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 2)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

// q1..q7 (a choice field whose options are exactly the template's own yes/no/partial/na answer
// set) render like a checklist answer row; every other field on the card (trainees_interviewed,
// tech_topic, tech_result, feedback) renders through the normal FieldEditor. course/batch are
// skipped entirely -- already shown read-only as the tab's own label.
private fun looksLikeAnswerChoice(field: Field, templateAnswers: List<AnswerOption>): Boolean =
    field.kind == "choice" && field.choiceOptions().map { it.id }.toSet() == templateAnswers.map { it.id }.toSet()

@Composable
private fun InterviewCardFields(
    block: ReportBlock.Cards,
    entry: JsonObject,
    index: Int,
    templateAnswers: List<AnswerOption>,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
) {
    val linkedKeys = block.linkFrom?.fields?.toSet() ?: emptySet()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        block.fields.forEach { field ->
            if (field.key in linkedKeys) return@forEach
            val value = data.cardField(block.key, index, field.key)
            if (looksLikeAnswerChoice(field, templateAnswers)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(field.label, style = MaterialTheme.typography.bodyLarge)
                        AnswerButtons(
                            options = field.choiceOptions(),
                            selected = value,
                            readOnly = readOnly,
                            onSelect = { v -> editor.editNow(data.withCardField(block.key, index, field.key, v)) },
                        )
                    }
                }
            } else {
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
}

// linked fields (e.g. course/batch) render read-only plain text in the header instead of an
// editable FieldEditor row, e.g. "Welding (SMAW) · Batch 07" -- spec's example, matched here for
// the course+batch shape specifically; any other linkFrom field set falls back to a plain join.
private fun linkedCardHeader(block: ReportBlock.Cards, entry: JsonObject): String {
    val link = block.linkFrom ?: return "${block.itemLabel}"
    val course = entry["course"]?.jsonPrimitive?.contentOrNull
    val batch = entry["batch"]?.jsonPrimitive?.contentOrNull
    if (course != null && batch != null) {
        return if (batch.isBlank()) course else "$course · Batch $batch"
    }
    return link.fields.mapNotNull { key -> entry[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }
        .joinToString(" · ")
        .ifBlank { block.itemLabel }
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
    val link = block.linkFrom
    val isLinked = link != null
    val mismatch = block.compare?.let { cardCompareMismatch(it, entry) } ?: false
    // a filled card needs a confirm before removal; an empty seeded one goes straight away
    var confirmRemove by remember { mutableStateOf(false) }
    val hasContent = entry.values.any { it.toString().trim('"').isNotBlank() }
    // linked fields show read-only in the header (linkedCardHeader) instead of as an editable
    // row further down -- everything else on the card still edits normally.
    val editableFields = if (link != null) block.fields.filterNot { it.key in link.fields } else block.fields
    // mismatch = one warning line under the header, not a red card: a tinted card body
    // hurts contrast outdoors (DESIGN.md sunlight rule)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isLinked) linkedCardHeader(block, entry) else "${block.itemLabel} ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                // linked cards have no Remove either (spec) -- their lifecycle follows the
                // source card in section A, not a tap here.
                if (!readOnly && !isLinked) {
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
            @Composable
            fun CardField(field: Field, modifier: Modifier = Modifier) = FieldEditor(
                field = field,
                value = data.cardField(block.key, index, field.key),
                readOnly = readOnly,
                onImmediate = { v -> editor.editNow(data.withCardField(block.key, index, field.key, v)) },
                onDebounced = { v -> editor.editDebounced(data.withCardField(block.key, index, field.key, v)) },
                modifier = modifier,
                courseOptions = if (field.kind == "courseRef") courseRefOptions(data, field) else emptyList(),
            )
            // number fields two per row (total | female, register | TMS): halves the scroll per course
            pairNumberFields(editableFields).forEach { group ->
                if (group.size == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { CardField(it, Modifier.weight(1f)) }
                    }
                } else {
                    CardField(group.single())
                }
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
fun ReportBlockView(
    block: ReportBlock,
    answers: List<AnswerOption>,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
    template: ReportTemplate,
    onOpenSection: (String) -> Unit,
) {
    when (block) {
        is ReportBlock.Fields -> FieldsBlockView(block, data, readOnly, editor)
        is ReportBlock.Checklist -> ChecklistBlockView(block, answers, data, readOnly, editor)
        is ReportBlock.Cards -> CardsBlockView(block, data, readOnly, editor, template, onOpenSection)
        is ReportBlock.Flags -> FlagsBlockView(block, data, readOnly, editor)
        is ReportBlock.Criteria -> CriteriaBlockView(block, data, readOnly, editor)
    }
}

// small "Optional" tag (spec: optional sections show a tag on the hub row / section header /
// chip) -- reused by ReportHub.kt's SectionRow and ReportSectionScreen.kt's top bar title.
@Composable
fun OptionalTag(modifier: Modifier = Modifier) {
    Text(
        "OPTIONAL",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// orange = actions only (DESIGN.md); the primary button of every report action bar
@Composable
fun actionButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.tertiary,
    contentColor = MaterialTheme.colorScheme.onTertiary,
)

// consecutive number fields grouped in pairs; everything else stays one per row
private fun pairNumberFields(fields: List<Field>): List<List<Field>> {
    val groups = mutableListOf<List<Field>>()
    var i = 0
    while (i < fields.size) {
        val pairable = fields[i].kind == "number" && i + 1 < fields.size && fields[i + 1].kind == "number"
        if (pairable) {
            groups += listOf(fields[i], fields[i + 1])
            i += 2
        } else {
            groups += listOf(fields[i])
            i += 1
        }
    }
    return groups
}
