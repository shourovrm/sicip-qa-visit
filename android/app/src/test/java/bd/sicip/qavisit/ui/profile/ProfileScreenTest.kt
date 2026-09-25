// password-form validation + error mapping; pure functions, no compose/android involved.
package bd.sicip.qavisit.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProfileScreenTest {
    @Test fun valid_when_long_enough_and_matching() {
        assertTrue(passwordFormValid("password1", "password1"))
    }

    @Test fun invalid_when_too_short() {
        assertFalse(passwordFormValid("short1", "short1"))
    }

    @Test fun invalid_when_mismatched() {
        assertFalse(passwordFormValid("password1", "password2"))
    }

    @Test fun offline_error_reads_friendly() {
        assertEquals("No connection — try again", passwordErrorMessage(IOException("network down")))
    }

    @Test fun validation_error_surfaces_its_message() {
        assertEquals("too short", passwordErrorMessage(IllegalArgumentException("too short")))
    }

    @Test fun unknown_error_falls_back_to_generic_message() {
        assertEquals("Something went wrong — try again", passwordErrorMessage(RuntimeException("boom")))
    }

    @Test fun selected_model_key_is_saved_pick_when_present() {
        assertEquals("careful", selectedModelKey("careful", "fast", listOf("fast", "careful")))
    }

    @Test fun selected_model_key_falls_back_to_server_default_when_unsaved() {
        assertEquals("fast", selectedModelKey(null, "fast", listOf("fast")))
        assertEquals("fast", selectedModelKey("gone", "fast", listOf("fast")))
    }

    @Test fun tapping_a_non_default_model_saves_its_key() {
        assertEquals("careful", modelKeyToSave("careful", "fast"))
    }

    @Test fun tapping_the_default_model_clears_back_to_null() {
        assertEquals(null, modelKeyToSave("fast", "fast"))
    }
}
