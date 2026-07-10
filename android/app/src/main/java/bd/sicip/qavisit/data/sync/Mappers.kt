// entity <-> supabase json, one section per table. explicit fields only, no reflection --
// keeps kotlin camelCase and sql snake_case (see supabase/migrations/001_init.sql) honest
// against each other, and catches a renamed column at compile time instead of in the field.
//
// updated_at (and visit's created_at) are never pushed: postgrest's merge-duplicates upsert
// takes an INSERT path on conflict, which skips the moddatetime BEFORE UPDATE trigger, so a
// client-supplied updated_at would become server truth and jump ahead of peers' watermarks.
// omitting the column lets the column default / trigger own it. fromJson still reads it back
// on every pull, since the server does stamp it there.
package bd.sicip.qavisit.data.sync

import bd.sicip.qavisit.data.db.Activity
import bd.sicip.qavisit.data.db.Leave
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.data.db.TravelLeg
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// small readers shared by every fromJson below. rows always come back from postgrest, so a
// required column being absent is a genuine bug -- let getValue throw, don't hide it.
private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
private fun JsonObject.strOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: default
private fun JsonObject.boolOrNull(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
private fun JsonObject.num(key: String, default: Double = 0.0): Double =
    this[key]?.jsonPrimitive?.doubleOrNull ?: default
private fun JsonObject.intNum(key: String, default: Int = 0): Int =
    this[key]?.jsonPrimitive?.intOrNull ?: default

// ============ trips ============
fun Trip.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("officer_id", officerId)
    put("status", status)
    put("started_at", startedAt)
    put("finished_at", finishedAt)
    put("informed_officer_id", informedOfficerId)
    put("submitted", submitted)
    put("deleted", deleted)
}

// dirty is local-only bookkeeping, never sent by the server -- always reset to false here,
// this row just arrived already in sync.
fun JsonObject.toTrip(): Trip = Trip(
    id = str("id"),
    officerId = str("officer_id"),
    status = str("status"),
    startedAt = str("started_at"),
    finishedAt = strOrNull("finished_at"),
    informedOfficerId = strOrNull("informed_officer_id"),
    updatedAt = str("updated_at"),
    submitted = bool("submitted"),
    deleted = bool("deleted"),
    dirty = false,
)

// ============ visits ============
fun Visit.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("officer_id", officerId)
    put("trip_id", tripId)
    put("institute", institute)
    put("association", association)
    put("district", district)
    put("dhaka_metro", dhakaMetro)
    put("purpose", purpose)
    put("ref_no", refNo)
    put("ref_date", refDate)
    put("start_date", startDate)
    put("end_date", endDate)
    put("category", category)
    put("category_override", categoryOverride)
    put("is_additional", isAdditional)
    put("status", status)
    put("remarks", remarks)
    put("source", source)
    put("deleted", deleted)
}

fun JsonObject.toVisit(): Visit = Visit(
    id = str("id"),
    officerId = str("officer_id"),
    tripId = strOrNull("trip_id"),
    institute = str("institute"),
    association = str("association"),
    district = str("district"),
    dhakaMetro = boolOrNull("dhaka_metro"),
    purpose = str("purpose"),
    refNo = strOrNull("ref_no"),
    refDate = strOrNull("ref_date"),
    startDate = str("start_date"),
    endDate = str("end_date"),
    category = str("category"),
    categoryOverride = bool("category_override"),
    isAdditional = bool("is_additional"),
    status = str("status"),
    remarks = strOrNull("remarks"),
    source = str("source"),
    createdAt = str("created_at"),
    updatedAt = str("updated_at"),
    deleted = bool("deleted"),
    dirty = false,
)

// ============ travel_legs ============
fun TravelLeg.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("trip_id", tripId)
    put("dep_date", depDate)
    put("dep_time", depTime)
    put("dep_place", depPlace)
    put("arr_date", arrDate)
    put("arr_time", arrTime)
    put("arr_place", arrPlace)
    put("mode", mode)
    put("class", travelClass)
    put("fare", fare)
    put("night_stay", nightStay)
    put("food_day", foodDay)
    put("remarks", remarks)
    put("deleted", deleted)
}

fun JsonObject.toTravelLeg(): TravelLeg = TravelLeg(
    id = str("id"),
    tripId = str("trip_id"),
    depDate = str("dep_date"),
    depTime = str("dep_time"),
    depPlace = str("dep_place"),
    arrDate = str("arr_date"),
    arrTime = str("arr_time"),
    arrPlace = str("arr_place"),
    mode = str("mode"),
    travelClass = strOrNull("class"),
    fare = num("fare"),
    nightStay = intNum("night_stay"),
    foodDay = num("food_day"),
    remarks = strOrNull("remarks"),
    updatedAt = str("updated_at"),
    deleted = bool("deleted"),
    dirty = false,
)

// ============ activities ============
fun Activity.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("trip_id", tripId)
    put("visit_id", visitId)
    put("at", at)
    put("note", note)
    put("deleted", deleted)
}

fun JsonObject.toActivity(): Activity = Activity(
    id = str("id"),
    tripId = strOrNull("trip_id"),
    visitId = strOrNull("visit_id"),
    at = str("at"),
    note = str("note"),
    updatedAt = str("updated_at"),
    deleted = bool("deleted"),
    dirty = false,
)

// ============ leaves ============
fun Leave.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("officer_id", officerId)
    put("type", type)
    put("reason", reason)
    put("informed_officer_id", informedOfficerId)
    put("start_date", startDate)
    put("end_date", endDate)
    put("status", status)
    put("deleted", deleted)
}

fun JsonObject.toLeave(): Leave = Leave(
    id = str("id"),
    officerId = str("officer_id"),
    type = str("type"),
    reason = strOrNull("reason"),
    informedOfficerId = strOrNull("informed_officer_id"),
    startDate = str("start_date"),
    endDate = str("end_date"),
    status = str("status"),
    updatedAt = str("updated_at"),
    deleted = bool("deleted"),
    dirty = false,
)

// ============ officers (pull-only, no toJson -- we never push officer rows) ============
fun JsonObject.toOfficer(): Officer = Officer(
    id = str("id"),
    name = str("name"),
    email = str("email"),
    role = str("role"),
    active = bool("active", default = true),
    updatedAt = str("updated_at"),
    dirty = false,
)
