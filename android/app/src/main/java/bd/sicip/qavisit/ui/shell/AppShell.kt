// post-login shell: navy app bar (title only), orange-tinted bottom nav, 5 tabs.
package bd.sicip.qavisit.ui.shell

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.settings.ThemePrefs
import bd.sicip.qavisit.ui.bill.BillScreen
import bd.sicip.qavisit.ui.home.HomeScreen
import bd.sicip.qavisit.ui.home.StartTrip
import bd.sicip.qavisit.ui.home.TripScreen
import bd.sicip.qavisit.ui.leaves.LeaveForm
import bd.sicip.qavisit.ui.leaves.LeavesScreen
import bd.sicip.qavisit.ui.profile.ProfileScreen
import bd.sicip.qavisit.ui.team.TeamScreen
import bd.sicip.qavisit.ui.visits.VisitForm
import bd.sicip.qavisit.ui.visits.VisitsScreen
import java.time.Duration
import java.time.Instant

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("home", "Home", Icons.Filled.Home),
    NavItem("team", "Team", Icons.Filled.Groups),
    NavItem("visits", "Visits", Icons.Filled.Checklist),
    NavItem("leaves", "Leaves", Icons.Filled.EventBusy),
    NavItem("profile", "Profile", Icons.Filled.Person),
)

// TopAppBar is the experimental bit here (nav-bar items are stable); this
// screen-level shell composable is the natural opt-in boundary.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(context: Context, officerId: String) {
    val db = remember { AppDb.get(context) }
    val sessionStore = remember { SessionStore(context) }
    val themePrefs = remember { ThemePrefs(context) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: NAV_ITEMS.first().route
    val currentTitle = NAV_ITEMS.firstOrNull { it.route == currentRoute }?.label ?: "SICIP QA Visit"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTitle) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                NAV_ITEMS.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                // one instance of each tab, back button unwinds to home instead
                                // of stacking every tab visit -- standard bottom-nav behavior.
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.tertiary,
                            selectedTextColor = MaterialTheme.colorScheme.tertiary,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NAV_ITEMS.first().route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    officerId = officerId,
                    db = db,
                    sessionStore = sessionStore,
                    onOpenTrip = { tripId -> navController.navigate("trip/$tripId") },
                    onFinishTrip = { tripId -> navController.navigate("trip/$tripId?action=finish") },
                    onStartTrip = { visitId ->
                        navController.navigate(if (visitId != null) "start_trip?visitId=$visitId" else "start_trip")
                    },
                    onScheduleVisit = { navController.navigate("visit_form") },
                    onEditVisit = { visitId -> navController.navigate("visit_form?visitId=$visitId") },
                )
            }
            composable("team") { TeamScreen(officerId = officerId, db = db) }
            composable("visits") {
                VisitsScreen(
                    officerId = officerId,
                    db = db,
                    onEditVisit = { visitId -> navController.navigate("visit_form?visitId=$visitId") },
                    onOpenBill = { navController.navigate("bill") },
                )
            }
            composable("bill") {
                BillScreen(officerId = officerId, db = db, onDone = { navController.popBackStack() })
            }
            composable("leaves") {
                LeavesScreen(
                    officerId = officerId,
                    db = db,
                    onAddLeave = { navController.navigate("leave_form") },
                    onEditLeave = { leaveId -> navController.navigate("leave_form?leaveId=$leaveId") },
                )
            }
            composable("profile") {
                ProfileScreen(officerId = officerId, db = db, themePrefs = themePrefs, sessionStore = sessionStore)
            }

            composable(
                "leave_form?leaveId={leaveId}",
                arguments = listOf(
                    navArgument("leaveId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                LeaveForm(
                    officerId = officerId,
                    db = db,
                    leaveId = entry.arguments?.getString("leaveId"),
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                "visit_form?visitId={visitId}&tripId={tripId}&additional={additional}",
                arguments = listOf(
                    navArgument("visitId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("tripId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("additional") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                VisitForm(
                    officerId = officerId,
                    visitDao = db.visitDao(),
                    visitId = entry.arguments?.getString("visitId"),
                    tripId = entry.arguments?.getString("tripId"),
                    forceAdditional = entry.arguments?.getBoolean("additional") ?: false,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                "start_trip?visitId={visitId}",
                arguments = listOf(
                    navArgument("visitId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                StartTrip(
                    officerId = officerId,
                    db = db,
                    preselectedVisitId = entry.arguments?.getString("visitId"),
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                "trip/{tripId}?action={action}",
                arguments = listOf(
                    navArgument("tripId") { type = NavType.StringType },
                    navArgument("action") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val tripId = entry.arguments?.getString("tripId") ?: return@composable
                TripScreen(
                    tripId = tripId,
                    initialAction = entry.arguments?.getString("action"),
                    db = db,
                    onScheduleAdhocVisit = { id, hasPrimary ->
                        navController.navigate("visit_form?tripId=$id&additional=$hasPrimary")
                    },
                    onEditVisit = { visitId -> navController.navigate("visit_form?visitId=$visitId") },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

// pure so it's unit-testable without touching compose/android.
// ponytail: "syncing" only covers the pre-first-sync window (no lastSyncAt/lastError yet).
// a manual retry tap while already synced won't flip this back to "Syncing…" -- add a
// WorkManager work-info flow if live feedback on manual retries is ever needed.
fun syncChipText(lastSyncAt: String?, lastError: String?, now: Instant = Instant.now()): String = when {
    lastError != null -> "⚠ Offline"
    lastSyncAt != null -> "✓ Synced ${relativeTime(lastSyncAt, now)}"
    else -> "Syncing…"
}

fun relativeTime(iso: String, now: Instant = Instant.now()): String {
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return "just now"
    val seconds = Duration.between(then, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h"
        else -> "${seconds / 86400}d"
    }
}
