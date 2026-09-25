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
// "AI remarks" (spec §6) is CriteriaAiRemarksDialog below -- a full-screen Dialog (no new nav
// route needed) that runs domain/report/Remarks.kt's criteriaNeedsAiRun-eligible items of ONE
// section sequentially, one Cloudflare Worker request at a time, showing before/after per item
// and letting the officer accept or keep the original; a 429 waits data/remote/RewriteClient.kt's
// parsed (or default 10s) retry_after and resumes the SAME item instead of giving up on it.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.remote.RewriteClient
import bd.sicip.qavisit.data.remote.RewriteResult
import bd.sicip.qavisit.data.remote.SupabaseClient
import bd.sicip.qavisit.domain.report.CriteriaItem
import bd.sicip.qavisit.domain.report.CriteriaOption
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.buildRemarks
import bd.sicip.qavisit.domain.report.criteriaNeedsAiRun
import bd.sicip.qavisit.domain.report.numbersPreserved
import bd.sicip.qavisit.domain.report.printedRemarks
import bd.sicip.qavisit.settings.RewriteModelPrefs
import bd.sicip.qavisit.ui.theme.LocalToneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            var evidenceOpen by remember(item.id) { mutableStateOf(data.criteriaEvidence(item.id).isNotBlank()) }
            OutlinedTextField(
                value = data.criteriaEvidence(item.id),
                onValueChange = { v -> editor.editDebounced(data.withCriteriaEvidence(item.id, v)) },
                label = { Text("Evidence seen (documents, photos)") },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = data.criteriaNote(item.id),
                onValueChange = { v -> editor.editDebounced(data.withCriteriaNote(item.id, v)) },
                label = { Text("Other remarks") },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
            RemarksPreview(item, data)
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
private fun RemarksPreview(item: CriteriaItem, data: ReportData) {
    val bullets = printedRemarks(item, data)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Remarks preview",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bullets.isEmpty()) {
            Text(
                "Nothing yet. Mark an option to add a sentence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            bullets.forEach { bullet ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(bullet, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ============================== AI remarks (spec §6) ==============================

private enum class AiItemStatus { PENDING, RUNNING, WAITING_RETRY, READY, ACCEPTED, KEPT, SKIPPED }

private data class AiItemState(
    val item: CriteriaItem,
    val status: AiItemStatus,
    val before: List<String> = emptyList(),
    val after: String = "",
    val note: String = "",
)

// eligible items across every `criteria` block of one section, in template order -- computed
// once when the dialog opens, not re-derived as items are accepted/kept (an item accepted a
// moment ago must not vanish off the list mid-run just because its own ai.source now matches).
private fun eligibleCriteriaItems(section: ReportSection, data: ReportData): List<CriteriaItem> =
    section.blocks.filterIsInstance<ReportBlock.Criteria>()
        .flatMap { it.items }
        .filter { !it.heading && criteriaNeedsAiRun(it, data) }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CriteriaAiRemarksDialog(
    sectionTitle: String,
    section: ReportSection,
    editor: ReportEditor,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // cancelled automatically when this composable leaves composition (onDismiss's caller stops
    // rendering it) -- spec §6: "stop on leaving" the AI remarks screen.
    val scope = rememberCoroutineScope()
    val sessionStore = remember { SessionStore(context) }
    val supabaseClient = remember { SupabaseClient() }
    val rewriteClient = remember { RewriteClient() }
    val rewriteModelPrefs = remember { RewriteModelPrefs(context) }
    val modelKey by rewriteModelPrefs.selectedKey.collectAsState(initial = null)

    // snapshot at open time (see eligibleCriteriaItems's own comment) -- editor.data still drives
    // every actual read/write below, this list only decides WHICH items this run covers.
    val items = remember { eligibleCriteriaItems(section, editor.data) }
    var states by remember { mutableStateOf(items.map { AiItemState(it, AiItemStatus.PENDING) }) }
    var running by remember { mutableStateOf(false) }

    fun updateState(index: Int, transform: (AiItemState) -> AiItemState) {
        states = states.toMutableList().also { it[index] = transform(it[index]) }
    }

    // runs items[startIndex..] one request at a time, pausing (not stopping) at the first item
    // that needs the officer's accept/keep tap -- resumeFrom lets "Use AI version"/"Keep
    // original" continue the queue instead of restarting it.
    fun runFrom(startIndex: Int) {
        if (running) return
        running = true
        scope.launch {
            var index = startIndex
            while (index < states.size) {
                val current = states[index]
                if (current.status != AiItemStatus.PENDING && current.status != AiItemStatus.WAITING_RETRY) {
                    index++
                    continue
                }
                updateState(index) { it.copy(status = AiItemStatus.RUNNING) }
                val bullets = buildRemarks(current.item, editor.data)
                val joined = bullets.joinToString("\n")
                val session = sessionStore.ensureFresh(supabaseClient)
                if (session == null) {
                    updateState(index) { it.copy(status = AiItemStatus.SKIPPED, note = "Session expired") }
                    index++
                    continue
                }
                var attempts = 0
                var resolved = false
                while (!resolved && attempts < 5) {
                    attempts++
                    when (val result = rewriteClient.rewrite(joined, current.item.text, session.accessToken, modelKey, mode = "remarks")) {
                        is RewriteResult.Ok -> {
                            if (numbersPreserved(joined, result.text)) {
                                updateState(index) { it.copy(status = AiItemStatus.READY, before = bullets, after = result.text) }
                            } else {
                                updateState(index) { it.copy(status = AiItemStatus.SKIPPED, note = "Kept original: numbers changed") }
                            }
                            resolved = true
                        }
                        is RewriteResult.Err -> {
                            val retryAfter = result.retryAfterSeconds
                            if (retryAfter != null) {
                                updateState(index) { it.copy(status = AiItemStatus.WAITING_RETRY, note = "Waiting ${retryAfter}s") }
                                delay(retryAfter * 1000L)
                            } else {
                                updateState(index) { it.copy(status = AiItemStatus.SKIPPED, note = result.message) }
                                resolved = true
                            }
                        }
                    }
                }
                if (!resolved) updateState(index) { it.copy(status = AiItemStatus.SKIPPED, note = "Gave up after repeated 429s") }
                // READY items pause the queue here -- the officer's tap on Use AI version/Keep
                // original (below) calls runFrom(index + 1) itself.
                if (states[index].status == AiItemStatus.READY) break
                index++
            }
            running = false
        }
    }

    LaunchedEffect(Unit) { runFrom(0) }

    val readyIndex = states.indexOfFirst { it.status == AiItemStatus.READY }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = { Column { Text("AI remarks"); Text(sectionTitle, style = MaterialTheme.typography.labelMedium) } },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            },
        ) { innerPadding ->
            if (items.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
                    Text(
                        "Nothing to rewrite here -- every criterion is either empty or fixed sentences only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Scaffold
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding() + 8.dp, 16.dp, innerPadding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            states.forEach { s -> AiRow(s) }
                        }
                    }
                }
                if (readyIndex >= 0) {
                    val ready = states[readyIndex]
                    item {
                        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${ready.item.no.orEmpty()} ${ready.item.text}".trim(), style = MaterialTheme.typography.titleSmall)
                                Text("FROM OPTIONS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ready.before.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                                Text("AI VERSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ready.after.split("\n").filter { it.isNotBlank() }.forEach { Text("• ${it.trim()}", style = MaterialTheme.typography.bodySmall) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = {
                                            updateState(readyIndex) { it.copy(status = AiItemStatus.KEPT) }
                                            runFrom(readyIndex + 1)
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                    ) { Text("Keep original") }
                                    Button(
                                        onClick = {
                                            editor.editNow(editor.data.withCriteriaAi(ready.item.id, ready.before.joinToString("\n"), ready.after))
                                            updateState(readyIndex) { it.copy(status = AiItemStatus.ACCEPTED) }
                                            runFrom(readyIndex + 1)
                                        },
                                        colors = actionButtonColors(),
                                        modifier = Modifier.weight(1f).height(48.dp),
                                    ) { Text("Use AI version") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiRow(state: AiItemState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (state.status) {
            AiItemStatus.ACCEPTED -> Icon(Icons.Filled.Check, contentDescription = null, tint = LocalToneColors.current.yes)
            AiItemStatus.RUNNING -> CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            AiItemStatus.READY -> Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            else -> Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("${state.item.no.orEmpty()} ${state.item.text}".trim(), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            val subtitle = when (state.status) {
                AiItemStatus.PENDING -> "Waiting"
                AiItemStatus.RUNNING -> "Writing…"
                AiItemStatus.WAITING_RETRY -> state.note
                AiItemStatus.READY -> "Ready to check"
                AiItemStatus.ACCEPTED -> "Accepted"
                AiItemStatus.KEPT -> "Kept original"
                AiItemStatus.SKIPPED -> state.note.ifBlank { "Skipped" }
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
