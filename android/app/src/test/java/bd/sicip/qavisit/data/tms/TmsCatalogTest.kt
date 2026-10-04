package bd.sicip.qavisit.data.tms

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class TmsCatalogTest {
    // TMS institute rows carry the entity that registered the institute; an institute shared by
    // two partners (Compact: BACI 5 + BEIOA 8) must keep the partner the list was asked for
    @Test
    fun `institute keeps the partner it was listed under, not the row's entity`() {
        val row = buildJsonObject {
            put("id", 1503)
            put("entity_id", 5)
            put("training_institute_no", "1820988")
            put("institute_name", "Compact")
        }
        assertEquals(8L, tmsInstituteOf(row, entityId = 8).entityId)
    }
}
