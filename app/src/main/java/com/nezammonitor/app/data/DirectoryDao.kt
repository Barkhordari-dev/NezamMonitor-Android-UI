package com.nezammonitor.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectoryDao {
    // Offices
    @Query("SELECT * FROM directory_offices ORDER BY name ASC")
    fun getAllOffices(): Flow<List<DirectoryOfficeEntity>>

    @Query("SELECT * FROM directory_offices ORDER BY name ASC")
    suspend fun getAllOfficesList(): List<DirectoryOfficeEntity>

    @Query("SELECT * FROM directory_offices WHERE siteId = :siteId")
    suspend fun getOffice(siteId: Int): DirectoryOfficeEntity?

    @Query("SELECT COUNT(*) FROM directory_offices")
    suspend fun getOfficeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffices(offices: List<DirectoryOfficeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffice(office: DirectoryOfficeEntity)

    // Engineers
    @Query("SELECT * FROM directory_engineers WHERE siteId = :siteId")
    suspend fun getEngineer(siteId: Int): DirectoryEngineerEntity?

    @Query("SELECT * FROM directory_engineers WHERE officeId = :officeId ORDER BY name ASC, family ASC")
    suspend fun getEngineersForOffice(officeId: Int): List<DirectoryEngineerEntity>

    @Query("SELECT * FROM directory_engineers ORDER BY name ASC, family ASC")
    suspend fun getAllEngineersList(): List<DirectoryEngineerEntity>

    @Query("SELECT COUNT(*) FROM directory_engineers")
    suspend fun getEngineerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEngineers(engineers: List<DirectoryEngineerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEngineer(engineer: DirectoryEngineerEntity)

    // Education (replaced wholesale on every snapshot seed)
    @Query("SELECT * FROM directory_education WHERE engineerId = :engineerId ORDER BY id ASC")
    suspend fun getEducation(engineerId: Int): List<DirectoryEducationEntity>

    @Query("DELETE FROM directory_education WHERE engineerId = :engineerId")
    suspend fun deleteEducation(engineerId: Int)

    @Insert
    suspend fun insertEducation(rows: List<DirectoryEducationEntity>)

    // Licence history (replaced wholesale on every snapshot seed)
    @Query("SELECT * FROM directory_licence_history WHERE engineerId = :engineerId ORDER BY id ASC")
    suspend fun getLicenceHistory(engineerId: Int): List<DirectoryLicenceHistoryEntity>

    @Query("DELETE FROM directory_licence_history WHERE engineerId = :engineerId")
    suspend fun deleteLicenceHistory(engineerId: Int)

    @Insert
    suspend fun insertLicenceHistory(rows: List<DirectoryLicenceHistoryEntity>)

    // Full replace (v3.11.3): a newer bundled snapshot replaces the
    // whole directory dataset — the tables are wiped and re-seeded.
    @Query("DELETE FROM directory_offices")
    suspend fun deleteAllOffices()

    @Query("DELETE FROM directory_engineers")
    suspend fun deleteAllEngineers()

    @Query("DELETE FROM directory_education")
    suspend fun deleteAllEducation()

    @Query("DELETE FROM directory_licence_history")
    suspend fun deleteAllLicenceHistory()
}
