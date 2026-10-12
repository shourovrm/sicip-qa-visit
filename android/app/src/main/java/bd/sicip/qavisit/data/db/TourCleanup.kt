// tour left with no visit: soft-delete it with its travels + activity notes (dirty, syncs up).
package bd.sicip.qavisit.data.db

import java.time.Instant

data class EmptiedTour(val tripId: String, val travelCount: Int)

// the tour that deleting `visit` would leave empty. null = visit not on a tour, other visits
// remain, or the tour is on a submitted bill (frozen, stays).
suspend fun tourEmptiedBy(db: AppDb, visit: Visit): EmptiedTour? {
    val tripId = visit.tripId ?: return null
    val hasOtherVisit = db.visitDao().byTrip(tripId).any { it.id != visit.id }
    if (hasOtherVisit) return null
    val trip = db.tripDao().byId(tripId) ?: return null
    if (trip.submitted || trip.deleted) return null
    return EmptiedTour(tripId, db.travelLegDao().byTrip(tripId).size)
}

suspend fun deleteTour(db: AppDb, tripId: String) {
    val now = Instant.now().toString()
    db.travelLegDao().byTrip(tripId).forEach { db.travelLegDao().softDelete(it.id, now) }
    db.activityDao().byTrip(tripId).forEach { db.activityDao().softDelete(it.id, now) }
    db.tripDao().softDelete(tripId, now)
}
