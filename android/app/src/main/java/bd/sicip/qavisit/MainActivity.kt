package bd.sicip.qavisit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.data.sync.SyncWorker
import bd.sicip.qavisit.settings.ThemePrefs
import bd.sicip.qavisit.ui.login.LoginScreen
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
            SicipTheme(themeMode = mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (session != null) {
                        // logged in -> start the recurring sync and kick off one right away
                        // so the very first screen after login isn't stuck on stale data.
                        LaunchedEffect(Unit) {
                            SyncWorker.schedulePeriodic(applicationContext)
                            SyncNow.enqueue(applicationContext)
                        }
                        // shell (bottom nav, home) comes in the next task
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("SICIP QA Visit")
                        }
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
