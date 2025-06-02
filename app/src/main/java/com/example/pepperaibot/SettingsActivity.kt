package com.example.pepperaibot

import android.util.Log
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
    var ownTextToSpeech by remember {
        mutableStateOf(sharedPreferences.getBoolean("own_text_to_speech", false))
    }
    var ttsApiUrl by remember {
        mutableStateOf(sharedPreferences.getString("tts_api_url", "") ?: "")
    }
    var ttsApiKey by remember {
        mutableStateOf(sharedPreferences.getString("tts_api_key", "") ?: "")
    }
    var ttsModel by remember {
        mutableStateOf(sharedPreferences.getString("tts_model", "") ?: "")
    }

    if(ownTextToSpeech) {
            Log.e("ok lol", "oklol")
        }
        else {
            Log.e("lolok", "lolok")
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Settings Screen", style = MaterialTheme.typography.headlineSmall)
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

        // OwnTextToSpeech Checkbox
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = ownTextToSpeech,
                onCheckedChange = {
                    ownTextToSpeech = it
                    sharedPreferences.edit().putBoolean("own_text_to_speech", it).apply()
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use Own Text-To-Speech", style = MaterialTheme.typography.bodyLarge)
        }

// Conditionally show TTS fields
        if (ownTextToSpeech) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Text-To-Speech API URL:", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = ttsApiUrl,
                onValueChange = {
                    ttsApiUrl = it
                    sharedPreferences.edit().putString("tts_api_url", it).apply()
                },
                label = { Text("TTS API URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Text-To-Speech API Key:", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = ttsApiKey,
                onValueChange = {
                    ttsApiKey = it
                    sharedPreferences.edit().putString("tts_api_key", it).apply()
                },
                label = { Text("TTS API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Text-To-Speech Model:", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = ttsModel,
                onValueChange = {
                    ttsModel = it
                    sharedPreferences.edit().putString("tts_model", it).apply()
                },
                label = { Text("TTS Model") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

