package com.example.pepperaibot

import android.util.Log
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import com.example.pepperaibot.ui.theme.PepperAIBotTheme

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
    var voiceRecognition by remember {
        mutableStateOf(sharedPreferences.getBoolean("voice_recognition", false))
    }
    var voiceRecognitionApiUrl by remember {
        mutableStateOf(sharedPreferences.getString("voice_recognition_api_url", "") ?: "")
    }
    var voiceRecognitionApiKey by remember {
        mutableStateOf(sharedPreferences.getString("voice_recognition_api_key", "") ?: "")
    }
    var voiceRecognitionModel by remember {
        mutableStateOf(sharedPreferences.getString("voice_recognition_model", "") ?: "")
    }
    var readTimeout by remember {
        mutableStateOf(sharedPreferences.getLong("api_read_timeout", 30).toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Changes to settings require restarting the app.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // API URL
        Text("Enter the API URL:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = apiUrl,
            onValueChange = {
                apiUrl = it
                sharedPreferences.edit().putString("api_url", it).apply()
            },
            label = { Text("API URL") },
            modifier = Modifier.fillMaxWidth()
        )

        // API Key
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter the API key:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                sharedPreferences.edit().putString("api_key", it).apply()
            },
            label = { Text("API KEY") },
            modifier = Modifier.fillMaxWidth()
        )

        // AI Model
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter the AI Model:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = aiModel,
            onValueChange = {
                aiModel = it
                sharedPreferences.edit().putString("ai_model", it).apply()
            },
            label = { Text("AI Model") },
            modifier = Modifier.fillMaxWidth()
        )

        // voice_recognition Checkbox
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = voiceRecognition,
                onCheckedChange = {
                    voiceRecognition = it
                    sharedPreferences.edit().putBoolean("voice_recognition", it).apply()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use your own voice recognition", style = MaterialTheme.typography.bodyLarge)
        }

        // If own voice recognition checkbox is checked
        if (voiceRecognition) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Voice recognition API URL:", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = voiceRecognitionApiUrl,
                onValueChange = {
                    voiceRecognitionApiUrl = it
                    sharedPreferences.edit().putString("voice_recognition_api_url", it).apply()
                },
                label = { Text("Voice recognition API URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Voice recognition API Key:", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = voiceRecognitionApiKey,
                onValueChange = {
                    voiceRecognitionApiKey = it
                    sharedPreferences.edit().putString("voice_recognition_api_key", it).apply()
                },
                label = { Text("Voice recognition API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Voice Recognition Model:", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = voiceRecognitionModel,
                onValueChange = {
                    voiceRecognitionModel = it
                    sharedPreferences.edit().putString("voice_recognition_model", it).apply()
                },
                label = { Text("Voice Recognition Model") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Wait timeout
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter the API wait timeout:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = readTimeout,
            onValueChange = {
                if(it.isDigitsOnly()) {
                    readTimeout = it
                    val timeout = it.toLongOrNull() ?: 30L
                    sharedPreferences.edit().putLong("api_read_timeout", timeout).apply()
                }
            },
            label = { Text("API wait timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
