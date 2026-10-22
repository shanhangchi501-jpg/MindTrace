package com.mindtrace.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindtrace.app.data.AppLocale
import com.mindtrace.app.ui.AppViewModel
import com.mindtrace.app.ui.LockScreen
import com.mindtrace.app.ui.MainApp
import com.mindtrace.app.ui.OnboardingScreen
import com.mindtrace.app.ui.i18n.LocalAppLanguage
import com.mindtrace.app.ui.theme.MindTraceTheme

class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private var notificationDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationDestination = intent.getStringExtra(EXTRA_DESTINATION)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val language = AppLocale.fromCode(settings.language)
            CompositionLocalProvider(LocalAppLanguage provides language) {
                MindTraceTheme {
                    SecureScreen(enabled = settings.preventScreenshots)
                    when {
                        !settings.onboardingComplete -> OnboardingScreen(viewModel)
                        viewModel.locked -> LockScreen(viewModel)
                        else -> MainApp(
                            viewModel = viewModel,
                            initialDestination = notificationDestination,
                            onDestinationConsumed = { notificationDestination = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination = intent.getStringExtra(EXTRA_DESTINATION)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
    }
}

@Composable
private fun SecureScreen(enabled: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    LaunchedEffect(enabled, window) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
