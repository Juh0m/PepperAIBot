package com.example.pepperaibot

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import com.example.pepperaibot.ui.theme.PepperAIBotTheme
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks

class MainActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    private val TAG = "VoskDemo"
    private val SAMPLE_RATE = 16000
    private val BUFFER_SIZE_ELEMENTS = 65536
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private val BUFFER_SIZE_BYTES = BUFFER_SIZE_ELEMENTS * 2 // 2 bytes per short

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var resultTextView: TextView
    private lateinit var recordButton: Button
    private lateinit var model: Model
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false
    private var isModelInitialized = false

    // Request audio permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initVosk()
        } else {
            Toast.makeText(this, "Permissions required for speech recognition", Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.resultTextView)
        recordButton = findViewById(R.id.recordButton)

        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_REQUEST_CODE
                )
            } else {
                // Permission already granted
                startRecording() // or any function that needs the mic
            }
        }

        // Initialize QiSDK if needed for Pepper robot integration
        try {
            QiSDK.register(this, this)
        } catch (e: Exception) {
            Log.w(TAG, "QiSDK registration failed. Is this running on a Pepper robot?", e)
            // Continue anyway as the app can work without Pepper
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val audioPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val storagePermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionsToRequest = mutableListOf<String>()

        if (audioPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (storagePermission != PackageManager.PERMISSION_GRANTED) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initVosk()
        }
    }

    private fun initVosk() {
        // Initialize Vosk
        LibVosk.setLogLevel(LogLevel.INFO)

        // Load model in a background thread
        scope.launch {
            try {
                resultTextView.text = "Loading model..."
                withContext(Dispatchers.IO) {
                    // Get the model from assets
                    val modelDir = File(filesDir, "model")
                    Log.d(TAG, "Model directory: ${modelDir.absolutePath}")
                    Log.d(TAG, "Contents: ${modelDir.list()?.joinToString()}")
                    if (!modelDir.exists()) {
                        modelDir.mkdirs()
                        Log.d(TAG, "Model directory: ${modelDir.absolutePath}")
                        extractAssets("model-en-us.zip", modelDir)
                    }

                    model = Model(modelDir.absolutePath)
                    isModelInitialized = true
                }
                recordButton.isEnabled = true
                resultTextView.text = "Ready for speech recognition"
            } catch (e: IOException) {
                Log.e(TAG, "Failed to initialize Vosk model", e)
                resultTextView.text = "Failed to initialize Vosk model: ${e.message}"
            }
        }
    }

    private fun extractAssets(zipName: String, targetDir: File) {
        try {
            assets.open(zipName).use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var zipEntry = zipInputStream.nextEntry
                    while (zipEntry != null) {
                        val newFile = File(targetDir, zipEntry.name)
                        if (zipEntry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            File(newFile.parent!!).mkdirs()
                            extractFile(zipInputStream, newFile)
                        }
                        zipInputStream.closeEntry()
                        zipEntry = zipInputStream.nextEntry
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error extracting model file", e)
            Toast.makeText(this, "Failed to extract model: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun extractFile(zipInputStream: ZipInputStream, targetFile: File) {
        FileOutputStream(targetFile).use { fileOutputStream ->
            val buffer = ByteArray(4096)
            var length: Int
            while (zipInputStream.read(buffer).also { length = it } > 0) {
                fileOutputStream.write(buffer, 0, length)
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        if (!isModelInitialized) {
            Toast.makeText(this, "Model is not initialized yet", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Initialize recognizer
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

            // Initialize AudioRecord
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IOException("Failed to initialize AudioRecord")
            }

            audioRecord?.startRecording()
            isRecording = true
            recordButton.text = "Stop"
            resultTextView.text = "Listening..."

            // Start processing audio
            val buffer = ShortArray(BUFFER_SIZE_ELEMENTS)

            recordingJob = scope.launch(Dispatchers.IO) {
                try {
                    while (isRecording) {
                        val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        Log.d(TAG, "Audio read size: $read")
                        val rms = buffer.rms()
                        Log.d(TAG, "Audio buffer RMS volume: $rms")
                        if (read > 0) {
                            // Convert shorts to bytes for recognizer
                            val byteBuffer = ByteBuffer.allocate(read * 2) // 2 bytes per short
                                .order(ByteOrder.LITTLE_ENDIAN)

                            for (i in 0 until read) {
                                byteBuffer.putShort(buffer[i])
                            }

                            val data = byteBuffer.array()

                            // Process audio
                            if (recognizer?.acceptWaveForm(data, read * 2) == true) {
                                Log.d(TAG, "Got final result!")
                                // Final result
                                val result = recognizer?.result ?: ""
                                withContext(Dispatchers.Main) {
                                    processResult(result, true)
                                }
                            } else {
                                // Partial result
                                Log.d(TAG, "Partial result: ${recognizer?.partialResult}")
                                val partial = recognizer?.partialResult ?: ""
                                withContext(Dispatchers.Main) {
                                    processResult(partial, false)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during recording", e)
                    withContext(Dispatchers.Main) {
                        resultTextView.text = "Error: ${e.message}"
                        stopRecording()
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
            resultTextView.text = "Failed to start recording: ${e.message}"
        }
    }

    private fun processResult(result: String, isFinal: Boolean) {
        try {
            val jsonObject = JSONObject(result)
            Log.d(TAG, "Raw recognizer output: $result")
            val text = if (isFinal) {
                jsonObject.optString("text", "")
            } else {
                jsonObject.optString("partial", "")
            }

            // Update UI
            if (text.isNotEmpty()) {
                resultTextView.text = if (isFinal) "Final: $text" else "Partial: $text"

                // Handle the recognized text here
                if (isFinal && text.isNotEmpty()) {
                    handleRecognizedText(text)
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing JSON result", e)
        }
    }

    private fun handleRecognizedText(text: String) {
        // Here you can add your logic to process the recognized text
        // For example, sending it to an AI service or triggering Pepper robot actions
        Log.d(TAG, "Recognized text: $text")

        // Example: Stop recording after processing final result
        stopRecording()
    }

    private fun stopRecording() {
        // Cancel the coroutine job
        recordingJob?.cancel()
        recordingJob = null

        // Stop and release audio recorder
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Free recognizer
        recognizer?.close()
        recognizer = null

        // Update UI
        recordButton.text = "Start"
        isRecording = false
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()

        // Free model resources
        if (::model.isInitialized) {
            model.close()
        }

        // Unregister from QiSDK
        try {
            QiSDK.unregister(this, this)
        } catch (e: Exception) {
            Log.w(TAG, "QiSDK unregister failed", e)
        }
    }

    // Required RobotLifecycleCallbacks methods for Pepper integration
    override fun onRobotFocusGained(qiContext: QiContext) {
        // Robot focus gained - you can perform robot-specific actions here
        Log.d(TAG, "Robot focus gained")
    }

    override fun onRobotFocusLost() {
        // Robot focus lost
        Log.d(TAG, "Robot focus lost")
    }

    override fun onRobotFocusRefused(reason: String) {
        // Robot focus refused
        Log.e(TAG, "Robot focus refused: $reason")
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startRecording()
            } else {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun ShortArray.rms(): Double {
        var sum = 0.0
        for (s in this) sum += s * s
        return Math.sqrt(sum / this.size)
    }
}