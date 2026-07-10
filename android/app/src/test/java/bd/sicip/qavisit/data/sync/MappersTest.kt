// round-trip every field through toJson()/fromJson() for each synced table. dirty is
// local-only and intentionally reset to false on the way back in -- that's the contract,
// not a bug, so test entities are built with dirty = false to match.
//
// updated_at (and visit's created_at) are server-owned: toJson() must never emit them (C1),
// so a literal toJson().toXxx() round trip no longer works -- fromJson requires the column.
// each test asserts the push payload omits it, then re-adds it (as a pull would) before
// checking every other field survives the round trip.
package bd.sicip.qavisit.data.sync

import bd.sicip.qavisit.data.db.Activity
import bd.sicip.qavisit.data.db.Leave
import bd.sicip.qavisit.data.db.TravelLeg
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

// simulates a server pull: adds back a column toJson() (correctly) never sent.
private fun JsonObject.withColumn(key: String, value: String): JsonObject =
    JsonObject(toMutableMap().also { it[key] = JsonPrimitive(value) })

class MappersTest {
    @Test
    fun `trip round trips`() {
        val trip = Trip(
            id = "t1",
            officerId = "o1",
            status = "finished",
            startedAt = "2024-01-01T00:00:00Z",
            finishedAt = "2024-01-02T00:00:00Z",
            informedOfficerId = "o2",
            updatedAt = "2024-01-03T00:00:00Z",
            deleted = true,
            dirty = false,
        )
        val json = trip.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertEquals(trip, json.withColumn("updated_at", trip.updatedAt).toTrip())
    }

    @Test
    fun `trip round trips with nulls`() {
        val trip = Trip(
            id = "t2",
            officerId = "o1",
            status = "active",
            startedAt = "2024-01-01T00:00:00Z",
            finishedAt = null,
            informedOfficerId = null,
            updatedAt = "2024-01-01T00:00:00Z",
            deleted = false,
            dirty = false,
        )
        val json = trip.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertEquals(trip, json.withColumn("updated_at", trip.updatedAt).toTrip())
    }

    @Test
    fun `visit round trips`() {
        val visit = Visit(
            id = "v1",
            officerId = "o1",
            tripId = "t1",
            institute = "Institute",
            association = "Association",
            district = "Dhaka",
            dhakaMetro = true,
            purpose = "purpose text",
            refNo = "REF-1",
            startDate = "2024-01-01",
            endDate = "2024-01-02",
            category = "A+",
            categoryOverride = true,
            isAdditional = true,
            status = "done",
            remarks = "some remark",
            source = "sheet",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-02T00:00:00Z",
            deleted = true,
            dirty = false,
        )
        val json = visit.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertFalse(json.containsKey("created_at"))
        assertEquals(
            visit,
            json.withColumn("updated_at", visit.updatedAt).withColumn("created_at", visit.createdAt).toVisit(),
        )
    }

    @Test
    fun `visit round trips with nulls`() {
        val visit = Visit(
            id = "v2",
            officerId = "o1",
            tripId = null,
            institute = "Institute",
            association = "Association",
            district = "Chattogram",
            dhakaMetro = null,
            purpose = "purpose text",
            refNo = null,
            startDate = "2024-01-01",
            endDate = "2024-01-01",
            category = "N/A",
            categoryOverride = false,
            isAdditional = false,
            status = "scheduled",
            remarks = null,
            source = "app",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            deleted = false,
            dirty = false,
        )
        val json = visit.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertFalse(json.containsKey("created_at"))
        assertEquals(
            visit,
            json.withColumn("updated_at", visit.updatedAt).withColumn("created_at", visit.createdAt).toVisit(),
        )
    }

    @Test
    fun `travel leg round trips`() {
        val leg = TravelLeg(
            id = "tl1",
            tripId = "t1",
            depDate = "2024-01-01",
            depTime = "08:00:00",
            depPlace = "A place",
            arrDate = "2024-01-01",
            arrTime = "10:30:00",
            arrPlace = "B place",
            mode = "bus",
            travelClass = "AC",
            fare = 123.45,
            nightStay = 2,
            foodDay = 1.5,
            remarks = "remark",
            updatedAt = "2024-01-01T00:00:00Z",
            deleted = true,
            dirty = false,
        )
        val json = leg.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertEquals(leg, json.withColumn("updated_at", leg.updatedAt).toTravelLeg())
    }

    @Test
    fun `travel leg round trips with null class and remarks`() {
        val leg = TravelLeg(
            id = "tl2",
            tripId = "t1",
            depDate = "2024-01-01",
            depTime = "08:00:00",
            depPlace = "A place",
            arrDate = "2024-01-01",
            arrTime = "10:30:00",
            arrPlace = "B place",
            mode = "flight",
            travelClass = null,
            fare = 0.0,
            nightStay = 0,
            foodDay = 0.0,
            remarks = null,
            updatedAt = "2024-01-01T00:00:00Z",
            deleted = false,
            dirty = false,
        )
        val json = leg.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertEquals(leg, json.withColumn("updated_at", leg.updatedAt).toTravelLeg())
    }

    @Test
    fun `activity round trips with trip id`() {
        val activity = Activity(
            id = "a1",
            tripId = "t1",
            visitId = null,
            at = "2024-01-01T00:00:00Z",
            note = "a note",
            updatedAt = "2024-01-01T00:00:00Z",
            deleted = true,
            dirty = false,
        )
        val json = activity.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertEquals(activity, json.withColumn("updated_at", activity.updatedAt).toActivity())
    }

    @Test
    fun `activity round trips with visit id`() {
        val activity = Activity(
            id = "a2",
            tripId = null,
            visitId = "v1",
            at = "2024-01-01T00:00:00Z",
            note = "a note",
            updatedAt = "2024-01-01T00:00:00Z",
            deleted = false,
            dirty = false,
        )
        val json = activity.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertEquals(activity, json.withColumn("updated_at", activity.updatedAt).toActivity())
    }

    @Test
    fun `leave round trips`() {
        val leave = Leave(
            id = "l1",
            officerId = "o1",
            type = "Casual",
            reason = "reason text",
            informedOfficerId = "o2",
            startDate = "2024-01-01",
            endDate = "2024-01-02",
            status = "availed",
            updatedAt = "2024-01-01T00:00:00Z",
            deleted = true,
            dirty = false,
        )
        val json = leave.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertEquals(leave, json.withColumn("updated_at", leave.updatedAt).toLeave())
    }

    @Test
    fun `leave round trips with nulls`() {
        val leave = Leave(
            id = "l2",
            officerId = "o1",
            type = "Sick",
            reason = null,
            informedOfficerId = null,
            startDate = "2024-01-01",
            endDate = "2024-01-01",
            status = "scheduled",
            updatedAt = "2024-01-01T00:00:00Z",
            deleted = false,
            dirty = false,
        )
        val json = leave.toJson()
        assertFalse(json.containsKey("updated_at"))
        assertEquals(leave, json.withColumn("updated_at", leave.updatedAt).toLeave())
    }

    @Test
    fun `officer parses from json (pull-only, no toJson)`() {
        val json = buildJsonObject {
            put("id", "o1")
            put("name", "Jane Officer")
            put("email", "jane@example.com")
            put("role", "admin")
            put("active", false)
            put("updated_at", "2024-01-01T00:00:00Z")
        }
        val officer = json.toOfficer()
        assertEquals("o1", officer.id)
        assertEquals("Jane Officer", officer.name)
        assertEquals("jane@example.com", officer.email)
        assertEquals("admin", officer.role)
        assertEquals(false, officer.active)
        assertEquals("2024-01-01T00:00:00Z", officer.updatedAt)
        assertEquals(false, officer.dirty)
    }
}
