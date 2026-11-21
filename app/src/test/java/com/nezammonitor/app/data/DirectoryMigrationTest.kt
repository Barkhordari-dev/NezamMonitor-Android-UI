package com.nezammonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Room v9 → v10 migration checks (v3.10.0). There is no Android runtime
 * in a JVM unit test, so the migration cannot be run against a live
 * database here — instead the migration's own SQL is checked against
 * the entity classes, and the KSP-generated schema (NezamDatabase_Impl,
 * produced by the build) is diffed against the same SQL by hand after
 * the build (see the v3.10.0 report). The two rules that caused the
 * 3.7.6 launch crash are asserted directly:
 *  1. No DEFAULT anywhere (a leftover DEFAULT fails Room's migration
 *     validation with "Migration didn't properly handle" at launch).
 *  2. Every column of every new entity appears in the CREATE TABLE
 *     with the type Room expects, and every index names a real
 *     table/column.
 */
class DirectoryMigrationTest {

    private fun entityFields(clazz: Class<*>): List<String> =
        clazz.declaredFields
            .map { it.name }
            .filter { !it.startsWith("\$") && it != "Companion" && it != "INSTANCE" }

    private fun columnsOf(createTableSql: String): List<String> {
        val inner = createTableSql.substringAfter("(").substringBeforeLast(")")
        return inner.split(",")
            .map { it.trim() }
            .filter { it.startsWith("`") }
            .map { it.substringAfter("`").substringBefore("`") }
    }

    @Test
    fun migrationSql_hasNoDefaults() {
        for (sql in NezamDatabase.MIGRATION_9_10_SQL) {
            assertFalse("DEFAULT in migration SQL: $sql", sql.contains("DEFAULT", ignoreCase = true))
        }
    }

    @Test
    fun migrationSql_createsFourTablesWithEntityColumns() {
        val creates = NezamDatabase.MIGRATION_9_10_SQL.filter { it.startsWith("CREATE TABLE") }
        assertEquals(4, creates.size)
        val byTable = creates.associateBy { sql ->
            sql.substringAfter("`").substringBefore("`")
        }
        val expected = mapOf(
            "directory_offices" to DirectoryOfficeEntity::class.java,
            "directory_engineers" to DirectoryEngineerEntity::class.java,
            "directory_education" to DirectoryEducationEntity::class.java,
            "directory_licence_history" to DirectoryLicenceHistoryEntity::class.java
        )
        for ((table, clazz) in expected) {
            val sql = byTable[table] ?: error("missing CREATE TABLE for $table")
            assertEquals(
                "columns of $table",
                entityFields(clazz).sorted(),
                columnsOf(sql).sorted()
            )
        }
    }

    @Test
    fun migrationSql_typesMatchRoomConventions() {
        val sql = NezamDatabase.MIGRATION_9_10_SQL.joinToString("\n")
        // Primary keys: Int site ids, auto-generated Long child ids.
        assertTrue(sql.contains("`siteId` INTEGER NOT NULL"))
        assertTrue(sql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
        // Text columns are TEXT NOT NULL; counts/ids are INTEGER NOT NULL.
        assertTrue(sql.contains("`name` TEXT NOT NULL"))
        assertTrue(sql.contains("`memberCount` INTEGER NOT NULL"))
        assertTrue(sql.contains("`officeId` INTEGER NOT NULL"))
        assertTrue(sql.contains("PRIMARY KEY(`siteId`)"))
    }

    @Test
    fun migrationSql_indexesReferenceRealTables() {
        val indexes = NezamDatabase.MIGRATION_9_10_SQL.filter { it.startsWith("CREATE INDEX") }
        assertEquals(2, indexes.size)
        assertTrue(
            indexes.any { it.contains("`directory_education` (`engineerId`)") }
        )
        assertTrue(
            indexes.any { it.contains("`directory_licence_history` (`engineerId`)") }
        )
        assertTrue(
            indexes.all { it.contains("index_directory_education_engineerId") || it.contains("index_directory_licence_history_engineerId") }
        )
    }
}
