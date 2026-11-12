package com.nezammonitor.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CaseEntity::class, SpecificationEntity::class, EngineerEntity::class, FeeEntity::class, ReportEntity::class, FollowUpEditEntity::class, FeeReceiptEntity::class, SnapshotEntity::class],
    version = 6,
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE reports ADD COLUMN imageName TEXT DEFAULT ''") } catch (_: Exception) {}
            }
        }

        // v5 adds the `archived` column (Archive menu). Room expects it with
        // NO default value (the entity has no @ColumnInfo(defaultValue)), so
        // a plain ALTER TABLE ... ADD COLUMN ... DEFAULT 0 leaves DEFAULT 0 in
        // the schema and Room's migration validation then fails with
        // "Migration didn't properly handle: cases" — the app crashes at the
        // first database open (launch) on every update from v4. Rebuild the
        // table with the exact schema Room expects instead; all data is kept
        // (archived is 0 for every existing row — nobody could archive yet).
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cases_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `caseNumber` TEXT NOT NULL, `serial` TEXT NOT NULL, `owner` TEXT NOT NULL, `ownerMobile` TEXT NOT NULL, `responsibility` TEXT NOT NULL, `capacityDate` TEXT NOT NULL, `office` TEXT NOT NULL, `reportDate1` TEXT NOT NULL, `reportDate2` TEXT NOT NULL, `reportDate3` TEXT NOT NULL, `ownerFather` TEXT NOT NULL, `ownerNationalCode` TEXT NOT NULL, `ownerAddress` TEXT NOT NULL, `ownerZip` TEXT NOT NULL, `ownerTel` TEXT NOT NULL, `ownerBirthLoc` TEXT NOT NULL, `permitNumber` TEXT NOT NULL, `permitDate` TEXT NOT NULL, `renovationCode` TEXT NOT NULL, `address` TEXT NOT NULL, `blockNumber` TEXT NOT NULL, `landArea` TEXT NOT NULL, `municipalArea` TEXT NOT NULL, `effectiveArea` TEXT NOT NULL, `floors` TEXT NOT NULL, `units` TEXT NOT NULL, `buildingGroup` TEXT NOT NULL, `structureType` TEXT NOT NULL, `usageType` TEXT NOT NULL, `planZone` TEXT NOT NULL, `issuer` TEXT NOT NULL, `stateTitle` TEXT NOT NULL, `workType` TEXT NOT NULL, `discipline` TEXT NOT NULL, `unitTitle` TEXT NOT NULL, `orderPart` TEXT NOT NULL, `executionDate` TEXT NOT NULL, `dbId` TEXT NOT NULL, `archived` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `cases_new` (`id`, `caseNumber`, `serial`, `owner`, `ownerMobile`, `responsibility`, `capacityDate`, `office`, `reportDate1`, `reportDate2`, `reportDate3`, `ownerFather`, `ownerNationalCode`, `ownerAddress`, `ownerZip`, `ownerTel`, `ownerBirthLoc`, `permitNumber`, `permitDate`, `renovationCode`, `address`, `blockNumber`, `landArea`, `municipalArea`, `effectiveArea`, `floors`, `units`, `buildingGroup`, `structureType`, `usageType`, `planZone`, `issuer`, `stateTitle`, `workType`, `discipline`, `unitTitle`, `orderPart`, `executionDate`, `dbId`, `archived`) SELECT `id`, `caseNumber`, `serial`, `owner`, `ownerMobile`, `responsibility`, `capacityDate`, `office`, `reportDate1`, `reportDate2`, `reportDate3`, `ownerFather`, `ownerNationalCode`, `ownerAddress`, `ownerZip`, `ownerTel`, `ownerBirthLoc`, `permitNumber`, `permitDate`, `renovationCode`, `address`, `blockNumber`, `landArea`, `municipalArea`, `effectiveArea`, `floors`, `units`, `buildingGroup`, `structureType`, `usageType`, `planZone`, `issuer`, `stateTitle`, `workType`, `discipline`, `unitTitle`, `orderPart`, `executionDate`, `dbId`, 0 FROM `cases`")
                db.execSQL("DROP TABLE IF EXISTS `cases`")
                db.execSQL("ALTER TABLE `cases_new` RENAME TO `cases`")
            }
        }

        // v6 adds the `snapshots` table (Changes menu). A plain CREATE TABLE:
        // it is a new table, so no data to migrate and no validation risk.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAt` TEXT NOT NULL, `label` TEXT NOT NULL, `dataJson` TEXT NOT NULL, `caseCount` INTEGER NOT NULL, `feeCount` INTEGER NOT NULL, `reportCount` INTEGER NOT NULL)")
            }
        }

        fun getDatabase(context: Context): NezamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NezamDatabase::class.java,
                    "nezam_monitor.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
