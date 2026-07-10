// session expiry math only (valid()); the datastore-backed store itself needs an
// android context and is compile-checked, not unit-tested (no robolectric here — yagni).
package bd.sicip.qavisit.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private fun session(expiresAt: Long) = Session(
        accessToken = "a",
        refreshToken = "r",
        expiresAt = expiresAt,
        userId = "u1",
        email = "x@sicip.bd",
    )

    @Test fun valid_well_before_expiry() {
        assertTrue(session(expiresAt = 1_000).valid(nowEpochSeconds = 100))
    }

    @Test fun invalid_past_expiry() {
        assertFalse(session(expiresAt = 1_000).valid(nowEpochSeconds = 1_001))
    }

    @Test fun invalid_inside_60s_skew_window() {
        // 30s left on the clock still counts as expired: refresh should kick in early
        assertFalse(session(expiresAt = 1_000).valid(nowEpochSeconds = 970))
    }

    @Test fun valid_just_outside_skew_window() {
        assertTrue(session(expiresAt = 1_000).valid(nowEpochSeconds = 939))
    }
}
