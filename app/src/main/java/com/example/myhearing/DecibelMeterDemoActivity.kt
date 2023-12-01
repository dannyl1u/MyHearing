package com.example.myhearing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10


class DecibelMeterDemoActivity : ComponentActivity() {

    private val RECORD_AUDIO_PERMISSION_CODE = 123
    private var audioRecord: AudioRecord? = null
    private lateinit var noiseLevelTextView: TextView
    private lateinit var settingsButton: Button
    private lateinit var backButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMillis = 1000L


    private var currentMode = Mode.NUMBER
    private lateinit var progressBar: ProgressBar
    private lateinit var horizontalProgressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentMode = intent.getStringExtra("selectedMode")
        currentMode = convertStringToMode(intentMode ?: "Number")
        Log.d("DecibelMeterDemoActivity", currentMode.toString())
        setLayoutForCurrentMode()

        noiseLevelTextView = findViewById(R.id.tvDecibelLevel)
        settingsButton = findViewById(R.id.settingsButton)
        backButton = findViewById(R.id.backButton)


        backButton.setOnClickListener {
            // Go back to nav drawer
            finish()
        }
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            finish()
        }

        if (checkPermission()) {
            initAudioRecord()

            startSoundCheckRunnable()
        } else {
            requestPermission()
        }


    }

    private fun startSoundCheckRunnable() {
        val soundCheckRunnable = object : Runnable {
            override fun run() {
                updateDecibelLevel()
                handler.postDelayed(this, updateIntervalMillis)
            }
        }

        handler.post(soundCheckRunnable)
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION_CODE
        )
    }

    // https://developer.android.com/reference/android/media/AudioRecord
    private fun initAudioRecord() {
        val sampleRate = 44100 // Sample rate in Hz
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        audioRecord = AudioRecord(
            AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
    }

    private fun updateDecibelLevel() {
        val bufferSize = audioRecord?.bufferSizeInFrames ?: 0
        val audioData = ShortArray(bufferSize)
        val readResult = audioRecord?.read(audioData, 0, bufferSize)

        if (readResult != null && readResult != AudioRecord.ERROR_BAD_VALUE) {
            val maxAmplitude = audioData.max()

            val prefs = getSharedPreferences("com.example.myhearing", Context.MODE_PRIVATE)
            val calibrationFactor = prefs.getFloat("calibration_factor", 1f)

            val decibel = 20 * log10(maxAmplitude.toDouble() * calibrationFactor)
            Log.d("DecibelMeter", "Decibel: $decibel")
            Log.d("DecibelMeter", "Calibration Factor: $calibrationFactor")
            runOnUiThread {
                noiseLevelTextView.text = "Decibel Level: ${decibel.toInt()} dB"
                val progress = decibel.toInt().coerceIn(0, 100)
                // Update both ProgressBar's progress
                progressBar.progress = progress
                horizontalProgressBar.progress = progress

            }
        }
    }

    private enum class Mode {
        NUMBER, GAUGE, HORIZONTALGAUGE
    }

    private fun setLayoutForCurrentMode() {
        // Set the content view based on the current mode
        when (currentMode) {
            Mode.NUMBER -> {
                setContentView(R.layout.dbmode_number)
                progressBar = findViewById(R.id.progressBar)
                horizontalProgressBar = findViewById(R.id.horizontalProgressBar)

                progressBar.visibility = View.GONE
                horizontalProgressBar.visibility = View.GONE
            }

            Mode.GAUGE -> {
                setContentView(R.layout.dbmode_gauge)
                progressBar = findViewById(R.id.progressBar)
                horizontalProgressBar = findViewById(R.id.horizontalProgressBar)
                progressBar.visibility = View.VISIBLE
                horizontalProgressBar.visibility = View.GONE

            }

            Mode.HORIZONTALGAUGE -> {
                setContentView(R.layout.dbmode_horizontalgauge)
                progressBar = findViewById(R.id.progressBar)
                horizontalProgressBar = findViewById(R.id.horizontalProgressBar)
                progressBar.visibility = View.GONE
                horizontalProgressBar.visibility = View.VISIBLE

            }
        }
    }

    private fun convertStringToMode(modeString: String): Mode {
        return when (modeString) {
            "Number" -> Mode.NUMBER
            "Gauge" -> Mode.GAUGE
            "Horizontal Gauge" -> Mode.HORIZONTALGAUGE
            else -> throw IllegalArgumentException("Invalid mode: $modeString")
        }
    }

    override fun onDestroy() {
        stopSoundCheckRunnable()
        super.onDestroy()
        audioRecord?.stop()
        audioRecord?.release()
    }

    // Release AudioRecord to prevent crashing upon finish()
    private fun stopSoundCheckRunnable() {
        handler.removeCallbacksAndMessages(null)
    }

}