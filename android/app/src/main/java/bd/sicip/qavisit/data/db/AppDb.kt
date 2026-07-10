// local source of truth (offline-first). mirrors supabase 001_init.sql tables.
package bd.sicip.qavisit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Officer::class, Trip::class, Visit::class, TravelLeg::class, Activity::class, Leave::class, Bill::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun officerDao(): OfficerDao
    abstract fun tripDao(): TripDao
    abstract fun visitDao(): VisitDao
    abstract fun travelLegDao(): TravelLegDao
    abstract fun activityDao(): ActivityDao
    abstract fun leaveDao(): LeaveDao
    abstract fun billDao(): BillDao

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

        // mirrors supabase/migrations/003_bills_appmeta.sql -- immutable submitted-bill archive.
        // CREATE TABLE matches Room's own generated DDL for the Bill entity exactly (verified
        // against the ksp-generated AppDb_Impl before commit): TEXT/REAL/INTEGER per Kotlin
        // type, NOT NULL for every non-nullable property, in declaration order.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bills` (`id` TEXT NOT NULL, `officer_id` TEXT NOT NULL, " +
                        "`bill_date` TEXT NOT NULL, `data` TEXT NOT NULL, `net` REAL NOT NULL, " +
                        "`created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, `deleted` INTEGER NOT NULL, " +
                        "`dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        // single instance per process (room recommends this); double-checked lock avoids
        // two screens racing to open the db file at once.
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "app.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
