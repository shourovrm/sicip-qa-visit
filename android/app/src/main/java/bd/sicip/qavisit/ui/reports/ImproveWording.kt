// "Improve wording" button under a report text field -- sends ONLY that field's current text
// (never the whole report, never address/officers) to our rewrite endpoint (data/remote/
// RewriteClient.kt), shows the suggestion in a bottom sheet, and lets the officer pick "Keep
// mine" or "Use this". Never auto-replaces and never blocks saving -- the field itself keeps
// working (typing, debounced autosave) whether or not this button is ever tapped.
package bd.sicip.qavisit.ui.reports

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.remote.RewriteClient
import bd.sicip.qavisit.data.remote.RewriteResult
import bd.sicip.qavisit.data.remote.SupabaseClient
import kotlinx.coroutines.launch

// plain framework check -- this build ships no-GMS, so no play-services connectivity helper.
// NET_CAPABILITY_VALIDATED means the network actually reaches the internet, not just "connected"
// to e.g. a captive-portal wifi with no route out.
private fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

// text = the field's current value, label = short description sent alongside it (the template's
// own question text for that field -- helps the model, adds no extra report content). onApply is
// the caller's immediate-edit write (editor.editNow(...)) so the result is normalized/persisted
// exactly like any other discrete edit.
@Composable
fun ImproveWordingButton(
    text: String,
    label: String,
    readOnly: Boolean,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (readOnly || text.isBlank()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionStore = remember { SessionStore(context) }
    val supabaseClient = remember { SupabaseClient() }
    val rewriteClient = remember { RewriteClient() }

    var loading by remember { mutableStateOf(false) }
    var error by remember(text) { mutableStateOf<String?>(null) } // a fresh edit clears a stale error
    var suggestion by remember { mutableStateOf<String?>(null) }

    // re-checked on every recomposition (e.g. right before the tap handler runs) rather than
    // cached once -- there is no NetworkCallback listener here, this is a point-in-time check.
    val online = isOnline(context)

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    error = null
                    loading = true
                    scope.launch {
                        val session = sessionStore.ensureFresh(supabaseClient)
                        val result = if (session == null) {
                            RewriteResult.Err("Session expired — sign in again")
                        } else {
                            rewriteClient.rewrite(text, label, session.accessToken)
                        }
                        loading = false
                        when (result) {
                            is RewriteResult.Ok -> suggestion = result.text
                            is RewriteResult.Err -> error = result.message
                        }
                    }
                },
                enabled = online && !loading,
            ) { Text("Improve wording") }
            if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            if (!online) Text("Offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }

    val current = suggestion
    if (current != null) {
        ImproveWordingSheet(
            original = text,
            suggested = current,
            onKeepMine = { suggestion = null },
            onUseThis = {
                onApply(current)
                suggestion = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImproveWordingSheet(
    original: String,
    suggested: String,
    onKeepMine: () -> Unit,
    onUseThis: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onKeepMine, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Improve wording", style = MaterialTheme.typography.titleLarge)

            Text("YOURS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(original, style = MaterialTheme.typography.bodyMedium)

            Text("SUGGESTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(suggested, style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onKeepMine, modifier = Modifier.weight(1f).height(48.dp)) { Text("Keep mine") }
                Button(onClick = onUseThis, colors = actionButtonColors(), modifier = Modifier.weight(1f).height(48.dp)) { Text("Use this") }
            }
        }
    }
}
