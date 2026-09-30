// lets any screen jump to the Profile tab's TMS account card without threading callbacks through
// every route. AppShell provides the real one.
package bd.sicip.qavisit.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

val LocalOpenTmsSettings = staticCompositionLocalOf<() -> Unit> { {} }
