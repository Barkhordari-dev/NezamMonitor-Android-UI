package com.nezammonitor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nezammonitor.app.ui.components.NezamGradientBackground
import com.nezammonitor.app.ui.navigation.Screen
import com.nezammonitor.app.ui.navigation.drawerItems
import com.nezammonitor.app.ui.screens.cases.CasesScreen
import com.nezammonitor.app.ui.screens.cases.CaseDetailScreen
import com.nezammonitor.app.ui.screens.engineers.EngineersScreen
import com.nezammonitor.app.ui.screens.fees.FeesScreen
import com.nezammonitor.app.ui.screens.followup.FollowUpScreen
import com.nezammonitor.app.ui.screens.home.HomeScreen
import com.nezammonitor.app.ui.screens.reports.ReportsScreen
import com.nezammonitor.app.ui.screens.settings.SettingsScreen
import com.nezammonitor.app.ui.screens.export.ExportScreen
import com.nezammonitor.app.ui.screens.update.UpdateScreen
import com.nezammonitor.app.ui.screens.reportgen.ReportGeneratorScreen
import com.nezammonitor.app.ui.screens.reporthistory.ReportHistoryScreen
import com.nezammonitor.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NezamMonitorApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // Branded header: diagonal gradient in the theme's accent
                // colours, with the title in onPrimary.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        "مانیتورینگ نظام مهندسی",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "مهندس برخورداری",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    drawerItems.forEach { screen ->
                        NavigationDrawerItem(
                            icon = { Text(screen.icon, fontSize = 18.sp) },
                            label = { Text(screen.title, fontSize = 14.sp) },
                            // Was hard-coded `false`, so the current page was
                            // never highlighted. Now it follows the NavHost.
                            selected = currentRoute == screen.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) {
        // The whole app sits on the theme's vertical gradient; the Scaffold
        // and the top bar are transparent so it shows through on every screen.
        NezamGradientBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        title = {
                            Column {
                                Text("مانیتورینگ نظام مهندسی", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("مهندس برخورداری", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "منو")
                            }
                        }
                    )
                }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.padding(padding)
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            viewModel = viewModel,
                            onNavigateToImport = { navController.navigate(Screen.Import.route) },
                            onNavigateToCases = { navController.navigate(Screen.Cases.route) },
                            onNavigateToEngineers = { navController.navigate(Screen.Engineers.route) },
                            onNavigateToFees = { navController.navigate(Screen.Fees.route) },
                            onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                            onNavigateToFollowUp = { navController.navigate(Screen.FollowUp.route) },
                            onNavigateToExport = { navController.navigate(Screen.Export.route) },
                            onNavigateToUpdate = { navController.navigate(Screen.Update.route) },
                            onNavigateToReportGenerator = { navController.navigate(Screen.ReportGenerator.route) },
                            onNavigateToReportHistory = { navController.navigate(Screen.ReportHistory.route) },
                            onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                        )
                    }
                    composable(Screen.Update.route) {
                        UpdateScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Cases.route) {
                        CasesScreen(
                            viewModel = viewModel,
                            onCaseClick = { caseNumber ->
                                navController.navigate(Screen.CaseDetail.createRoute(caseNumber))
                            }
                        )
                    }
                    composable(
                        route = Screen.CaseDetail.route,
                        arguments = listOf(navArgument("caseNumber") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encoded = backStackEntry.arguments?.getString("caseNumber") ?: ""
                        val caseNumber = try { URLDecoder.decode(encoded, "UTF-8") } catch (e: Exception) { encoded }
                        LaunchedEffect(caseNumber) { viewModel.loadCaseDetail(caseNumber) }
                        CaseDetailScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Engineers.route) {
                        EngineersScreen(
                            viewModel = viewModel,
                            onCaseClick = { caseNumber ->
                                navController.navigate(Screen.EngineerDetail.createRoute(caseNumber))
                            }
                        )
                    }
                    composable(
                        route = Screen.EngineerDetail.route,
                        arguments = listOf(navArgument("caseNumber") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encoded = backStackEntry.arguments?.getString("caseNumber") ?: ""
                        val caseNumber = try { URLDecoder.decode(encoded, "UTF-8") } catch (e: Exception) { encoded }
                        LaunchedEffect(caseNumber) { viewModel.loadCaseDetail(caseNumber) }
                        com.nezammonitor.app.ui.screens.engineers.EngineerDetailScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Fees.route) {
                        FeesScreen(
                            viewModel = viewModel,
                            onCaseClick = { caseNumber ->
                                navController.navigate(Screen.FeeDetail.createRoute(caseNumber))
                            }
                        )
                    }
                    composable(
                        route = Screen.FeeDetail.route,
                        arguments = listOf(navArgument("caseNumber") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encoded = backStackEntry.arguments?.getString("caseNumber") ?: ""
                        val caseNumber = try { URLDecoder.decode(encoded, "UTF-8") } catch (e: Exception) { encoded }
                        LaunchedEffect(caseNumber) { viewModel.loadCaseDetail(caseNumber) }
                        com.nezammonitor.app.ui.screens.fees.FeeDetailScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Reports.route) {
                        ReportsScreen(
                            viewModel = viewModel,
                            onCaseClick = { caseNumber ->
                                navController.navigate(Screen.ReportDetail.createRoute(caseNumber))
                            }
                        )
                    }
                    composable(
                        route = Screen.ReportDetail.route,
                        arguments = listOf(navArgument("caseNumber") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encoded = backStackEntry.arguments?.getString("caseNumber") ?: ""
                        val caseNumber = try { URLDecoder.decode(encoded, "UTF-8") } catch (e: Exception) { encoded }
                        LaunchedEffect(caseNumber) { viewModel.loadCaseDetail(caseNumber) }
                        com.nezammonitor.app.ui.screens.reports.ReportDetailScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.FollowUp.route) {
                        FollowUpScreen(viewModel = viewModel)
                    }
                    composable(Screen.Receipts.route) {
                        // دریافتی‌ها = تب دوم همان صفحه حق‌الزحمه (مطابق ویندوز FeesView: PanelFees / PanelReceipts)
                        FeesScreen(viewModel = viewModel, initialTab = 1)
                    }
                    composable(Screen.ReportGenerator.route) {
                        ReportGeneratorScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(Screen.ReportHistory.route) {
                        ReportHistoryScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(viewModel = viewModel)
                    }
                    composable(Screen.Import.route) {
                        HomeScreen(viewModel = viewModel)
                    }
                    composable(Screen.Export.route) {
                        ExportScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
