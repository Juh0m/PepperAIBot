package com.example.pepperaibot
import android.*
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aldebaran.qi.sdk.*
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.example.pepperaibot.ui.theme.PepperAIBotTheme
import java.io.*
import java.nio.*
import java.util.zip.ZipInputStream
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.*
import retrofit2.Call
import retrofit2.http.*
import retrofit2.http.Headers

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    // Tag for logging
    private val tag = "PepperAI"

    // Audio stuff
    private val sampleRate = 16000
    private val bufferSizeElements = 65536
    private val recordAudioRequestCode = 101

    // qiContext needed for making Pepper talk, needs focus
    private var qiContext: QiContext? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var model: Model

    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private var isModelInitialized = false

    private val conversation = mutableListOf<Message>()

    private val viewModel by viewModels<MainViewModel>()

    // Retrofit client for HTTP requests
    private lateinit var retrofitClient : MainViewModel.RetrofitClient

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            initVosk()
        } else {
            Toast.makeText(this, "Permissions required for speech recognition", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retrofitClient = viewModel.getRetrofitClient()
        retrofitClient.setup(applicationContext)
        viewModel.onStartListening = {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    recordAudioRequestCode
                )
            }
        }

        viewModel.onStopListening = { stopRecording() }
        setContent {
            PepperAIBotTheme {
                MainScreen(viewModel = viewModel) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
              }
        }

        try {
            QiSDK.register(this, this)
        } catch (e: SecurityException) {
            Log.e("QiSDK", "Failed to register QiSDK due to SecurityException", e)
        }
        checkPermissions()
    }

    // Check microphone permissions
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            initVosk()
        }
    }

    private fun initVosk() {
        LibVosk.setLogLevel(LogLevel.INFO)
        scope.launch {
            try {
                viewModel.updateListeningText("Loading model...")
                withContext(Dispatchers.IO) {
                    val modelDir = File(filesDir, "model")
                    if (!modelDir.exists()) {
                        modelDir.mkdirs()
                        extractAssets("model-en-us.zip", modelDir)
                    }
                    model = Model(modelDir.absolutePath)
                    isModelInitialized = true
                }
                viewModel.updateListeningText("Ready for speech recognition")
            } catch (e: IOException) {
                Log.e(tag, "Model init error", e)
                viewModel.updateListeningText("Model error: ${e.message}")
            }
        }
    }

    private fun extractAssets(zipName: String, targetDir: File) {
        assets.open(zipName).use { inputStream ->
            ZipInputStream(inputStream).use { zipInputStream ->
                var zipEntry = zipInputStream.nextEntry
                while (zipEntry != null) {
                    val newFile = File(targetDir, zipEntry.name)
                    if (zipEntry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        File(newFile.parent!!).mkdirs()
                        FileOutputStream(newFile).use { out ->
                            val buffer = ByteArray(4096)
                            var len: Int
                            while (zipInputStream.read(buffer).also { len = it } > 0) {
                                out.write(buffer, 0, len)
                            }
                        }
                    }
                    zipEntry = zipInputStream.nextEntry
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        if (!isModelInitialized) return

        try {
            recognizer = Recognizer(model, sampleRate.toFloat())
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            )

            audioRecord?.startRecording()
            isRecording = true
            viewModel.updateListeningText("Listening...")

            val buffer = ShortArray(bufferSizeElements)
            recordingJob = scope.launch(Dispatchers.IO) {
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        val byteBuffer = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until read) byteBuffer.putShort(buffer[i])
                        val data = byteBuffer.array()

                        if (recognizer?.acceptWaveForm(data, data.size) == true) {
                            val result = recognizer?.result ?: ""
                            withContext(Dispatchers.Main) {
                                processResult(result, true)
                            }
                        } else {
                            val partial = recognizer?.partialResult ?: ""
                            withContext(Dispatchers.Main) {
                                processResult(partial, false)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Recording failed", e)
            viewModel.updateListeningText("Recording error: ${e.message}")
        }
    }

    private fun processResult(result: String, isFinal: Boolean) {
        val jsonObject = JSONObject(result)
        val text = if (isFinal) jsonObject.optString("text", "") else jsonObject.optString("partial", "")
        if (text.isNotEmpty()) {
            if(isFinal) {
                conversation.add(Message("user", text))
                viewModel.updateUserText(text, true)
                viewModel.toggleListening()
                // Get Pepper's response from specified API
                // Only works with OpenAI or similar APIs
                conversation.add(Message("user", text))
                val request = ChatRequest(
                    model = retrofitClient.aiModel,
                    messages = conversation
                )
                val api = retrofitClient.getApi()
                api.getChatCompletion(request).enqueue(object : retrofit2.Callback<ChatResponse> {
                    override fun onResponse(call: Call<ChatResponse>, response: retrofit2.Response<ChatResponse>) {
                        if (response.isSuccessful) {
                            val reply = response.body()?.choices?.firstOrNull()?.message?.content
                            conversation.add(Message("assistant", reply.toString()))
                            Log.e(tag, "Full AI Reply: $reply")

                            if (reply != null) {
                                viewModel.updateAIResponse(reply)
                                robotSay(reply)
                            }
                        } else {
                            // Connection to API was successful but response not OK
                            conversation.add(Message("assistant", "AI was unable to respond."))
                            Log.e(tag, "Error: ${response.code()} ${response.errorBody()?.string()}")
                            viewModel.updateAIResponse("API responded with error ${response.code()}. Please check your API key and AI model.")
                        }
                    }

                    // Failure to connect to API.
                    override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                        t.printStackTrace()
                        viewModel.updateAIResponse("Failed to access API. Please check your API URL.")
                    }
                })
            }
            else {
                viewModel.updateUserText("Converting speech to text in progress: $text", false)
            }
        }
    }

    private fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recognizer?.close()
        recognizer = null
        isRecording = false
        viewModel.updateListeningText("Stopped")
    }

    // HTTP REQUESTS
    // Request
    data class Message(
        val role: String,
        val content: String
    )

    data class ChatRequest(
        val model: String,
        val messages: List<Message>
    )

    // Response
    data class ChatResponse(
        val choices: List<Choice>
    )

    data class Choice(
        val index: Int,
        val message: Message,
        val finish_reason: String
    )

    interface AIApi {
        @Headers("Content-Type: application/json")
        @POST("v1/chat/completions")
        fun getChatCompletion(
            @Body request: ChatRequest
        ): Call<ChatResponse>
    }


    // PEPPER QiSDK STUFF
    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        if (::model.isInitialized) model.close()
        QiSDK.unregister(this, this)
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.d(tag, "Robot focus gained")
        this.qiContext = qiContext
    }

    override fun onRobotFocusLost() {
        Log.d(tag, "Robot focus lost")
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(tag, "Robot focus refused: $reason")
    }

    private fun robotSay(text: String) {
        qiContext?.let { context ->
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    SayBuilder.with(context)
                        .withText(text)
                        .build()
                        .run()
                } catch (e: Exception) {
                    Log.e("Speech", "Failed to make Pepper speak", e)
                    viewModel.updateListeningText("Pepper was unable to speak.")
                }
            }
        } ?: Log.w("Speech", "QiContext is not available yet")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(viewModel: MainViewModel, onSettingsClick: () -> Unit) {
        var isListening = viewModel.isListening.value
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Pepper Chat") },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Press the button to talk with Pepper.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(viewModel.listeningText.value, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(viewModel.userInput.value, modifier = Modifier.fillMaxWidth().background(Color(0xFFE0F7FA)).padding(8.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(viewModel.aiResponse.value, modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F8E9)).padding(8.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FloatingActionButton(
                        onClick = {
                            if (!isListening) {
                                viewModel.toggleListening()
                            }
                        },
                        containerColor = if (viewModel.isListening.value) Color(0xFFD32F2F) else Color(0xFF6200EE),

                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Mic", tint = Color.White)
                    }
                }
            }
        }
    }
}
