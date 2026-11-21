package com.nezammonitor.app.data

import android.content.Context

/**
 * The Yazd design-offices directory, bundled INSIDE the app as a
 * snapshot asset (v3.11.3) — there is NO official API, and since
 * v3.11.3 there is NO in-app update anymore either (user decision
 * 2026-11-20: the user replaces the snapshot itself in a future
 * version). This class only seeds the Room directory tables from the
 * asset (DirectorySnapshotSeeder); the office-detail and engineer-
 * profile screens read the local tables directly. Nothing here touches
 * the network — the whole site-fetching layer (search, detail fetch,
 * per-case sync, per-item refresh) was removed in v3.11.3. The page
 * parser (DirectoryParser) stays as a utility: its name normalization
 * is still used for the local lookups, and its tests still cover it.
 */
class DirectoryRepository(
    private val dao: DirectoryDao,
    private val prefs: android.content.SharedPreferences?
) {
    /** Production entry point (MainViewModel). */
    constructor(context: Context, dao: DirectoryDao) : this(
        dao,
        context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE)
    )

    /** Seeds the directory tables from the bundled snapshot asset when
     *  its version is newer than the one already seeded (v3.11.3).
     *  No network, never throws. Safe to call on every app start — the
     *  version check reads only the first bytes of the asset. */
    suspend fun ensureSnapshotSeeded(context: Context): DirectorySnapshotSeeder.SeedOutcome {
        val prefs = prefs ?: return DirectorySnapshotSeeder.SeedOutcome.Failed("تنظیمات در دسترس نیست")
        return DirectorySnapshotSeeder.ensureSeeded(context, dao, prefs)
    }
}
