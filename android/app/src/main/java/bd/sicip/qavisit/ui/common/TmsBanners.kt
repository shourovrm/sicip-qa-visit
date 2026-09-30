// top banners for TMS state, same look as Home's update banner: sign-in failed (everyone) and
// API changed (admins only). the update banner stays first; these render right after it.
package bd.sicip.qavisit.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.remote.SupabaseClient
import bd.sicip.qavisit.data.tms.TmsLinkHealth
import bd.sicip.qavisit.data.tms.TmsServices
import bd.sicip.qavisit.data.tms.fetchTmsAlertText
import bd.sicip.qavisit.data.tms.fetchTmsApiBroken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// collects auth state and (admins) refreshes the alert on every resume; silent on error.
@Composable
fun rememberTmsBannerState(officerId: String, db: AppDb): TmsBannerState {
    val context = LocalContext.current
    val auth = remember { TmsServices.get(context).auth }
    val authState by auth.state.collectAsState()
    var isAdmin by remember(officerId) { mutableStateOf(false) }
    var adminAlert by remember { mutableStateOf<String?>(null) }
    val linkBroken by TmsLinkHealth.broken.collectAsState()

    LaunchedEffect(officerId) { isAdmin = db.officerDao().byId(officerId)?.role == "admin" }
    LifecycleResumeEffect(isAdmin) {
        val job = CoroutineScope(Dispatchers.Main).launch {
            val client = SupabaseClient()
            val sessions = SessionStore(context.applicationContext)
            if (isAdmin) {
                adminAlert = fetchTmsAlertText(client, sessions)
            } else {
                fetchTmsApiBroken(client, sessions)?.let { TmsLinkHealth.set(it) }
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    return tmsBannerState(authState, isAdmin, adminAlert, linkBroken)
}

@Composable
fun TmsBanners(state: TmsBannerState, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.signInFailure?.let { TopBanner(it, "Open settings", onOpenSettings) }
        state.adminAlert?.let { TopBanner(it) }
        state.linkBroken?.let { TopBanner(it) }
    }
}

@Composable
private fun TopBanner(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                ) { Text(actionLabel) }
            }
        }
    }
}
