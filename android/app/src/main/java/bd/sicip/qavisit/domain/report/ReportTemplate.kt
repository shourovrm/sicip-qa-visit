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

@Serializable
data class AnswerOption(val id: String, val label: String, val tone: String)

@Serializable
data class ChecklistItem(val id: String, val text: String)

@Serializable
data class FlagItem(val id: String, val text: String)

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
) {
    fun selectOptions(): List<String> =
        (options as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

    fun choiceOptions(): List<AnswerOption> =
        (options as? JsonArray)?.map { templateJson.decodeFromJsonElement(AnswerOption.serializer(), it) } ?: emptyList()

    // tone of a choice field's current value, e.g. for colouring the chosen segmented button
    // or (ReportProgress) deciding whether a "no" answer should flag the section.
    fun toneFor(value: String): String? = choiceOptions().find { it.id == value }?.tone
}

@Serializable
sealed class ReportBlock {
    @Serializable
    data class Fields(val fields: List<Field>) : ReportBlock()

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
        // block contributes 0 to progress totals; every card whose titleField is non-blank is
        // a free-text flag instead (section L's "other_flags") -- see ReportProgress's customFlags.
        val countsAsFlags: Boolean = false,
        val fields: List<Field>,
    ) : ReportBlock()

    @Serializable
    data class Flags(val items: List<FlagItem>) : ReportBlock()
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
            else -> error("unknown report block type: $type")
        }
}

@Serializable
data class ReportSection(
    val letter: String,
    val key: String,
    val short: String,
    val title: String,
    val note: String? = null,
    // excluded from ReportProgress's sectionsCounted/sectionsDone rollup (spec: "optional
    // section"); still computed and shown in `sections`, just tagged "Optional" in the UI.
    val optional: Boolean = false,
    val blocks: List<@Serializable(with = ReportBlockSerializer::class) ReportBlock>,
)

@Serializable
data class ReportTemplate(
    val id: String,
    val version: Int,
    val title: String,
    val program: String,
    val subtitle: String,
    val answers: List<AnswerOption>,
    val sections: List<ReportSection>,
)

private val templateJson = Json { ignoreUnknownKeys = true }

// pure parse, no Android dependency -- this is what the fixture-parity unit test calls
// directly on shared/report-templates/surprise-v1.json's text.
fun parseReportTemplate(text: String): ReportTemplate =
    templateJson.decodeFromString(ReportTemplate.serializer(), text)

// runtime entry point: reads the asset build.gradle.kts points at shared/report-templates/,
// so no copy of the template lives inside the app module.
fun loadReportTemplate(context: Context, assetName: String = "surprise-v1.json"): ReportTemplate =
    parseReportTemplate(context.assets.open(assetName).bufferedReader().use { it.readText() })
