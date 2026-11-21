package com.nezammonitor.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Yazd design-offices directory (v3.10.0, read-only snapshot since
 * v3.11.3): the offices and engineers of service.yazdnezam.ir:8033/
 * resume/daftar, shipped INSIDE the app as a bundled snapshot
 * (directory-snapshot.json) and seeded into these tables on first run
 * or when a newer snapshot arrives. There is no directory list screen,
 * no full sync and no in-app update — see DirectorySnapshotSeeder.
 * New tables only; no existing table is touched.
 *
 * `siteId` is the site's own internal numeric id (the number in
 * /resume/daftar/{id} and /resume/engineers/{id}). It is NOT the office
 * number shown in the name ("دفتر 4" is site id 32).
 *
 * No discipline is ever hardcoded here: every discipline string comes
 * from the snapshot.
 */

// One row per design office.
@Entity(tableName = "directory_offices")
data class DirectoryOfficeEntity(
    @PrimaryKey val siteId: Int,
    val name: String = "",
    val licence: String = "",
    val manager: String = "",
    val mobile: String = "",
    val contact: String = "",
    val province: String = "",
    val city: String = "",
    val address: String = "",
    val phone: String = "",
    val fax: String = "",
    val hours: String = "",
    val detailUrl: String = "",
    val memberCount: Int = 0,
    val updatedAt: String = ""
)

// One row per engineer of the directory (a different person from the
// per-case EngineerEntity: this one has grades, licence dates and a photo).
@Entity(tableName = "directory_engineers")
data class DirectoryEngineerEntity(
    @PrimaryKey val siteId: Int,
    val name: String = "",
    val family: String = "",
    val discipline: String = "",
    val phone: String = "",
    val designGrade: String = "",
    val supervisionGrade: String = "",
    val executionGrade: String = "",
    val licenceIssue: String = "",
    val licenceValidity: String = "",
    val photo: String = "",
    val officeId: Int = 0,
    val officeName: String = "",
    val updatedAt: String = ""
)

// One row per education record of a directory engineer.
@Entity(
    tableName = "directory_education",
    indices = [Index(value = ["engineerId"])]
)
data class DirectoryEducationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engineerId: Int = 0,
    val degree: String = "",
    val field: String = "",
    val orientation: String = "",
    val universityType: String = "",
    val university: String = "",
    val city: String = "",
    val country: String = "",
    val start: String = "",
    val end: String = ""
)

// One row per licence-history record (design / supervision / execution
// × grade 1/2/3 dates) of a directory engineer.
@Entity(
    tableName = "directory_licence_history",
    indices = [Index(value = ["engineerId"])]
)
data class DirectoryLicenceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engineerId: Int = 0,
    val kind: String = "",
    val grade1: String = "",
    val grade2: String = "",
    val grade3: String = ""
)
