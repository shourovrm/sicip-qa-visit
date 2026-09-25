// local source of truth (offline-first). mirrors supabase 001_init.sql tables.
package bd.sicip.qavisit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Officer::class, Trip::class, Visit::class, TravelLeg::class, Activity::class, Bill::class, Report::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun officerDao(): OfficerDao
    abstract fun tripDao(): TripDao
    abstract fun visitDao(): VisitDao
    abstract fun travelLegDao(): TravelLegDao
    abstract fun activityDao(): ActivityDao
    abstract fun billDao(): BillDao
    abstract fun reportDao(): ReportDao

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

        // leaves feature removed -- drop the table, nothing mirrors it anymore.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS leaves")
            }
        }

        // mirrors supabase/migrations/010_reports.sql -- visit reports (surprise visit now,
        // monitoring/QA later). CREATE TABLE matches Room's own generated DDL for the Report
        // entity exactly (verified against the ksp-generated AppDb_Impl before commit): TEXT/
        // INTEGER per Kotlin type, NOT NULL for every non-nullable property, in declaration
        // order -- same discipline as MIGRATION_2_3 above.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reports` (`id` TEXT NOT NULL, `officer_id` TEXT NOT NULL, " +
                        "`visit_id` TEXT NOT NULL, `type` TEXT NOT NULL, `template_version` INTEGER NOT NULL, " +
                        "`data` TEXT NOT NULL, `status` TEXT NOT NULL, `submitted_at` TEXT, " +
                        "`created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, `deleted` INTEGER NOT NULL, " +
                        "`dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        // mirrors supabase/migrations/011_visit_type.sql -- QA report spec §1's new nullable
        // visits.visit_type column ("surprise"/"qa", CHECK omitted here the same way earlier
        // migrations omit them: Room's schema identity hash is computed from the entity
        // annotations, not from an ALTER TABLE's constraints, so a CHECK here would just be
        // unenforced decoration). Old "Surprise Visit" purpose rows are migrated in place, same
        // UPDATE the server migration runs, so local drafts written before this app update still
        // resolve to the same (purpose, visit_type) shape the rest of the code now expects.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE visits ADD COLUMN visit_type TEXT")
                db.execSQL(
                    "UPDATE visits SET purpose = 'Monitoring Visit', visit_type = 'surprise' " +
                        "WHERE purpose = 'Surprise Visit'",
                )
            }
        }

        // single instance per process (room recommends this); double-checked lock avoids
        // two screens racing to open the db file at once.
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "app.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                .also { instance = it }
        }
    }
}
