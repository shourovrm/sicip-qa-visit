package bd.sicip.qavisit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import bd.sicip.qavisit.settings.ThemePrefs
import bd.sicip.qavisit.ui.theme.SicipTheme
import bd.sicip.qavisit.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePrefs = ThemePrefs(applicationContext)
        setContent {
            val mode by themePrefs.mode.collectAsState(initial = ThemeMode.SYSTEM)
            SicipTheme(themeMode = mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("SICIP QA Visit")
                    }
                }
            }
        }
    }
}
