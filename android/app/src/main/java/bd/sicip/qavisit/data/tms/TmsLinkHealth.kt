// process-wide "TMS link is broken" flag: set locally when this phone sees an API change, and
// overwritten by the server's answer (tms_api_broken) on app start/resume.
package bd.sicip.qavisit.data.tms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TmsLinkHealth {
    private val _broken = MutableStateFlow(false)
    val broken: StateFlow<Boolean> = _broken

    fun markBroken() { _broken.value = true }

    fun set(broken: Boolean) { _broken.value = broken }
}
