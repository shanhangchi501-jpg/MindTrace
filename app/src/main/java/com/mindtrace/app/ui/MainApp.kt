package com.mindtrace.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.ui.components.WarmBackground

private object Routes {
    const val HOME = "home"
    const val MEDICATIONS = "medications"
    const val INSIGHTS = "insights"
    const val LEARN = "learn"
    const val LEARN_DETAIL = "learn/{id}"
    const val ME = "me"
    const val SETTINGS = "settings"

    const val CHECK_IN = "checkin/{date}"
    const val JOURNAL_EDIT = "journal_edit/{journalId}/{date}"
    const val MED_EDIT = "med_edit/{medId}"
    const val SCALES = "scales"
    const val SCALES_ALL = "scales_all"
    const val SCALE = "scale/{type}"
    const val REPORT = "report"
    const val SAFETY = "safety"
    const val CRISIS = "crisis"
    const val PROFILE = "profile"
    const val SYMPTOMS = "symptoms"
    const val DIAGNOSES = "diagnoses"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val SUPPORT = "support"
    const val REMINDER_HELP = "reminder_help"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "今天", Icons.Rounded.Today),
    Tab(Routes.MEDICATIONS, "药物", Icons.Rounded.Medication),
    Tab(Routes.INSIGHTS, "趋势", Icons.Rounded.Insights),
    Tab(Routes.LEARN, "科普", Icons.AutoMirrored.Rounded.MenuBook),
    Tab(Routes.ME, "我的", Icons.Rounded.Person)
)

@Composable
fun MainApp(
    viewModel: AppViewModel,
    initialDestination: String?,
    onDestinationConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    LaunchedEffect(initialDestination) {
        when (initialDestination) {
            "checkin" -> {
                navController.navigate("checkin/${DateUtils.todayIso()}")
                onDestinationConsumed()
            }
            "medications" -> {
                navController.navigate(Routes.MEDICATIONS)
                onDestinationConsumed()
            }
            null -> Unit
            else -> onDestinationConsumed()
        }
    }

    WarmBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    if (tab.route == Routes.HOME) {
                                        // 首页是起始目的地。直接弹回首页，不依赖
                                        // restoreState，避免偶发点击后仍停留在其他 Tab。
                                        if (!navController.popBackStack(
                                                Routes.HOME,
                                                inclusive = false
                                            )
                                        ) {
                                            navController.navigate(Routes.HOME) {
                                                launchSingleTop = true
                                            }
                                        }
                                    } else {
                                        navController.navigate(tab.route) {
                                            popUpTo(
                                                navController.graph.findStartDestination().id
                                            ) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel = viewModel,
                        onCheckIn = { date -> navController.navigate("checkin/$date") },
                        onAddJournal = { date -> navController.navigate("journal_edit/0/$date") },
                        onEditJournal = { id ->
                            val entry = viewModel.journal.value.firstOrNull { it.id == id }
                            navController.navigate("journal_edit/$id/${entry?.date ?: DateUtils.todayIso()}")
                        },
                        onOpenMedications = { navController.navigate(Routes.MEDICATIONS) },
                        onOpenInsights = { navController.navigate(Routes.INSIGHTS) },
                        onOpenScales = { navController.navigate(Routes.SCALES) },
                        onOpenReport = { navController.navigate(Routes.REPORT) },
                        onOpenSafety = { navController.navigate(Routes.SAFETY) },
                        onOpenCrisis = { navController.navigate(Routes.CRISIS) }
                    )
                }

                composable(Routes.MEDICATIONS) {
                    MedicationsScreen(
                        viewModel = viewModel,
                        onAddMedication = { navController.navigate("med_edit/0") },
                        onEditMedication = { id -> navController.navigate("med_edit/$id") },
                        onBackdatedCheckIn = { date -> navController.navigate("checkin/$date") }
                    )
                }

                composable(Routes.INSIGHTS) {
                    InsightsScreen(
                        viewModel = viewModel,
                        onOpenScales = { navController.navigate(Routes.SCALES) },
                        onOpenReport = { navController.navigate(Routes.REPORT) }
                    )
                }

                composable(Routes.LEARN) {
                    LearnScreen(
                        onOpenEntry = { id -> navController.navigate("learn/$id") }
                    )
                }

                composable(
                    route = Routes.LEARN_DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.StringType })
                ) { entry ->
                    LearnDetailScreen(
                        entryId = entry.arguments?.getString("id") ?: "",
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.ME) {
                    SettingsScreen(
                        viewModel = viewModel,
                        settingsOnly = false,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onEditProfile = { navController.navigate(Routes.PROFILE) },
                        onEditSymptoms = { navController.navigate(Routes.SYMPTOMS) },
                        onOpenDiagnoses = { navController.navigate(Routes.DIAGNOSES) },
                        onOpenSafety = { navController.navigate(Routes.SAFETY) },
                        onOpenCrisis = { navController.navigate(Routes.CRISIS) },
                        onOpenReport = { navController.navigate(Routes.REPORT) }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        settingsOnly = true,
                        onBack = { navController.popBackStack() },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                        onOpenReminderHelp = { navController.navigate(Routes.REMINDER_HELP) },
                        onOpenTerms = { navController.navigate(Routes.TERMS) },
                        onOpenSupport = { navController.navigate(Routes.SUPPORT) }
                    )
                }

                composable(
                    route = Routes.CHECK_IN,
                    arguments = listOf(navArgument("date") { type = NavType.StringType })
                ) { entry ->
                    val date = entry.arguments?.getString("date") ?: DateUtils.todayIso()
                    CheckInScreen(
                        viewModel = viewModel,
                        date = date,
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                        onOpenMedications = { navController.navigate(Routes.MEDICATIONS) },
                        onOpenCrisis = { navController.navigate(Routes.CRISIS) },
                        onOpenSafety = { navController.navigate(Routes.SAFETY) }
                    )
                }

                composable(
                    route = Routes.JOURNAL_EDIT,
                    arguments = listOf(
                        navArgument("journalId") { type = NavType.LongType },
                        navArgument("date") { type = NavType.StringType }
                    )
                ) { entry ->
                    JournalEditScreen(
                        viewModel = viewModel,
                        journalId = entry.arguments?.getLong("journalId") ?: 0L,
                        initialDate = entry.arguments?.getString("date") ?: DateUtils.todayIso(),
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.MED_EDIT,
                    arguments = listOf(navArgument("medId") { type = NavType.LongType })
                ) { entry ->
                    MedicationEditScreen(
                        viewModel = viewModel,
                        medicationId = entry.arguments?.getLong("medId") ?: 0L,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SCALES) {
                    ScalesScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onStartScale = { type -> navController.navigate("scale/$type") },
                        onOpenAllScales = { navController.navigate(Routes.SCALES_ALL) }
                    )
                }

                composable(Routes.SCALES_ALL) {
                    ScalesAllScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onStartScale = { type -> navController.navigate("scale/$type") }
                    )
                }

                composable(
                    route = Routes.SCALE,
                    arguments = listOf(navArgument("type") { type = NavType.StringType })
                ) { entry ->
                    ScaleScreen(
                        viewModel = viewModel,
                        type = entry.arguments?.getString("type") ?: "PHQ9",
                        onBack = { navController.popBackStack() },
                        onOpenCrisis = { navController.navigate(Routes.CRISIS) }
                    )
                }

                composable(Routes.REPORT) {
                    ReportScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SAFETY) {
                    SafetyScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onCrisis = { navController.navigate(Routes.CRISIS) }
                    )
                }

                composable(Routes.CRISIS) {
                    CrisisScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenSafety = { navController.navigate(Routes.SAFETY) }
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileEditScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SYMPTOMS) {
                    SymptomSettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.DIAGNOSES) {
                    DiagnosisScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                        onOpenSafety = { navController.navigate(Routes.SAFETY) },
                        onOpenTerms = { navController.navigate(Routes.TERMS) },
                        onOpenSupport = { navController.navigate(Routes.SUPPORT) }
                    )
                }

                composable(Routes.PRIVACY) {
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.TERMS) {
                    TermsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SUPPORT) {
                    SupportScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.REMINDER_HELP) {
                    ReminderHelpScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
