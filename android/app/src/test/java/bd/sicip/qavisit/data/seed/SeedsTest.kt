// seed lists: counts + a few known entries, exact per design spec
package bd.sicip.qavisit.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedsTest {

    @Test fun districts_are_64_and_alphabetical_and_unique() {
        assertEquals(64, DISTRICTS.size)
        assertEquals(DISTRICTS.distinct().size, DISTRICTS.size)
        assertEquals(DISTRICTS.sorted(), DISTRICTS)
        assertTrue(DISTRICTS.contains("Dhaka"))
    }

    @Test fun associations_are_30() {
        assertEquals(30, ASSOCIATIONS.size)
        assertEquals(ASSOCIATIONS.distinct().size, ASSOCIATIONS.size)
        assertTrue(ASSOCIATIONS.contains("Others"))
        assertTrue(ASSOCIATIONS.contains("BGMEA"))
    }

    @Test fun purposes_are_six() {
        assertEquals(6, PURPOSES.size)
        assertTrue(PURPOSES.contains("Capacity Assessment"))
        assertTrue(PURPOSES.contains("Others"))
    }

    @Test fun transport_modes_map_to_classes() {
        assertEquals(listOf("AC", "Non-AC"), TRANSPORT["Bus"])
        assertEquals(listOf("Snigdha", "AC Berth", "AC Seat", "Shovon"), TRANSPORT["Train"])
        assertEquals(listOf("Single AC Cabin", "Non-AC Cabin", "AC Seat"), TRANSPORT["Launch"])
        assertEquals(listOf("Economy"), TRANSPORT["Air"])
        assertEquals(listOf("Rented"), TRANSPORT["Uber Car"])
        assertEquals(emptyList<String>(), TRANSPORT["Other"])
        assertEquals(emptyList<String>(), TRANSPORT["N/A"])
        assertEquals(12, TRANSPORT.size)
    }
}
