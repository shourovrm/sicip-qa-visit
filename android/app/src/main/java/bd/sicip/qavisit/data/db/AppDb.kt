// local source of truth (offline-first). mirrors supabase 001_init.sql tables.
package bd.sicip.qavisit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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

    companion object {
        // single instance per process (room recommends this); double-checked lock avoids
        // two screens racing to open the db file at once.
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "app.db")
                .build()
                .also { instance = it }
        }
    }
}
