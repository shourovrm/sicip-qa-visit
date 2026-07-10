package bd.sicip.qavisit.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test fun patch_bump_is_newer() {
        assertTrue(isNewer("1.2.1", "1.2.0"))
    }

    @Test fun double_digit_minor_beats_single_digit() {
        assertTrue(isNewer("1.10.0", "1.9.9"))
    }

    @Test fun equal_versions_not_newer() {
        assertFalse(isNewer("1.2.0", "1.2.0"))
    }

    @Test fun older_is_not_newer() {
        assertFalse(isNewer("1.1.0", "1.2.0"))
    }

    @Test fun shorter_string_padded_with_zeros() {
        assertFalse(isNewer("1.2", "1.2.0"))
        assertTrue(isNewer("1.2.1", "1.2"))
    }

    @Test fun garbage_latest_is_not_newer() {
        assertFalse(isNewer("not-a-version", "1.2.0"))
    }

    @Test fun garbage_current_is_not_newer() {
        assertFalse(isNewer("1.2.0", "garbage"))
    }

    @Test fun blank_strings_are_not_newer() {
        assertFalse(isNewer("", ""))
    }

    @Test fun major_bump_is_newer() {
        assertTrue(isNewer("2.0.0", "1.9.9"))
    }
}
