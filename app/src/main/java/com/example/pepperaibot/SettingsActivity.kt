package com.example.pepperaibot

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
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

    // Default system prompt
    val defaultPrompt = """
Avoid very long responses, but maintain detail. Find a balance between length and conciseness.

You are Pepper, a robot at \"Sote Virtual Lab\" at Tampere University of Applied Sciences (TAMK).

Remain factual. Do not say a fact unless you are certain it is true.

Answer in English.

Only introduce yourself once, unless explicitly prompted to do otherwise.

Speak in human-like, relaxed and natural language.

If user asks about the meaning of life, answer that the meaning of life is \"42\".

If user says \"seven twenty seven\", answer with \"When you see it!\". It is an \"osu!\" reference.
""".trimIndent()

    // Core API settings
    var apiUrl by remember { mutableStateOf(sharedPreferences.getString("api_url", "") ?: "") }
    var apiKey by remember { mutableStateOf(sharedPreferences.getString("api_key", "") ?: "") }
    var aiModel by remember { mutableStateOf(sharedPreferences.getString("ai_model", "") ?: "") }
    // System Prompt with default fallback
    var systemPrompt by remember {
        mutableStateOf(
            sharedPreferences.getString("system_prompt", null)?.takeIf { it.isNotBlank() }
                ?: defaultPrompt
        )
    }
    var readTimeout by remember { mutableStateOf(sharedPreferences.getLong("api_read_timeout", 60).toString()) }
    var voiceRecognition by remember { mutableStateOf(sharedPreferences.getBoolean("voice_recognition", false)) }
    var voiceRecognitionApiUrl by remember { mutableStateOf(sharedPreferences.getString("voice_recognition_api_url", "") ?: "") }
    var voiceRecognitionApiKey by remember { mutableStateOf(sharedPreferences.getString("voice_recognition_api_key", "") ?: "") }
    var voiceRecognitionModel by remember { mutableStateOf(sharedPreferences.getString("voice_recognition_model", "") ?: "") }
    var voiceRecognitionReadTimeout by remember { mutableStateOf(sharedPreferences.getLong("voice_recognition_read_timeout", 60).toString()) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Settings", color = Color.White)
        Text("Changes to voice recognition settings require restarting the app.", color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        // API URL
        Text("Enter the API URL:", color = Color.White)
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

        // API Key
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter the API key:", color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                sharedPreferences.edit { putString("api_key", it) }
            },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth()
        )

        // AI Model
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter the AI Model:", color = Color.White)
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

        // System Prompt
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter the System Prompt:", color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = systemPrompt,
            onValueChange = {
                systemPrompt = it
                sharedPreferences.edit { putString("system_prompt", it) }
            },
            label = { Text("System Prompt") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            keyboardOptions = KeyboardOptions.Default
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Reset to default button
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
                systemPrompt = defaultPrompt
                sharedPreferences.edit { putString("system_prompt", defaultPrompt) }
            }) {
                Text("Reset Prompt")
            }
        }

        // Wait timeout for AI API
        Spacer(modifier = Modifier.height(16.dp))
        Text("Enter the AI API wait timeout:", color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = readTimeout,
            onValueChange = {
                if (it.isDigitsOnly()) {
                    readTimeout = it
                    val timeout = it.toLongOrNull() ?: 60L
                    sharedPreferences.edit { putLong("api_read_timeout", timeout) }
                }
            },
            label = { Text("API wait timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        // External speech to text
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = voiceRecognition,
                onCheckedChange = {
                    voiceRecognition = it
                    sharedPreferences.edit { putBoolean("voice_recognition", it) }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use your own voice recognition", color = Color.White)
        }

        // If own voice recognition checkbox is checked
        if (voiceRecognition) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Voice recognition API URL:", color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = voiceRecognitionApiUrl,
                onValueChange = {
                    voiceRecognitionApiUrl = it
                    sharedPreferences.edit { putString("voice_recognition_api_url", it) }
                },
                label = { Text("Voice recognition API URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Voice recognition API Key:", color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = voiceRecognitionApiKey,
                onValueChange = {
                    voiceRecognitionApiKey = it
                    sharedPreferences.edit { putString("voice_recognition_api_key", it) }
                },
                label = { Text("Voice recognition API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Voice Recognition Model:", color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = voiceRecognitionModel,
                onValueChange = {
                    voiceRecognitionModel = it
                    sharedPreferences.edit { putString("voice_recognition_model", it) }
                },
                label = { Text("Voice Recognition Model") },
                modifier = Modifier.fillMaxWidth()
            )

            // Wait timeout for voice recognition API
            Spacer(modifier = Modifier.height(16.dp))
            Text("Enter the voice recognition API wait timeout:", color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = voiceRecognitionReadTimeout,
                onValueChange = {
                    if (it.isDigitsOnly()) {
                        voiceRecognitionReadTimeout = it
                        val timeout = it.toLongOrNull() ?: 60L
                        sharedPreferences.edit { putLong("voice_recognition_read_timeout", timeout) }
                    }
                },
                label = { Text("API wait timeout (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}
