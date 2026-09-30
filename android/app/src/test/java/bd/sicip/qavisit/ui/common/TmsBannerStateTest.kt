package bd.sicip.qavisit.ui.common

import bd.sicip.qavisit.data.tms.TmsAuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TmsBannerStateTest {
    private val loggedIn = TmsAuthState.LoggedIn("Rina", 0)

    @Test
    fun `sign-in failure shows the TMS message`() {
        val state = tmsBannerState(TmsAuthState.Failed("no connection"), isAdmin = false, adminAlertText = null, linkBroken = true)
        assertEquals("TMS sign-in failed: no connection", state.signInFailure)
        assertNull(state.linkBroken) // not logged in: no broken-link notice
    }

    @Test
    fun `non-admin logged in sees the broken link notice only`() {
        val state = tmsBannerState(loggedIn, isAdmin = false, adminAlertText = "detail", linkBroken = true)
        assertEquals(TMS_LINK_BROKEN_TEXT, state.linkBroken)
        assertNull(state.adminAlert)
    }

    @Test
    fun `admin sees the detailed alert instead of the notice`() {
        val state = tmsBannerState(loggedIn, isAdmin = true, adminAlertText = "detail", linkBroken = true)
        assertEquals("detail", state.adminAlert)
        assertNull(state.linkBroken)
    }

    @Test
    fun `logged out with nothing wrong shows nothing`() {
        assertFalse(tmsBannerState(TmsAuthState.LoggedOut, isAdmin = false, adminAlertText = null, linkBroken = true).any)
        assertFalse(tmsBannerState(loggedIn, isAdmin = false, adminAlertText = null, linkBroken = false).any)
    }
}
