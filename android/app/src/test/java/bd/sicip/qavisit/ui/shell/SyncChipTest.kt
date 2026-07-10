// sync chip text/relative-time; pure functions, no compose involved.
package bd.sicip.qavisit.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private val NOW = Instant.parse("2026-07-10T12:00:00.000Z")

class SyncChipTest {
    @Test fun error_wins_over_a_stale_success() {
        assertEquals("⚠ Offline", syncChipText("2026-07-10T11:00:00.000Z", "network down", NOW))
    }

    @Test fun never_synced_and_no_error_yet_reads_syncing() {
        assertEquals("Syncing…", syncChipText(null, null, NOW))
    }

    @Test fun successful_sync_shows_relative_time() {
        assertEquals("✓ Synced 5m", syncChipText("2026-07-10T11:55:00.000Z", null, NOW))
    }

    @Test fun relative_time_buckets() {
        assertEquals("just now", relativeTime("2026-07-10T11:59:30.000Z", NOW))
        assertEquals("5m", relativeTime("2026-07-10T11:55:00.000Z", NOW))
        assertEquals("2h", relativeTime("2026-07-10T10:00:00.000Z", NOW))
        assertEquals("1d", relativeTime("2026-07-09T12:00:00.000Z", NOW))
    }

    @Test fun unparseable_timestamp_falls_back_to_just_now() {
        assertEquals("just now", relativeTime("not-a-date", NOW))
    }
}
