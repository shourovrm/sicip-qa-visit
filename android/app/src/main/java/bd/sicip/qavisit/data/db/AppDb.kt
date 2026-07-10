// local source of truth (offline-first). mirrors supabase 001_init.sql tables.
package bd.sicip.qavisit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Officer::class, Trip::class, Visit::class, TravelLeg::class, Activity::class, Leave::class],
    version = 2,
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
        // mirrors supabase/migrations/002_ref_date_submitted.sql. existing installs must
        // migrate cleanly -- no fallbackToDestructiveMigration, this is a phone in someone's
        // pocket with real trip data on it.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE visits ADD COLUMN ref_date TEXT")
                db.execSQL("ALTER TABLE trips ADD COLUMN submitted INTEGER NOT NULL DEFAULT 0")
            }
        }

        // single instance per process (room recommends this); double-checked lock avoids
        // two screens racing to open the db file at once.
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "app.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
