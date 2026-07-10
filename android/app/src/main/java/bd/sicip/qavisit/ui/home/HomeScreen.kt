// home tab: active-trip hero (direction B navy card) or a start-trip prompt, upcoming
// scheduled visits, and a 3-up points/rank/visits snippet row. FAB schedules a new visit.
package bd.sicip.qavisit.ui.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.domain.formatFare
import bd.sicip.qavisit.domain.dayNumber
import bd.sicip.qavisit.domain.primaryVisit
import bd.sicip.qavisit.ui.common.StatusPill
import bd.sicip.qavisit.ui.theme.LocalStatusColors

@Composable
fun HomeScreen(
    officerId: String,
    db: AppDb,
    onOpenTrip: (String) -> Unit,
    onAddLeg: (String) -> Unit,
    onLogVisit: (tripId: String, hasPrimary: Boolean) -> Unit,
    onFinishTrip: (String) -> Unit,
    onStartTrip: () -> Unit,
    onScheduleVisit: () -> Unit,
    onEditVisit: (String) -> Unit,
) {
    val vm = remember(officerId, db) { HomeViewModel(officerId, db) }
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

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
            item {
                val trip = state.activeTrip
                if (trip == null) {
                    NoActiveTripCard(onStartTrip)
                } else {
                    ActiveTripHero(
                        dayNumber = dayNumber(trip.startedAt),
                        primary = primaryVisit(state.activeTripVisits) { it.isAdditional },
                        startedAt = trip.startedAt,
                        visitCount = state.activeTripVisits.size,
                        legCount = state.activeTripLegs.size,
                        fareSum = state.activeTripLegs.sumOf { it.fare },
                        onOpen = { onOpenTrip(trip.id) },
                        onAddLeg = { onAddLeg(trip.id) },
                        onLogVisit = { onLogVisit(trip.id, state.activeTripVisits.any { !it.isAdditional }) },
                        onFinishTrip = { onFinishTrip(trip.id) },
                    )
                }
            }

            item { PointsRow(myPoints = state.myPoints, myRank = state.myRank, officerCount = state.officerCount, visitCount = state.myVisitCount) }

            if (state.upcoming.isNotEmpty()) {
                item {
                    Text(
                        "UPCOMING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.upcoming) { visit ->
                    UpcomingVisitCard(visit, onClick = { onEditVisit(visit.id) })
                }
            }
        }
    }
}

@Composable
private fun NoActiveTripCard(onStartTrip: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No active trip", style = MaterialTheme.typography.titleMedium)
            Text(
                "Start a trip when you head out -- attach any scheduled visits, log legs as you go.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onStartTrip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                shape = RoundedCornerShape(99),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Start trip") }
        }
    }
}

@Composable
private fun ActiveTripHero(
    dayNumber: Int,
    primary: Visit?,
    startedAt: String,
    visitCount: Int,
    legCount: Int,
    fareSum: Double,
    onOpen: () -> Unit,
    onAddLeg: () -> Unit,
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
                "ACTIVE TRIP · DAY $dayNumber",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = primary?.let { "${it.purpose} · ${it.institute}" } ?: "Ad-hoc trip",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                "Started ${startedAt.take(10)} · $visitCount visits · $legCount legs · ${formatFare(fareSum)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedHeroButton("Add leg", onAddLeg, Modifier.weight(1f))
                OutlinedHeroButton("Log visit", onLogVisit, Modifier.weight(1f))
                Button(
                    onClick = onFinishTrip,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    shape = RoundedCornerShape(99),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Finish trip") }
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
private fun UpcomingVisitCard(visit: Visit, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(visit.institute, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${visit.purpose} · ${visit.district} · ${visit.startDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(contentAlignment = Alignment.Center) {
                StatusPill("SCHEDULED", LocalStatusColors.current.onVisit)
            }
        }
    }
}
