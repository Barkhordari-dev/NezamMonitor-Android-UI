package com.nezammonitor.app.ui.navigation

import java.net.URLEncoder

sealed class Screen(val route: String, val title: String, val icon: String) {
    object Home : Screen("home", "خانه", "🏠")
    object Update : Screen("update", "بروزرسانی", "🔄")
    object Cases : Screen("cases", "پرونده\u200Cها", "📋")
    object CaseDetail : Screen("case_detail/{caseNumber}", "جزئیات پرونده", "📋") {
        fun createRoute(caseNumber: String) = "case_detail/${URLEncoder.encode(caseNumber, "UTF-8")}"
    }
    object Engineers : Screen("engineers", "مهندسین", "👷")
    object EngineerDetail : Screen("engineer_detail/{caseNumber}", "مهندسین پرونده", "👷") {
        fun createRoute(caseNumber: String) = "engineer_detail/${URLEncoder.encode(caseNumber, "UTF-8")}"
    }
    object Fees : Screen("fees", "حق\u200Cالزحمه", "💰")
    object FeeDetail : Screen("fee_detail/{caseNumber}", "حق\u200Cالزحمه پرونده", "💰") {
        fun createRoute(caseNumber: String) = "fee_detail/${URLEncoder.encode(caseNumber, "UTF-8")}"
    }
    object Reports : Screen("reports", "گزارش\u200Cها", "📄")
    object ReportDetail : Screen("report_detail/{caseNumber}", "گزارش\u200Cهای پرونده", "📄") {
        fun createRoute(caseNumber: String) = "report_detail/${URLEncoder.encode(caseNumber, "UTF-8")}"
    }
    object FollowUp : Screen("followup", "پیگیری\u200Cها", "📌")
    object Receipts : Screen("receipts", "دریافتی‌ها", "🧾")
    object ReportGenerator : Screen("report_generator", "تولید گزارش", "📊")
    object ReportHistory : Screen("report_history", "سابقه گزارش\u200Cها", "📦")
    object ScheduleSettings : Screen("schedule_settings", "تغییر زمان\u200Cبندی", "⏱️")
    object ReportHelp : Screen("report_help", "راهنمای تولید گزارش", "❓")
    object ReportPaths : Screen("report_paths", "مسیرها", "📂")
    object Archive : Screen("archive", "بایگانی", "🗄️")
    object ArchiveDetail : Screen("archive_detail/{caseNumber}", "جزئیات پرونده بایگانی\u200Cشده", "🗄️") {
        fun createRoute(caseNumber: String) = "archive_detail/${URLEncoder.encode(caseNumber, "UTF-8")}"
    }
    object Changes : Screen("changes", "تغییرات", "📸")
    object Settings : Screen("settings", "تنظیمات", "⚙️")
    // Hidden settings (v3.8.4): NOT in drawerItems — reachable only by
    // tapping the About card in Settings 10 times and entering the password.
    object HiddenSettings : Screen("hidden_settings", "تنظیمات مخفی", "🔒")
    object Import : Screen("import", "ورود اطلاعات", "📥")
    object Export : Screen("export", "خروجی اکسل", "📤")
    // Yazd design-offices directory (v3.10.0, read-only since v3.11.3):
    // detail pages ONLY — there is no directory list/search screen and
    // no menu entry for browsing. They are opened by tapping an office
    // number or an engineer name wherever it already appears in the
    // existing UI. The routes carry the NAME (not the site id): the
    // screen resolves it from the bundled snapshot's local tables and
    // shows «یافت نشد» when the snapshot has no such office/engineer.
    // Nothing here fetches from the site anymore.
    object DirectoryOfficeDetail : Screen("directory_office_detail/{officeName}", "جزئیات دفتر طراحی", "🏢") {
        fun createRoute(officeName: String) = "directory_office_detail/${URLEncoder.encode(officeName, "UTF-8")}"
    }
    object DirectoryEngineerProfile : Screen("directory_engineer_profile/{engineerName}", "پروفایل مهندس", "👷") {
        fun createRoute(engineerName: String) =
            "directory_engineer_profile/${URLEncoder.encode(engineerName, "UTF-8")}"
    }
}

val drawerItems = listOf(
    Screen.Home,
    Screen.Update,
    Screen.Cases,
    Screen.Engineers,
    Screen.Fees,
    Screen.Reports,
    Screen.FollowUp,
    Screen.Receipts,
    Screen.ReportGenerator,
    Screen.Archive,
    Screen.Changes,
    Screen.Settings
)
