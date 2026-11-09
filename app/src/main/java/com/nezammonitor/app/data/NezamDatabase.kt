package com.nezammonitor.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CaseEntity::class, SpecificationEntity::class, EngineerEntity::class, FeeEntity::class, ReportEntity::class, FollowUpEditEntity::class, FeeReceiptEntity::class],
    version = 3,
    exportSchema = false
)
abstract class NezamDatabase : RoomDatabase() {
    abstract fun nezamDao(): NezamDao

    companion object {
        @Volatile
        var INSTANCE: NezamDatabase? = null
            private set

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE cases ADD COLUMN ownerFather TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN ownerNationalCode TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN ownerAddress TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN ownerZip TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN ownerTel TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN ownerBirthLoc TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN permitNumber TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN permitDate TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN renovationCode TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN address TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN blockNumber TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN landArea TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN municipalArea TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN effectiveArea TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN floors TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN units TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN buildingGroup TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN structureType TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN usageType TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN planZone TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN issuer TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN stateTitle TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN workType TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN discipline TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN unitTitle TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN orderPart TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN executionDate TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE cases ADD COLUMN dbId TEXT DEFAULT ''") } catch (_: Exception) {}
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("CREATE TABLE IF NOT EXISTS `fee_receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `receiptDate` TEXT NOT NULL DEFAULT '', `amount` INTEGER NOT NULL DEFAULT 0, `relatedEndDatesRaw` TEXT NOT NULL DEFAULT '', `isAdvance` INTEGER NOT NULL DEFAULT 0, `feeSum` INTEGER NOT NULL DEFAULT 0, `changes` TEXT NOT NULL DEFAULT '', `isRecentlyUpdated` INTEGER NOT NULL DEFAULT 0, `createdAt` TEXT NOT NULL DEFAULT '', `updatedAt` TEXT NOT NULL DEFAULT '')") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE engineers ADD COLUMN phone TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE engineers ADD COLUMN designLevel TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE engineers ADD COLUMN supervisionLevel TEXT DEFAULT ''") } catch (_: Exception) {}
                try { db.execSQL("ALTER TABLE engineers ADD COLUMN executionLevel TEXT DEFAULT ''") } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): NezamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NezamDatabase::class.java,
                    "nezam_monitor.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
