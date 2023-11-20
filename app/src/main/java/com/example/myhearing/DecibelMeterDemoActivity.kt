package com.example.myhearing

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myhearing.R

class DecibelMeterDemoActivity : ComponentActivity() {

    private val RECORD_AUDIO_PERMISSION_CODE = 123
    private var audioRecord: AudioRecord? = null
    private lateinit var noiseLevelTextView: TextView
    private val handler = Handler(Looper.getMainLooper()) // Handler for updating UI
    private val updateIntervalMillis = 1000L // Update every second

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
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
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
            val maxAmplitude = audioData.max() ?: 0
            val decibel = 20 * Math.log10(maxAmplitude.toDouble())
            Log.d("DecibelMeter", "Decibel: $decibel")
            runOnUiThread {
                // Update UI with decibel level
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
