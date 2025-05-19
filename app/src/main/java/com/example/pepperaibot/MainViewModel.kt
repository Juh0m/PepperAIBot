package com.example.pepperaibot

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    var isListening = mutableStateOf(false)
    var userInput = mutableStateOf("")
    var aiResponse = mutableStateOf("")
    var listeningText = mutableStateOf("")

    var onStartListening: (() -> Unit)? = null
    var onStopListening: (() -> Unit)? = null

    fun toggleListening() {
        if (isListening.value) {
            onStopListening?.invoke()
        } else {
            onStartListening?.invoke()
        }
        isListening.value = !isListening.value
    }

    fun updateUserText(text: String, isFinal: Boolean) {
        if (isFinal) {
            userInput.value = "You: $text"
            updateAIResponse("<insert pepper response here>")
        } else {
            userInput.value = "Listening: $text"
        }
    }

    fun updateAIResponse(response: String) {
        aiResponse.value = "Pepper: $response"
    }

    fun updateListeningText(text: String) {
        listeningText.value = text
    }
}
