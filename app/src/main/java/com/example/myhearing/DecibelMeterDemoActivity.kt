package com.example.myhearing

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10

class DecibelMeterDemoActivity : ComponentActivity() {

    private val RECORD_AUDIO_PERMISSION_CODE = 123
    private var audioRecord: AudioRecord? = null
    private lateinit var noiseLevelTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMillis = 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.noise_level)

        noiseLevelTextView = findViewById(R.id.tvDecibelLevel)

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
            val decibel = 20 * log10(maxAmplitude.toDouble())
            Log.d("DecibelMeter", "Decibel: $decibel")
            runOnUiThread {
                noiseLevelTextView.text = "Decibel Level: ${decibel.toInt()} dB"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecord?.stop()
        audioRecord?.release()
    }
}
