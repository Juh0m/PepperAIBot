package com.example.pepperaibot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.example.pepperaibot.ui.theme.PepperAIBotTheme
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    private val TAG = "VoskDemo"
    private val SAMPLE_RATE = 16000
    private val BUFFER_SIZE_ELEMENTS = 65536
    private val RECORD_AUDIO_REQUEST_CODE = 101

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var model: Model
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private var isModelInitialized = false

    private val viewModel by viewModels<MainViewModel>()

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

        viewModel.onStartListening = {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_REQUEST_CODE
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
                Log.e(TAG, "Model init error", e)
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
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            )

            audioRecord?.startRecording()
            isRecording = true
            viewModel.updateListeningText("Listening...")

            val buffer = ShortArray(BUFFER_SIZE_ELEMENTS)
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
            Log.e(TAG, "Recording failed", e)
            viewModel.updateUserText("Recording error: ${e.message}", false)
        }
    }

    private fun processResult(result: String, isFinal: Boolean) {
        val jsonObject = JSONObject(result)
        val text = if (isFinal) jsonObject.optString("text", "") else jsonObject.optString("partial", "")
        if (text.isNotEmpty()) {
            if(isFinal) {
                viewModel.updateUserText(text, true)
                stopRecording()
            }
            else {
                viewModel.updateUserText("Partial: $text", false)
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

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        if (::model.isInitialized) model.close()
        QiSDK.unregister(this, this)
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.d(TAG, "Robot focus gained")
    }

    override fun onRobotFocusLost() {
        Log.d(TAG, "Robot focus lost")
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(viewModel: MainViewModel, onSettingsClick: () -> Unit) {
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
                        onClick = { viewModel.toggleListening() },
                        containerColor = if (viewModel.isListening.value) Color(0xFFD32F2F) else Color(0xFF6200EE),
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Mic", tint = Color.White)
                    }
                }
            }
        }
    }
}
