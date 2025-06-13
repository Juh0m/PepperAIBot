package com.example.pepperaibot

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.example.pepperaibot.MainActivity.AIApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


class MainViewModel(application: Application) : AndroidViewModel(application) {
    var isListening = mutableStateOf(false)
    var userInput = mutableStateOf("")
    var aiResponse = mutableStateOf("")
    var listeningText = mutableStateOf("")

    var onStartListening: (() -> Unit)? = null
    var onStopListening: (() -> Unit)? = null

    // Retrofit Client
    // For HTTP Request to AI API
    object RetrofitClient {
        private lateinit var apiKey: String
        private lateinit var apiUrl: String
        private var readTimeout : Long = 30 // temporary init value
        private lateinit var api: AIApi
        private lateinit var authInterceptor: Interceptor

        lateinit var aiModel: String

        private var isInitialized = false

        fun setup(context: Context) {
            val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            apiKey = sharedPrefs.getString("api_key", "") ?: ""
            apiUrl = sharedPrefs.getString("api_url", "http://google.com/") ?: ""
            aiModel = sharedPrefs.getString("api_model", "") ?: ""
            readTimeout = sharedPrefs.getLong("api_read_timeout", 30L)

            authInterceptor = Interceptor { chain ->
                val newRequest = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(newRequest)
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .build()

            try {
                api = Retrofit.Builder()
                    .baseUrl(apiUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                    .create(AIApi::class.java)
            }
            catch (e: Exception) {
                api = Retrofit.Builder()
                    .baseUrl("http://google.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                    .create(AIApi::class.java)
                Log.e("RetrofitClient", "Building api failed (check url?), $e")
            }

            isInitialized = true
        }

        fun getApi(): AIApi {
            if (!isInitialized) {
                throw IllegalStateException("RetrofitClient is not initialized. Call setup(context) first.")
            }
            return api
        }
    }

    fun getRetrofitClient(): RetrofitClient {
        return RetrofitClient
    }


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
        } else {
            userInput.value = text
        }
    }

    fun updateAIResponse(response: String) {
        aiResponse.value = "Pepper: $response"
    }

    fun updateListeningText(text: String) {
        listeningText.value = text
    }
}
