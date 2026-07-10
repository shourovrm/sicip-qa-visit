package bd.sicip.qavisit

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.data.sync.SyncWorker
import bd.sicip.qavisit.settings.ThemePrefs
import bd.sicip.qavisit.ui.login.LoginScreen
import bd.sicip.qavisit.ui.shell.AppShell
import bd.sicip.qavisit.ui.theme.SicipTheme
import bd.sicip.qavisit.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = ThemePrefs(applicationContext)
        val sessionStore = SessionStore(applicationContext)
        val db = AppDb.get(applicationContext)
        setContent {
            val mode by themePrefs.mode.collectAsState(initial = ThemeMode.SYSTEM)
            // null until the datastore's first emission arrives; a real session flips
            // this straight to non-null so there's no separate splash state to manage.
            val session by sessionStore.session.collectAsState(initial = null)
            // API 33+ gates notifications behind a runtime prompt; ask once per cold start
            // and don't act on the answer -- a "no" just means the sync-done ping is silent.
            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* ignored */ }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            SicipTheme(themeMode = mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val currentSession = session
                    if (currentSession != null) {
                        // logged in -> start the recurring sync and kick off one right away
                        // so the very first screen after login isn't stuck on stale data.
                        LaunchedEffect(Unit) {
                            SyncWorker.schedulePeriodic(applicationContext)
                            SyncNow.enqueue(applicationContext)
                        }
                        AppShell(context = applicationContext, officerId = currentSession.userId)
                    } else {
                        LoginScreen(
                            sessionStore = sessionStore,
                            officerDao = db.officerDao(),
                            onLoggedIn = {},
                        )
                    }
                }
            }
        }
    }
}
