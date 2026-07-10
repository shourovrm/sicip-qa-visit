// local source of truth (offline-first). mirrors supabase 001_init.sql tables.
package bd.sicip.qavisit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Officer::class, Trip::class, Visit::class, TravelLeg::class, Activity::class, Leave::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun officerDao(): OfficerDao
    abstract fun tripDao(): TripDao
    abstract fun visitDao(): VisitDao
    abstract fun travelLegDao(): TravelLegDao
    abstract fun activityDao(): ActivityDao
    abstract fun leaveDao(): LeaveDao
}
