package com.example.pepperaibot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.pepperaibot.ui.theme.PepperAIBotTheme


class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PepperAIBotTheme {
                Text("Settings Screen")
            }
        }
    }
}
