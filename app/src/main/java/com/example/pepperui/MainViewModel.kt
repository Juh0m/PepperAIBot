package com.example.pepperui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    var isListening = mutableStateOf(false)
    var userInput = mutableStateOf("")
    var aiResponse = mutableStateOf("")

    fun toggleListening() {
        isListening.value = !isListening.value

        // Placeholder logic for demo:
        if (isListening.value) {
            userInput.value = "You: Hello, Pepper!"
            aiResponse.value = "Pepper: Hello! How can I assist you today?"
        }
    }
}
