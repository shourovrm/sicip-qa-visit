// QA conclusions drafts: one Worker call per draft (modes strengths/findings/plan, label "") with
// the deterministic Drafts.kt fallback on ANY failure -- offline, no session, http error, parse
// null, number guard. Old Worker ignores the mode -> parse fails -> fallback (spec §3).
package bd.sicip.qavisit.ui.reports

import android.content.Context
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.remote.DRAFT_MAX_CHARS
import bd.sicip.qavisit.data.remote.RewriteClient
import bd.sicip.qavisit.data.remote.RewriteResult
import bd.sicip.qavisit.data.remote.SupabaseClient
import bd.sicip.qavisit.domain.report.ComponentNotes
import bd.sicip.qavisit.domain.report.StrengthsDraft
import bd.sicip.qavisit.domain.report.cleanLines
import bd.sicip.qavisit.domain.report.fallbackDraft
import bd.sicip.qavisit.domain.report.numberedText
import bd.sicip.qavisit.domain.report.numbersPreserved
import bd.sicip.qavisit.domain.report.parseNumbered
import bd.sicip.qavisit.domain.report.parseStrengthsAnswer
import bd.sicip.qavisit.domain.report.planCards
import bd.sicip.qavisit.domain.report.strengthsPromptText
import bd.sicip.qavisit.settings.RewriteModelPrefs
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import java.util.UUID

const val DRAFT_FALLBACK_NOTICE = "AI unavailable — drafted from your marks"

// value + whether it came from the fallback (officer sees DRAFT_FALLBACK_NOTICE then)
data class Drafted<T>(val value: T, val fromMarks: Boolean)

class QaDraftAi(private val context: Context) {
    private val sessionStore = SessionStore(context)
    private val supabaseClient = SupabaseClient()
    private val rewriteClient = RewriteClient()
    private val modelPrefs = RewriteModelPrefs(context)

    // raw model answer, or null on any failure incl. a number the input never had
    private suspend fun ask(mode: String, text: String): String? {
        if (text.isBlank() || text.length > DRAFT_MAX_CHARS || !isOnline(context)) return null
        val session = sessionStore.ensureFresh(supabaseClient) ?: return null
        val modelKey = modelPrefs.selectedKey.first()
        val result = rewriteClient.rewrite(text, "", session.accessToken, modelKey, mode)
        val answer = (result as? RewriteResult.Ok)?.text ?: return null
        return answer.takeIf { numbersPreserved(text, it) }
    }

    suspend fun component(notes: ComponentNotes): Drafted<StrengthsDraft> {
        val parsed = ask("strengths", strengthsPromptText(notes))?.let { parseStrengthsAnswer(it) }
        return if (parsed != null) Drafted(parsed, false) else Drafted(fallbackDraft(notes), true)
    }

    suspend fun findings(weaknesses: List<String>): Drafted<List<String>> {
        val lines = ask("findings", weaknesses.joinToString("\n") { "- $it" })
            ?.let { cleanLines(it) }
            ?.takeIf { it.isNotEmpty() }
        return if (lines != null) Drafted(lines, false) else Drafted(weaknesses, true)
    }

    suspend fun plan(weaknesses: List<String>, existing: List<JsonObject>): Drafted<List<JsonObject>> {
        val actions = ask("plan", numberedText(weaknesses))?.let { parseNumbered(it, weaknesses.size) }
        val cards = planCards(weaknesses, actions, existing) { UUID.randomUUID().toString() }
        return Drafted(cards, actions == null)
    }
}
