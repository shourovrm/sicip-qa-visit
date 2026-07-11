// resolveMode / modeDropdownFor: dropdown+free-text <-> stored mode, both directions.
// composeRemarks / splitTicketRemark: remarks text + ticket tick box <-> stored remarks string.
package bd.sicip.qavisit.ui.home

import bd.sicip.qavisit.data.seed.TICKET_REMARK
import bd.sicip.qavisit.data.seed.TRANSPORT
import org.junit.Assert.assertEquals
import org.junit.Test

class LegFormTest {

    @Test fun seed_mode_stores_as_is() {
        assertEquals("Bus", resolveMode(dropdownValue = "Bus", otherText = "ignored"))
    }

    @Test fun other_with_text_stores_the_typed_name() {
        assertEquals("Car", resolveMode(dropdownValue = "Other", otherText = "Car"))
    }

    @Test fun other_left_blank_falls_back_to_other() {
        assertEquals("Other", resolveMode(dropdownValue = "Other", otherText = ""))
        assertEquals("Other", resolveMode(dropdownValue = "Other", otherText = "   "))
    }

    @Test fun seed_stored_mode_selects_itself_with_no_free_text() {
        assertEquals("Bus" to "", modeDropdownFor("Bus"))
        assertEquals("Other" to "", modeDropdownFor("Other"))
    }

    @Test fun non_seed_stored_mode_selects_other_with_prefilled_free_text() {
        assertEquals("Other" to "Car", modeDropdownFor("Car"))
    }

    @Test fun round_trips_through_the_real_seed_list() {
        TRANSPORT.keys.forEach { seedMode ->
            val (dropdown, other) = modeDropdownFor(seedMode)
            assertEquals(seedMode, resolveMode(dropdown, other))
        }
        val (dropdown, other) = modeDropdownFor("Rickshaw")
        assertEquals("Rickshaw", resolveMode(dropdown, other))
    }

    @Test fun blank_ticked_round_trips() {
        val stored = composeRemarks("", ticket = true)
        assertEquals(TICKET_REMARK, stored)
        assertEquals("" to true, splitTicketRemark(stored))
    }

    @Test fun text_ticked_round_trips() {
        val stored = composeRemarks("bus fare paid cash", ticket = true)
        assertEquals("bus fare paid cash; $TICKET_REMARK", stored)
        assertEquals("bus fare paid cash" to true, splitTicketRemark(stored))
    }

    @Test fun text_only_round_trips() {
        val stored = composeRemarks("bus fare paid cash", ticket = false)
        assertEquals("bus fare paid cash", stored)
        assertEquals("bus fare paid cash" to false, splitTicketRemark(stored))
    }

    @Test fun blank_unticked_stores_null() {
        assertEquals(null, composeRemarks("   ", ticket = false))
        assertEquals("" to false, splitTicketRemark(null))
    }

    @Test fun ticked_only_stored_value_splits_to_blank_ticked() {
        assertEquals("" to true, splitTicketRemark(TICKET_REMARK))
    }

    @Test fun legacy_remark_without_ticket_stays_untouched() {
        assertEquals("old remark, no ticket box back then" to false, splitTicketRemark("old remark, no ticket box back then"))
    }
}
