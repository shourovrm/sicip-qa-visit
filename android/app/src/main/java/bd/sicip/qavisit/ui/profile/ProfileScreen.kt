// profile tab: who am I, my stats, theme picker, change password, visit-scores link, sync, log out.
package bd.sicip.qavisit.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.BuildConfig
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.data.remote.SupabaseClient
import bd.sicip.qavisit.data.remote.SupabaseException
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.data.sync.SyncStateStore
import bd.sicip.qavisit.domain.RankOfficer
import bd.sicip.qavisit.domain.VisitScore
import bd.sicip.qavisit.domain.rank
import bd.sicip.qavisit.settings.ThemePrefs
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.shell.syncChipText
import bd.sicip.qavisit.ui.theme.LocalStatusColors
import bd.sicip.qavisit.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

// public spreadsheet where visit-score categories/points are documented.
private const val VISIT_SCORES_URL = "https://docs.google.com/spreadsheets/d/1MIZ7tMjWHKnM-NuLcfimH__N9YNac-MIQKXe_oUTBuM"
private const val WEB_APP_URL = "https://sicip-qa-visit.shourovrm.workers.dev"
private const val GITHUB_URL = "https://github.com/shourovrm/sicip-qa-visit"

private data class MyStats(val points: Int = 0, val position: Int = 0, val officerCount: Int = 0, val visitCount: Int = 0)

@Composable
fun ProfileScreen(
    officerId: String,
    db: AppDb,
    themePrefs: ThemePrefs,
    sessionStore: SessionStore,
    client: SupabaseClient = SupabaseClient(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState = remember { SyncStateStore(context) }

    var officer by remember { mutableStateOf<Officer?>(null) }
    var stats by remember { mutableStateOf(MyStats()) }
    val session by sessionStore.session.collectAsState(initial = null)
    val themeMode by themePrefs.mode.collectAsState(initial = ThemeMode.SYSTEM)
    val lastSyncAt by syncState.lastSyncAt.collectAsState(initial = null)
    val lastError by syncState.lastError.collectAsState(initial = null)

    LaunchedEffect(officerId) {
        officer = db.officerDao().byId(officerId)
        val officers = db.officerDao().all()
        val scores = db.visitDao().all().map { VisitScore(it.officerId, it.category, it.deleted) }
        val ranked = rank(officers.map { RankOfficer(it.id, it.name) }, scores)
        val mine = ranked.firstOrNull { it.officerId == officerId }
        stats = MyStats(
            points = mine?.points ?: 0,
            position = mine?.position ?: 0,
            officerCount = officers.size,
            visitCount = db.visitDao().byOfficer(officerId).size,
        )
    }

    var showLogoutConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { ProfileHeaderCard(officer, session?.email) }
        item { StatsRow(stats) }
        item { ThemeCard(themeMode) { mode -> scope.launch { themePrefs.set(mode) } } }
        item { ChangePasswordCard(sessionStore, client) }
        item { VisitScoresRow { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(VISIT_SCORES_URL))) } }
        item { SyncCard(lastSyncAt, lastError) { SyncNow.enqueue(context) } }
        item { AboutCard { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
        item {
            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Log out") }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to log in again to keep using the app.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { sessionStore.clear() } }, // MainActivity's session collector flips to LoginScreen
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileHeaderCard(officer: Officer?, sessionEmail: String?) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                // weighted + ellipsized so a long officer name can never squeeze the pill offscreen.
                Text(
                    officer?.name ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                if (officer?.role == "admin") {
                    StatusPill("ADMIN", LocalStatusColors.current.onVisit)
                }
            }
            Text(
                sessionEmail ?: officer?.email ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsRow(stats: MyStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Points", "${stats.points}", Modifier.weight(1f))
        StatTile("Rank", "#${stats.position} / ${stats.officerCount}", Modifier.weight(1f))
        StatTile("Visits", "${stats.visitCount}", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThemeCard(mode: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = mode == entry,
                        onClick = { onPick(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                    ) { Text(entry.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
        }
    }
}

@Composable
private fun ChangePasswordCard(sessionStore: SessionStore, client: SupabaseClient) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            ) {
                Text("Change password", style = MaterialTheme.typography.titleMedium)
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }

            if (expanded) {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; message = null },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; message = null },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let {
                    Text(
                        it,
                        color = if (isError) MaterialTheme.colorScheme.error else LocalStatusColors.current.success.ink,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = {
                        loading = true
                        message = null
                        scope.launch {
                            try {
                                val trimmedNew = newPassword.trim()
                                val trimmedConfirm = confirmPassword.trim()
                                if (!passwordFormValid(trimmedNew, trimmedConfirm)) {
                                    throw IllegalArgumentException("Password must be at least 8 characters and match")
                                }
                                val session = sessionStore.ensureFresh(client) ?: throw IOException("offline")
                                client.changePassword(session.accessToken, trimmedNew)
                                isError = false
                                message = "Password updated"
                                newPassword = ""
                                confirmPassword = ""
                            } catch (e: Exception) {
                                isError = true
                                message = passwordErrorMessage(e)
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    shape = RoundedCornerShape(99),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onTertiary, strokeWidth = 2.dp)
                    } else {
                        Text("Update password")
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitScoresRow(onOpen: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        ) {
            Text("Visit scores sheet", style = MaterialTheme.typography.titleMedium)
            Text("Open ↗", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SyncCard(lastSyncAt: String?, lastError: String?, onSyncNow: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(syncChipText(lastSyncAt, lastError), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onSyncNow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                shape = RoundedCornerShape(99),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Sync now") }
        }
    }
}

@Composable
private fun AboutCard(onOpen: (String) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("SICIP QA Visit", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onOpen(GITHUB_URL) }) {
                    Icon(Icons.Filled.Code, contentDescription = "Source")
                }
            }
            Text(
                "Visit management for SICIP QA field officers — schedule visits, run tours, " +
                    "log travel, auto-score performance, generate TA/DA bills. Offline-first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Created by Riad Mashrub Shourov, Program Officer (QA), SICIP",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Web app",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onOpen(WEB_APP_URL) },
            )
        }
    }
}

// -- pure fns below, unit-testable without touching compose/android --

fun passwordFormValid(newPassword: String, confirmPassword: String): Boolean =
    newPassword.length >= 8 && newPassword == confirmPassword

fun passwordErrorMessage(t: Throwable): String = when {
    t is IllegalArgumentException -> t.message ?: "Invalid password"
    t is SupabaseException && t.code == 422 -> supabaseErrorMessage(t.body)
    t is IOException -> "No connection — try again"
    else -> "Something went wrong — try again"
}

// gotrue error bodies are json ({"msg"/"message"/"error_description"/"error": "..."}); fall
// back to the raw body if it doesn't parse or has none of those keys.
private fun supabaseErrorMessage(body: String): String = try {
    val obj = Json.parseToJsonElement(body).jsonObject
    (obj["msg"] ?: obj["message"] ?: obj["error_description"] ?: obj["error"])?.jsonPrimitive?.content ?: body
} catch (e: Exception) {
    body
}
