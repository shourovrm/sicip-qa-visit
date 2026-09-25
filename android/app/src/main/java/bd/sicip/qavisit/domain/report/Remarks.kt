// pure remarks builder for one `criteria` item (QA report spec §4) -- turns the officer's
// per-option picks/details/remarks plus the item's evidence/note boxes into the exact bullet
// list that prints in the output's Remarks column (pdf/QaReportHtml.kt) and the section screen's
// live "Remarks preview" (ui/reports/CriteriaBlocks.kt). Ported 1:1 onto both android and web off
// the SAME rules (spec §4's numbered steps, and shared/report-templates/fixtures/reference.py's
// `build_remarks` once the other agent adds it there) -- shared/report-templates/fixtures/
// remarks-1.json is the fixture both platforms' unit tests assert against (see
// RemarksFixtureTest.kt; a small local test template/fixture stands in for it until that shared
// file exists, per this agent's own build instructions).
//
// No android/compose dependency -- pure string/ReportData logic only.
package bd.sicip.qavisit.domain.report

// {...$...} appears at most once per sentence template (spec: "sentence then has one {...$...}
// group") -- when `detail` is non-blank the `$` inside the braces is replaced by it and the
// braces themselves drop away; when `detail` is blank the WHOLE {...} group drops away, braces
// and all (e.g. "Fire extinguishers are available{, last examined on $}." with a blank detail
// becomes "Fire extinguishers are available.").
private val DETAIL_GROUP = Regex("\\{([^}]*)\\}")

private fun resolveDetailGroup(sentenceTemplate: String, detail: String): String =
    DETAIL_GROUP.replace(sentenceTemplate) { match ->
        if (detail.isNotBlank()) match.groupValues[1].replace("$", detail) else ""
    }

// "" stays ""; any other string gets a "." appended unless it already ends with one of . ! ?
// (spec §4's ensureStop, shared by every bullet-building step below).
fun ensureStop(s: String): String {
    if (s.isEmpty()) return ""
    val last = s.last()
    return if (last == '.' || last == '!' || last == '?') s else "$s."
}

// one MARKED option -> its fixed sentence ({...$...} resolved) + typed remark, one string
// (reference.py option_text; shared by buildRemarks and Drafts.kt's componentNotes).
fun optionText(itemId: String, option: CriteriaOption, data: ReportData): String {
    val remark = ensureStop(data.criteriaOptRemark(itemId, option.id).trim())
    val sentenceTemplate = when (data.criteriaOptValue(itemId, option.id)) {
        "seen" -> option.seen
        "not" -> option.not
        "na" -> option.na
        else -> "" // unrecognised stored value -- treat as no fixed sentence, remark still prints
    }
    val detail = data.criteriaOptDetail(itemId, option.id).trim()
    val sentence = resolveDetailGroup(sentenceTemplate, detail).trim()
    return listOf(sentence, remark).filter { it.isNotBlank() }.joinToString(" ")
}

// spec §4's buildRemarks(item, entry) -> List<String>, in template option order:
// 1. for each option: v blank + a typed remark -> its own bullet "<short/label>: <remark>."
//    (an unmarked option with no remark contributes nothing); v non-blank -> that answer's fixed
//    sentence (with its {...$...} group resolved against the typed detail) followed by the typed
//    remark, joined by a space, as ONE bullet -- unless both parts end up blank, which can only
//    happen for a "na" answer whose template is "" and no remark was typed (spec: na's fixed
//    sentence is usually "").
// 2. a non-blank "Evidence seen" box -> one more bullet, "Evidence seen: <text>."
// 3. a non-blank "Other remarks" box -> one more bullet, the typed text as-is (period ensured).
fun buildRemarks(item: CriteriaItem, data: ReportData): List<String> {
    val bullets = mutableListOf<String>()

    item.options.forEach { option ->
        val value = data.criteriaOptValue(item.id, option.id)
        val remark = ensureStop(data.criteriaOptRemark(item.id, option.id).trim())

        if (value.isBlank()) {
            if (remark.isNotBlank()) {
                bullets += "${option.short ?: option.label}: $remark"
            }
            return@forEach
        }

        val joined = optionText(item.id, option, data)
        if (joined.isNotBlank()) bullets += joined
    }

    val evidence = data.criteriaEvidence(item.id).trim()
    if (evidence.isNotBlank()) bullets += "Evidence seen: ${ensureStop(evidence)}"

    val note = data.criteriaNote(item.id).trim()
    if (note.isNotBlank()) bullets += ensureStop(note)

    return bullets
}

// AI remarks guard (spec §6): every run of digits in the model's output must also appear
// somewhere in the input, or the result is discarded (a changed/invented number or date is
// exactly the kind of silent factual drift this feature must never introduce). A number that
// simply disappeared (the model dropped a fact) is NOT itself a guard failure -- spec only
// requires facts not to CHANGE, and "keep every fact" is a prompt instruction, not this guard's
// job to re-enforce.
private val NUMBER_TOKEN = Regex("\\d+")

fun numbersPreserved(input: String, output: String): Boolean {
    val inputNumbers = NUMBER_TOKEN.findAll(input).map { it.value }.toSet()
    val outputNumbers = NUMBER_TOKEN.findAll(output).map { it.value }.toSet()
    return outputNumbers.all { it in inputNumbers }
}

// spec §6's eligibility rule for the "AI remarks" sequential runner (ui/reports/CriteriaBlocks.kt):
// an item is sent only when it has bullets at all, AND (the officer typed something beyond just
// picking options, OR there are already 3+ bullets), AND it hasn't already been run against
// these EXACT bullets (ai.source == the fresh join means nothing changed since the last run).
fun criteriaNeedsAiRun(item: CriteriaItem, data: ReportData): Boolean {
    val bullets = buildRemarks(item, data)
    if (bullets.isEmpty()) return false
    val typedSomething = item.options.any { option ->
        data.criteriaOptDetail(item.id, option.id).isNotBlank() || data.criteriaOptRemark(item.id, option.id).isNotBlank()
    } || data.criteriaEvidence(item.id).isNotBlank() || data.criteriaNote(item.id).isNotBlank()
    if (!typedSomething && bullets.size < 3) return false
    return data.criteriaAiSource(item.id) != bullets.joinToString("\n")
}

// spec §4's printedRemarks(item, entry) -- what actually prints/shows: the AI-rewritten bullets
// IF they were made from exactly the CURRENT fixed-sentence bullets (ai.source ==
// bullets.joinToString("\n")), else the fresh fixed-sentence bullets themselves (an edit made
// since the AI run invalidates it automatically, no explicit "stale" flag needed).
fun printedRemarks(item: CriteriaItem, data: ReportData): List<String> {
    val bullets = buildRemarks(item, data)
    val aiText = data.criteriaAiText(item.id)
    val aiSource = data.criteriaAiSource(item.id)
    if (aiText.isNotBlank() && aiSource == bullets.joinToString("\n")) {
        return aiText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }
    return bullets
}
