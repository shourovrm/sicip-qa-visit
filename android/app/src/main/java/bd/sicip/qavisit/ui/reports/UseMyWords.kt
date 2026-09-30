// "Use my words": after an AI draft/rewrite replaced something, what it replaced is offered
// back -- only while the drafted value is still untouched (a later hand edit makes it moot).
package bd.sicip.qavisit.ui.reports

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class DraftUndo<T> {
    var previous by mutableStateOf<T?>(null)
        private set
    private var applied by mutableStateOf<T?>(null)

    fun record(before: T, after: T) {
        previous = before
        applied = after
    }

    fun canUndo(current: T): Boolean = previous != null && current == applied

    fun clear() {
        previous = null
        applied = null
    }
}

@Composable
fun <T> rememberDraftUndo(): DraftUndo<T> = remember { DraftUndo() }

@Composable
fun <T> UseMyWordsButton(undo: DraftUndo<T>, current: T, onRestore: (T) -> Unit) {
    val previous = undo.previous
    if (previous == null || !undo.canUndo(current)) return
    TextButton(onClick = {
        onRestore(previous)
        undo.clear()
    }) { Text("Use my words") }
}
