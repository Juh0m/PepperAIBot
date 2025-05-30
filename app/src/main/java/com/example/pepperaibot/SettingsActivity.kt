package com.example.pepperaibot

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.pepperaibot.ui.theme.PepperAIBotTheme
import androidx.core.content.edit

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PepperAIBotTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var apiUrl by remember {
        mutableStateOf(sharedPreferences.getString("api_url", "") ?: "")
    }
    var apiKey by remember {
        mutableStateOf(sharedPreferences.getString("api_key", "") ?: "")
    }
    var aiModel by remember {
        mutableStateOf(sharedPreferences.getString("ai_model", "") ?: "")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Settings Screen", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter the API URL:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = apiUrl,
            onValueChange = {
                apiUrl = it
                sharedPreferences.edit { putString("api_url", it) }
            },
            label = { Text("API URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Enter the API key:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                sharedPreferences.edit { putString("api_key", it) }
            },
            label = { Text("API KEY") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Enter the AI Model:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = aiModel,
            onValueChange = {
                aiModel = it
                sharedPreferences.edit { putString("ai_model", it) }
            },
            label = { Text("AI Model") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
