// kotlinx-serialization model of shared/report-templates/<type>-v<n>.json -- the ONLY source
// of report questions (contract in docs/superpowers/specs/2026-09-24-reports.md). Both
// platforms load the same file; never hardcode questions in UI code.
//
// API for agent A2 (UI): parseReportTemplate(jsonText) is pure kotlin (no Context) and is what
// the fixture-parity unit test uses; loadReportTemplate(context, assetName) is the real runtime
// entry point, reading the asset that build.gradle.kts wires straight to shared/report-templates/
// (so "surprise-v1.json" is readable without copying the file into src/main/assets). Walk
// template.sections in order; each ReportSection.blocks is one of ReportBlock's four sealed
// subtypes (Fields/Checklist/Cards/Flags) -- `when` over it exhaustively, the compiler will
// flag a missed case if a fifth block type ever ships. Field.selectOptions()/choiceOptions()
// decode the polymorphic `options` array for kind=="select"/"choice" respectively;
// Field.toneFor(value) looks up a choice option's answer tone (used by ReportProgress's
// "flagged" rule and by the UI to colour segmented buttons).
package bd.sicip.qavisit.domain.report

import android.content.Context
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// tone defaults to "" (not a required column in every choice field) -- qa-v1.json's plain
// informational choice fields (e.g. section 1's "Signed with other organizations?" yes/na) carry
// no tone at all, unlike surprise-v1.json's checklist-style yes/no/partial/na answers which
// always do. ui/theme/Color.kt's forToneId and pdf/*.kt's TONE_COLOR both already fall back to a
// neutral colour for "" or any other unrecognised id, so this never renders as a crash or as a
// stray flagged-red.
@Serializable
data class AnswerOption(val id: String, val label: String, val tone: String = "")

// perCourse: with 2+ non-blank courses in section A, this item renders/answers ONE ROW PER
// COURSE instead of a single answer row (spec CHANGE SET 3) -- see ReportLinks.kt's
// syncPerCourse for how the per-course answers derive the item's overall `answer`.
@Serializable
data class ChecklistItem(val id: String, val text: String, val perCourse: Boolean = false)

@Serializable
data class FlagItem(val id: String, val text: String)

// one option under a `criteria` item (QA report spec §2/§3) -- src is a subset of ["A3","CL",
// "FC"] shown as small source tags in the UI; short (defaults to label at the call site, never
// here, so "missing" stays distinguishable from "explicitly blank") names the point in an
// unmarked-option remark bullet (domain/report/Remarks.kt's buildRemarks); seen/not/na are the
// fixed sentence templates for each 3-way answer, each with at most one `{...$...}` detail group
// (see Remarks.kt for how `$` is substituted or the whole group dropped).
@Serializable
data class CriteriaOption(
    val id: String,
    val label: String,
    val short: String? = null,
    val src: List<String> = emptyList(),
    val detail: String? = null,
    val seen: String = "",
    val not: String = "",
    val na: String = "",
)

// one row of a `criteria` block: either a heading (no options, `no` + `text` print across the
// row, e.g. "1." Physical resources...) or a real criterion with >= 1 option (e.g. "c)" Safety
// and fire prevention...). `evidence` is the Annex-3 evidence text shown as a UI hint ("Evidence
// (Annex-3): ...") -- not to be confused with a report's own per-item evidence-seen text, which
// is ReportData.criteriaEvidence(itemId), an officer-typed value.
@Serializable
data class CriteriaItem(
    val id: String,
    val no: String? = null,
    val text: String,
    val heading: Boolean = false,
    val evidence: String? = null,
    val options: List<CriteriaOption> = emptyList(),
)

@Serializable
data class CardsCompare(val fields: List<String>, val message: String)

// a `cards` block with `linkFrom` derives its rows from another cards block instead of the
// officer adding/removing them directly (e.g. section C's attendance cards follow section A's
// courses) -- see domain/report/ReportLinks.kt's syncLinks, ported from reference.py's
// sync_links. `cards` is the source block's key; `fields` are the source keys copied onto each
// linked target card verbatim.
@Serializable
data class CardsLink(val cards: String, val fields: List<String>)

// kind ∈ text | longtext | number | date | time | phone | select | choice | courseRef.
// `options` is raw JSON because its shape depends on kind (select = list of strings, choice =
// list of {id,label,tone}) -- kotlinx-serialization can't express that as one typed property,
// so the two accessor methods below decode it lazily per the caller's known kind. `optionsFrom`
// is courseRef's own source: the key of a `cards` block whose entries become this field's
// dropdown options (see ReportBlocks.kt's FieldEditor "courseRef" case).
@Serializable
data class Field(
    val key: String,
    val label: String,
    val kind: String,
    val required: Boolean = false,
    val prefill: String? = null, // institute | association | visit_date | officers
    val placeholder: String? = null,
    val options: JsonElement? = null,
    val optionsFrom: String? = null,
    // "Improve wording" button shows under this field's longtext editor (ui/reports/
    // ImproveWording.kt) -- set per-field in the template json, never inferred from `kind`, so
    // address/officers-style longtext fields can opt out (spec: never those two).
    val rewrite: Boolean = false,
    // QA s11/s12 question: key of the criteria section its No answers feed (Drafts.kt feedbackLines)
    val component: String? = null,
    // "weaknesses" (s14 findings) | "plan" (s15 recommendations): which draft button it gets
    val draftFrom: String? = null,
) {
    fun selectOptions(): List<String> =
        (options as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

    fun choiceOptions(): List<AnswerOption> =
        (options as? JsonArray)?.map { templateJson.decodeFromJsonElement(AnswerOption.serializer(), it) } ?: emptyList()

    // tone of a choice field's current value, e.g. for colouring the chosen segmented button
    // or (ReportProgress) deciding whether a "no" answer should flag the section.
    fun toneFor(value: String): String? = choiceOptions().find { it.id == value }?.tone
}

// QA s13 row: component name + its criteria section + the two top-level text field keys
@Serializable
data class ComponentPair(val component: String, val source: String, val strength: String, val weakness: String)

@Serializable
sealed class ReportBlock {
    // `heading` (qa-v1.json's section 1 only, e.g. "1.20 Contract/MoU Information") labels one
    // fields block as its own numbered sub-section, same idea as Cards/Checklist's own `heading` --
    // surprise-v1.json's fields blocks never set it, so it stays null there.
    // pairs: QA s13 only -- one strengths/weaknesses group per component (Drafts.kt)
    @Serializable
    data class Fields(val fields: List<Field>, val heading: String? = null, val pairs: List<ComponentPair> = emptyList()) : ReportBlock()

    @Serializable
    data class Checklist(val key: String, val heading: String? = null, val items: List<ChecklistItem>) : ReportBlock()

    @Serializable
    data class Cards(
        val key: String,
        val itemLabel: String,
        val start: Int,
        val titleField: String,
        val heading: String? = null,
        val note: String? = null,
        val compare: CardsCompare? = null,
        val linkFrom: CardsLink? = null,
        // "tabs" (section I interviews): render one card at a time behind a per-course tab
        // strip instead of a stacked list -- see ReportBlocks.kt's InterviewTabsView.
        val display: String? = null,
        // block contributes 0 to progress totals; every card whose titleField is non-blank is
        // a free-text flag instead (section L's "other_flags") -- see ReportProgress's customFlags.
        val countsAsFlags: Boolean = false,
        // QA s11/s12: no name field; old cards may still hold `name` -- never show or print it
        val anonymous: Boolean = false,
        // "weaknesses" (QA s16 plan): cards rebuilt from s13 weaknesses (Drafts.kt planCards)
        val draftFrom: String? = null,
        val fields: List<Field>,
    ) : ReportBlock()

    @Serializable
    data class Flags(val items: List<FlagItem>) : ReportBlock()

    // QA report Annex-3 criteria table (spec §2/§3): one table per section (or per sub-heading,
    // e.g. section 8's own "8.1" block) -- `intro` is the italic description line under the
    // Annex-3 heading, printed once above the table (pdf/QaReportHtml.kt) and as a note in the UI.
    @Serializable
    data class Criteria(val key: String, val intro: String? = null, val items: List<CriteriaItem>) : ReportBlock()
}

// picks the sealed subtype from the JSON's "type" discriminator ("fields"/"checklist"/
// "cards"/"flags") -- the field itself isn't modelled on any subtype, ignoreUnknownKeys drops
// it once selectDeserializer has read it.
private object ReportBlockSerializer : JsonContentPolymorphicSerializer<ReportBlock>(ReportBlock::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ReportBlock> =
        when (val type = element.jsonObject["type"]?.jsonPrimitive?.content) {
            "fields" -> ReportBlock.Fields.serializer()
            "checklist" -> ReportBlock.Checklist.serializer()
            "cards" -> ReportBlock.Cards.serializer()
            "flags" -> ReportBlock.Flags.serializer()
            "criteria" -> ReportBlock.Criteria.serializer()
            else -> error("unknown report block type: $type")
        }
}

@Serializable
data class ReportSection(
    // surprise-v1.json's own scheme (A, B, C...) -- qa-v1.json uses `number` ("1".."15") instead
    // (spec §2/§8), so `letter` is blank there; UI code should prefer `number` when non-blank,
    // falling back to `letter` (see ui/reports/ReportHub.kt's sectionBadge).
    val letter: String = "",
    val key: String,
    val short: String,
    val title: String,
    val note: String? = null,
    // qa-v1.json only (spec §2): "1".."15", the Annex-3 section number shown as the hub/section
    // badge instead of `letter`.
    val number: String? = null,
    // qa-v1.json only (spec §2/§8): "profile" (section 1) | "criteria" (2-10) | "conclusions"
    // (11-15) -- ui/reports/ReportHub.kt groups the hub list by this when present, with the
    // labels "Centre profile" / "Quality criteria" / "Feedback & conclusions". null (surprise
    // template) means an ungrouped flat list, same as before this field existed.
    val group: String? = null,
    // excluded from ReportProgress's sectionsCounted/sectionsDone rollup (spec: "optional
    // section"); still computed and shown in `sections`, just tagged "Optional" in the UI.
    val optional: Boolean = false,
    val blocks: List<@Serializable(with = ReportBlockSerializer::class) ReportBlock>,
) {
    // "8" (qa) or "A" (surprise) -- the one badge/number string the UI should ever print, so
    // call sites never have to choose between `number` and `letter` themselves.
    val badge: String get() = number?.takeIf { it.isNotBlank() } ?: letter
}

@Serializable
data class ReportTemplate(
    val id: String,
    val version: Int,
    val title: String,
    val program: String,
    val subtitle: String = "",
    // qa-v1.json only (spec §2): "QA visit" / "Surprise visit" -- the New-report button/type-chip
    // label (spec: "No 'Annex-3' text in UI"). surprise-v1.json falls back to
    // ui/reports/ReportStart.kt's own reportTypeLabel() for its equivalent copy, so this stays
    // null there rather than duplicating "Surprise visit" in two places.
    val short: String? = null,
    // qa-v1.json only (spec §2): "Annex-3" -- printed top-right on the PDF/Word output
    // (pdf/QaReportHtml.kt), never shown anywhere in the app UI itself (spec: "No 'Annex-3' text
    // in UI").
    val annex: String? = null,
    // qa-v1.json only (spec §1): "surprise" | "qa" -- which visits.visit_type this template
    // renders a report for (ui/reports/ReportStart.kt's reportTypeForVisit picks the template by
    // matching this, not by the report row's own `type` string, though in practice the two are
    // kept equal).
    val visitType: String? = null,
    // visit purposes this report can be written for ("Monitoring Visit"); empty = any visit
    val purposes: List<String> = emptyList(),
    // qa-v1.json has no checklist blocks (its 3-way seen/not/na criteria answers are a separate
    // fixed shape, not a `choice`-style AnswerOption list) -- defaults to empty so its absence
    // from that template's JSON doesn't fail to parse.
    val answers: List<AnswerOption> = emptyList(),
    val sections: List<ReportSection>,
)

fun ReportTemplate.allowsPurpose(purpose: String): Boolean = purposes.isEmpty() || purpose in purposes

private val templateJson = Json { ignoreUnknownKeys = true }

// pure parse, no Android dependency -- this is what the fixture-parity unit test calls
// directly on shared/report-templates/surprise-v1.json's text.
fun parseReportTemplate(text: String): ReportTemplate =
    templateJson.decodeFromString(ReportTemplate.serializer(), text)

// runtime entry point: reads the asset build.gradle.kts points at shared/report-templates/,
// so no copy of the template lives inside the app module.
fun loadReportTemplate(context: Context, assetName: String = "surprise-v1.json"): ReportTemplate =
    parseReportTemplate(context.assets.open(assetName).bufferedReader().use { it.readText() })
