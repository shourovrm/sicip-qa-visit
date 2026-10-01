// a card field with a template `suggest` list (TMS courses/batches/trainees, shared equipment):
// free text with a dropdown of matches. picking a trainee also fills the card's empty phone.
// no suggestions yet (not linked, signed out, offline) -> the plain field editor as before.
package bd.sicip.qavisit.ui.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.SUGGEST_EQUIPMENT
import bd.sicip.qavisit.domain.report.SUGGEST_TMS_TRAINEE
import bd.sicip.qavisit.domain.report.suggestionsFor
import bd.sicip.qavisit.domain.report.tmsLink
import bd.sicip.qavisit.domain.report.traineeBatchOf
import bd.sicip.qavisit.domain.report.traineePhoneFor
import bd.sicip.qavisit.ui.common.PickerDropdown
import kotlinx.serialization.json.JsonObject

// a dropdown menu is not lazy: keep it to the best matches
private const val MAX_SHOWN = 40

@Composable
fun SuggestCardField(
    field: Field,
    block: ReportBlock.Cards,
    index: Int,
    card: JsonObject,
    editor: ReportEditor,
    ownOptions: List<String>,
    plainEditor: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources = editor.suggestions.sources
    if (field.suggest == SUGGEST_TMS_TRAINEE) {
        val batch = sources.catalog?.let { traineeBatchOf(card, it) }
        LaunchedEffect(batch?.id) {
            if (batch != null) editor.suggestions.loadTrainees(editor.data.tmsLink(), batch)
        }
    }
    val sourced = suggestionsFor(field, card, sources)
    val suggestions = (ownOptions + sourced).distinct()
    val sourceTag = if (field.suggest == SUGGEST_EQUIPMENT) "Shared" else "TMS"
    if (editor.readOnly || suggestions.isEmpty()) {
        plainEditor()
        return
    }
    val value = editor.data.cardField(block.key, index, field.key)
    val shown = suggestions.filter { it.contains(value.trim(), ignoreCase = true) }.take(MAX_SHOWN)
    PickerDropdown(
        label = field.label,
        options = shown,
        selected = value,
        onSelect = { picked ->
            var next = editor.data.withCardField(block.key, index, field.key, picked)
            if (field.suggest == SUGGEST_TMS_TRAINEE) {
                traineePhoneFor(block, card, picked, sources)?.let { phone -> next = next.withCardField(block.key, index, "phone", phone) }
            }
            editor.editNow(next)
        },
        onTextChange = { typed -> editor.editDebounced(editor.data.withCardField(block.key, index, field.key, typed)) },
        searchable = true,
        optionTag = { option -> if (option in sourced && option !in ownOptions) sourceTag else null },
        modifier = modifier,
    )
}
