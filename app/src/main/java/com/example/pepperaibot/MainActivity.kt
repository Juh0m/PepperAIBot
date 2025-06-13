package com.example.pepperaibot
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.`object`.locale.Language
import com.aldebaran.qi.sdk.`object`.locale.Locale
import com.aldebaran.qi.sdk.`object`.locale.Region
import com.example.pepperaibot.ui.theme.PepperAIBotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    // Tag for logging
    private val tag = "PepperAI"

    // Audio stuff
    private val sampleRate = 16000
    private val bufferSizeElements = 65536
    private val recordAudioRequestCode = 101

    private var outputFilePath: String = ""
    private var isRecordingFile = false
    private var mediaRecorder: MediaRecorder? = null

    private lateinit var retrofit : Retrofit
    private lateinit var service : FileUploadService
    private lateinit var okHttpClient : OkHttpClient
    // qiContext needed for making Pepper talk, needs focus
    private var qiContext: QiContext? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var model: Model

    private lateinit var sharedPrefs : SharedPreferences
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private var isModelInitialized = false
    private var isTranscribing = false

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
        sharedPrefs = applicationContext.getSharedPreferences("app_settings", MODE_PRIVATE)
        retrofitClient = viewModel.getRetrofitClient()
        retrofitClient.setup(applicationContext)
        setupSTTClients()
        val localVoiceRecognition = sharedPrefs.getBoolean("voice_recognition", false)
        viewModel.onStartListening = {
            if (localVoiceRecognition && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startFileRecording(applicationContext)
            } else if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    recordAudioRequestCode

                )
            }
        }

        viewModel.onStopListening = {
            if(localVoiceRecognition) {
                stopFileRecording()
            }
            else {
                stopRecording()
            }
        }
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
    override fun onResume() {
        super.onResume()
        retrofitClient = viewModel.getRetrofitClient()
        retrofitClient.setup(applicationContext)
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
    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE])
    fun startFileRecording(context: Context) {
        if (isRecordingFile) return

        try {
            // Prepare output file
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            if (dir == null) throw IOException("Cannot access external files dir")
            val filename = "audio.aac"
            outputFilePath = File(dir, filename).absolutePath

            // Configure MediaRecorder
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Not sure what these should be set to
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outputFilePath)
                prepare()
                start()
            }

            isRecordingFile = true
            viewModel.updateListeningText("Recording to file…")
        } catch (e: Exception) {
            Log.e(tag, "startFileRecording failed", e)
            viewModel.updateListeningText("Recording error: ${e.localizedMessage}")
            // Clean up partial recorder
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingFile = false
        }
    }

    private fun stopFileRecording() {
        if (!isRecordingFile || mediaRecorder == null) return

        try {
            mediaRecorder!!.apply {
                stop()
                reset()
                release()
            }

            getLocalSTTResponse()

        } catch (e: Exception) {
            Log.e(tag, "stopFileRecording failed", e)
            viewModel.updateListeningText("Stop error: ${e.localizedMessage}")
        } finally {
            mediaRecorder = null
            isRecordingFile = false
        }
    }

    private fun processResult(result: String, isFinal: Boolean) {
        Log.i(tag, "RESULT: $result")
        val jsonObject = JSONObject(result)
        val text = if (isFinal) jsonObject.optString("text", "") else jsonObject.optString("partial", "")
        if (text.isNotEmpty()) {
            if(isFinal) {
                conversation.add(Message("user", text))
                viewModel.updateUserText(text, true)
                if(sharedPrefs.getBoolean("voice_recognition", false) == false) {
                    viewModel.toggleListening()
                }
                // Get Pepper's response from specified API
                // Only works with OpenAI or similar APIs
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
                            Log.i(tag, "Full AI Reply: $reply")

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

    // These should be moved somewhere else
    interface AIApi {
        @Headers("Content-Type: application/json")
        @POST("v1/chat/completions")
        fun getChatCompletion(
            @Body request: ChatRequest
        ): Call<ChatResponse>
    }
    interface FileUploadService {
        @Multipart
        @POST("transcribe")
        fun uploadFile(
            @Part filePart: MultipartBody.Part
        ): Call<ResponseBody>
    }

    private fun prepareFilePart(partName: String, file: File): MultipartBody.Part {
        val mimeType = "audio/aac"
        val requestFile = file
            .asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, file.name, requestFile)
    }

    private fun setupSTTClients()
    {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(sharedPrefs.getLong("voice_recognition_read_timeout", 60L), TimeUnit.SECONDS)
            .build()

        // Retrofit
        try {
            retrofit = Retrofit.Builder()
                .baseUrl(sharedPrefs.getString("voice_recognition_api_url", "http://u.rl/")?: "")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            service = retrofit.create(FileUploadService::class.java)
        } catch (e : Exception) {
            // Malformed URL causes a crash without this, don't have time for a better solution
            retrofit = Retrofit.Builder()
                .baseUrl("http://google.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            Toast.makeText(this, "An error occured. Please check your voice recognition API url. It should look like this: http://example.com/", Toast.LENGTH_LONG).show()
        }


        service = retrofit.create(FileUploadService::class.java)
    }

    private fun getLocalSTTResponse()
    {
        isTranscribing = true
        val file = File(outputFilePath)
        val filePart = prepareFilePart("audio", file)
        viewModel.updateListeningText("Waiting for speech-to-text conversion")
        service.uploadFile(filePart)
            .enqueue(object : retrofit2.Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: retrofit2.Response<ResponseBody>)
                {
                    if (response.isSuccessful) {
                        val responseText = response.body()?.string()
                        Log.i("Upload", "Success! $responseText")
                        Log.d(tag, response.toString())

                        if (responseText != null) {
                            processResult(responseText, true)
                        } else {
                            viewModel.updateListeningText("Speech-to-text API returned no response or it was empty")
                        }
                        isTranscribing = false

                    } else {
                        viewModel.updateListeningText("Something went wrong with speech-to-text conversion. ${response.code()}")
                        Log.e("Upload", "Server error: ${response.code()} ${response.errorBody()?.string()}")
                        isTranscribing = false
                    }
                }


                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    viewModel.updateListeningText("Something went wrong with speech-to-text conversion: ${t.localizedMessage}")
                    Log.e("Upload", "Failed: ${t.localizedMessage}")
                    isTranscribing = false
                }
            })
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
            val locale = Locale(Language.ENGLISH, Region.UNITED_STATES)
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    SayBuilder.with(context)
                        .withText(text)
                        .withLocale(locale)
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
        val scrollState = rememberScrollState()
        val isListening = viewModel.isListening.value
        val snackbarHostState = remember { SnackbarHostState() }
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    Text("Press the button to talk with Pepper.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(viewModel.listeningText.value, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(viewModel.userInput.value, modifier = Modifier.fillMaxWidth().background(Color(0xFF9A97A9)).padding(8.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(viewModel.aiResponse.value, modifier = Modifier.fillMaxWidth().background(Color(0xFF8EAD8F)).padding(8.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FloatingActionButton(
                        onClick = {
                            val sharedPrefs = applicationContext.getSharedPreferences("app_settings", MODE_PRIVATE)
                            if(isTranscribing) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please wait until Pepper responds.")
                                }
                            }
                            if (!isListening && !isTranscribing) {
                                viewModel.toggleListening()
                            }
                            // Stopping mid-recording is OK for local voice recognition
                            else if(sharedPrefs.getBoolean("voice_recognition", false) && !isTranscribing) {
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