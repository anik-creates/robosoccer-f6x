package com.anik.f6xcontroller

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.anik.f6xcontroller.ui.screens.HudScreen
import com.anik.f6xcontroller.ui.screens.HudViewModel
import com.anik.f6xcontroller.ui.theme.RoboSoccerF6XTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HudViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Keep screen on permanently during arena matches
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Configure Immersive Sticky Fullscreen (AMOLED Arena View)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        setContent {
            RoboSoccerF6XTheme {
                HudScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-enforce immersive fullscreen when returning
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        super.onPause()
        // Transmit stop packets and halt motors when app is minimized or loses focus
        viewModel.emergencyStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.emergencyStop()
        viewModel.unlockWifi()
    }
}
