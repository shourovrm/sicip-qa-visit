package bd.sicip.qavisit.data.tms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmsMatchingTest {
    private fun institute(id: Long, name: String, short: String = "", address: String = "") =
        TmsInstitute(id, "N$id", short, name, address, entityId = 1)

    private val institutes = listOf(
        institute(1, "Bogura Textile Institute", "BTI", "Bogra-5800"),
        institute(2, "Dhaka Garments Centre", "DGC", "Mirpur, Dhaka-1216"),
        institute(3, "Comilla Apparel Training", "CAT", "Cumilla Sadar"),
        institute(4, "Chittagong Sewing School", "CSS", "Agrabad, Chittagong"),
    )

    @Test
    fun `partner matches association ignoring case and spaces`() {
        val entities = listOf(TmsEntity(1, "BGMEA"), TmsEntity(2, "Kumudini"))
        assertEquals(2L, matchPartner(entities, " kumudini ")?.id)
        assertNull(matchPartner(entities, "Others"))
        assertNull(matchPartner(entities, ""))
    }

    @Test
    fun `search needs every word in name, short name or address`() {
        assertEquals(listOf(2L), searchInstitutes(institutes, "mirpur garments").map { it.id })
        assertEquals(listOf(1L), searchInstitutes(institutes, "bti").map { it.id })
        assertEquals(4, searchInstitutes(institutes, "  ").size)
    }

    @Test
    fun `district filter accepts alternate spellings`() {
        assertEquals(listOf(1L), filterByDistrict(institutes, "Bogura").map { it.id })
        assertEquals(listOf(1L), filterByDistrict(institutes, "Bogra").map { it.id })
        assertEquals(listOf(4L), filterByDistrict(institutes, "Chattogram").map { it.id })
        assertEquals(listOf(3L), filterByDistrict(institutes, "Cumilla").map { it.id })
        assertEquals(listOf(2L), filterByDistrict(institutes, "dhaka").map { it.id })
    }

    @Test
    fun `district filter falls back to the full list when nothing matches`() {
        assertEquals(4, filterByDistrict(institutes, "Sylhet").size)
        assertEquals(4, filterByDistrict(institutes, "Jashore").size)
        assertEquals(4, filterByDistrict(institutes, "").size)
    }

    @Test
    fun `alias groups cover both directions`() {
        assertEquals(setOf("barishal", "barisal"), districtSpellings("Barisal"))
        assertEquals(setOf("jashore", "jessore"), districtSpellings("Jessore"))
        assertEquals(setOf("rangpur"), districtSpellings("Rangpur"))
    }

    @Test
    fun `auto link needs exactly one exact name or short name`() {
        assertEquals(2L, autoLinkCandidate(institutes, " dhaka garments centre ")?.id)
        assertEquals(3L, autoLinkCandidate(institutes, "cat")?.id)
        assertNull(autoLinkCandidate(institutes, "Dhaka"))
        assertNull(autoLinkCandidate(institutes + institute(9, "DHAKA GARMENTS CENTRE"), "Dhaka Garments Centre"))
        assertNull(autoLinkCandidate(institutes, ""))
    }

    @Test
    fun `default tranche is the newest active one`() {
        val tranches = listOf(TmsTranche(1, "T1", true), TmsTranche(3, "T3", false), TmsTranche(2, "T2", true))
        assertEquals(2L, defaultTranche(tranches)?.id)
        assertEquals(3L, defaultTranche(tranches.map { it.copy(active = false) })?.id)
        assertNull(defaultTranche(emptyList()))
    }
}
