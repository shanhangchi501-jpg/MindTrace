package com.mindtrace.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        CheckInEntity::class,
        ProfileEntity::class,
        SymptomEntity::class,
        MedicationEntity::class,
        MedicationLogEntity::class,
        ScaleAssessmentEntity::class,
        JournalEntryEntity::class,
        SafetyPlanEntity::class,
        CrisisContactEntity::class,
        DiagnosisEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MindTraceDatabase : RoomDatabase() {
    abstract fun checkInDao(): CheckInDao
    abstract fun profileDao(): ProfileDao
    abstract fun symptomDao(): SymptomDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun scaleDao(): ScaleDao
    abstract fun journalDao(): JournalDao
    abstract fun safetyDao(): SafetyDao
    abstract fun diagnosisDao(): DiagnosisDao

    companion object {
        const val DB_NAME = "mindtrace.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `diagnoses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `standard` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `nameEn` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `diagnosedDate` TEXT,
                        `clinician` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `check_ins` ADD COLUMN `symptomSeverity` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun create(context: Context, passphrase: ByteArray): MindTraceDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                MindTraceDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
