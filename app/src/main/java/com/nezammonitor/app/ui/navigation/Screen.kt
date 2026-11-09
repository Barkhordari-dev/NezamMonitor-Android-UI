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
    object Settings : Screen("settings", "تنظیمات", "⚙️")
    object Import : Screen("import", "ورود اطلاعات", "📥")
    object Export : Screen("export", "خروجی اکسل", "📤")
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
    Screen.ReportHistory,
    Screen.Settings
)
