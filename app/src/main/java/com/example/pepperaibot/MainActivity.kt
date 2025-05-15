package com.example.pepperui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.example.pepperui.ui.theme.PepperUITheme

class MainActivity : ComponentActivity(), RobotLifecycleCallbacks {

    private val viewModel by viewModels<MainViewModel>()
    private val TAG = "MainActivity"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.initVosk(applicationContext)
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register Pepper SDK (safe even if not on a robot)
        try {
            QiSDK.register(this, this)
        } catch (e: Exception) {
            Log.w(TAG, "QiSDK registration failed — not running on Pepper?", e)
        }

        // Ask mic permission and init Vosk
        checkMicPermission()

        setContent {
            PepperUITheme {
                MainScreen(
                    viewModel = viewModel,
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }

    private fun checkMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.initVosk(applicationContext)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopRecognition()
        QiSDK.unregister(this, this)
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        viewModel.setQiContext(qiContext)
    }

    override fun onRobotFocusLost() {
        viewModel.clearQiContext()
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.e(TAG, "Pepper focus refused: $reason")
    }
}
