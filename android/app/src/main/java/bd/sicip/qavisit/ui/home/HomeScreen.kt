// home tab: dashboard (points/rank/visits + this-month line) on top, ACTIVE TOUR hero when a
// tour is running, then UPCOMING scheduled visits -- each with its own small "Start" button
// that opens the start-tour sheet pre-checked for that visit. FAB schedules a new visit.
package bd.sicip.qavisit.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.remote.SupabaseClient
import bd.sicip.qavisit.domain.dayNumber
import bd.sicip.qavisit.domain.primaryVisit
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.theme.LocalStatusColors

@Composable
fun HomeScreen(
    officerId: String,
    db: AppDb,
    sessionStore: SessionStore,
    onOpenTrip: (String) -> Unit,
    onLogVisit: (tripId: String, hasPrimary: Boolean) -> Unit,
    onFinishTrip: (String) -> Unit,
    onStartTrip: (visitId: String?) -> Unit,
    onScheduleVisit: () -> Unit,
    onEditVisit: (String) -> Unit,
    client: SupabaseClient = SupabaseClient(),
) {
    val vm = remember(officerId, db) { HomeViewModel(officerId, db, sessionStore, client) }
    val state by vm.state.collectAsState(initial = HomeUiState())
    val updateNotice by vm.updateNotice.collectAsState()
    val context = LocalContext.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScheduleVisit,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Schedule visit") }
        },
    ) { innerPadding ->
        if (state.loading) return@Scaffold

        LazyColumn(
            contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding(), 16.dp, innerPadding.calculateBottomPadding() + 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            updateNotice?.let { notice ->
                item {
                    UpdateBanner(
                        version = notice.latestVersion,
                        onGet = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notice.apkUrl))) },
                        onDismiss = { vm.dismissUpdateNotice() },
                    )
                }
            }

            item {
                Dashboard(
                    myPoints = state.myPoints,
                    myRank = state.myRank,
                    officerCount = state.officerCount,
                    visitCount = state.myVisitCount,
                    monthVisitCount = state.monthVisitCount,
                    monthPoints = state.monthPoints,
                )
            }

            val trip = state.activeTrip
            if (trip != null) {
                item {
                    ActiveTripHero(
                        dayNumber = dayNumber(trip.startedAt),
                        primary = primaryVisit(state.activeTripVisits) { it.isAdditional },
                        startedAt = trip.startedAt,
                        visitCount = state.activeTripVisits.size,
                        onOpen = { onOpenTrip(trip.id) },
                        onLogVisit = { onLogVisit(trip.id, state.activeTripVisits.any { !it.isAdditional }) },
                        onFinishTrip = { onFinishTrip(trip.id) },
                    )
                }
            }

            if (state.upcoming.isNotEmpty()) {
                item {
                    Text(
                        "UPCOMING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.upcoming) { visit ->
                    UpcomingVisitCard(visit, onClick = { onEditVisit(visit.id) }, onStart = { onStartTrip(visit.id) })
                }
            } else if (trip == null) {
                item { TeachingCard() }
            }
        }
    }
}

@Composable
private fun Dashboard(
    myPoints: Int,
    myRank: Int,
    officerCount: Int,
    visitCount: Int,
    monthVisitCount: Int,
    monthPoints: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PointsRow(myPoints = myPoints, myRank = myRank, officerCount = officerCount, visitCount = visitCount)
        Text(
            "This month: $monthVisitCount visits · $monthPoints pts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateBanner(version: String, onGet: () -> Unit, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Update available — v$version",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onGet, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)) {
                    Text("Get")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TeachingCard() {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Schedule a visit to begin", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap + to schedule your first visit, then start a tour from it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActiveTripHero(
    dayNumber: Int,
    primary: Visit?,
    startedAt: String,
    visitCount: Int,
    onOpen: () -> Unit,
    onLogVisit: () -> Unit,
    onFinishTrip: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "ACTIVE TOUR · DAY $dayNumber",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = primary?.let { "${it.purpose} · ${it.institute}" } ?: "Ad-hoc tour",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                "Started ${startedAt.take(10)} · $visitCount visits",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedHeroButton("Log visit", onLogVisit, Modifier.weight(1f))
                Button(
                    onClick = onFinishTrip,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    shape = RoundedCornerShape(99),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("End tour") }
            }
        }
    }
}

@Composable
private fun OutlinedHeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = RoundedCornerShape(99),
        modifier = modifier.height(48.dp),
    ) { Text(text) }
}

@Composable
private fun PointsRow(myPoints: Int, myRank: Int, officerCount: Int, visitCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        SnippetCard("My points", "$myPoints", Modifier.weight(1f))
        SnippetCard("Rank", "#$myRank / $officerCount", Modifier.weight(1f))
        SnippetCard("Visits", "$visitCount", Modifier.weight(1f))
    }
}

@Composable
private fun SnippetCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpcomingVisitCard(visit: Visit, onClick: () -> Unit, onStart: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // weighted + ellipsized so a long institute name can never squeeze the pill offscreen.
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(visit.institute, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${visit.purpose} · ${visit.district} · ${visit.startDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    StatusPill("SCHEDULED", LocalStatusColors.current.onVisit)
                }
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(99),
                    modifier = Modifier.height(32.dp),
                ) { Text("Start", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
