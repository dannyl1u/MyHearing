package com.example.myhearing

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
//import kotlinx.android.synthetic.main.activity_main.*


class DecibelMeterDemoActivity : AppCompatActivity() {

    private val RECORD_AUDIO_PERMISSION_CODE = 1
    private var mediaRecorder: MediaRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkPermission()) {
            initMediaRecorder()
        } else {
            requestPermission()
        }
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

    private fun initMediaRecorder() {
        mediaRecorder = MediaRecorder()
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mediaRecorder?.setOutputFile("/dev/null")

        try {
            mediaRecorder?.prepare()
            mediaRecorder?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Start a thread to update the UI with the decibel level
        Thread {
            while (true) {
                updateDecibelLevel()
                try {
                    Thread.sleep(1000) // Update every second
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    private fun updateDecibelLevel() {
        val amplitude = mediaRecorder?.maxAmplitude ?: 0
        val decibel = 20 * Math.log10(amplitude.toDouble())
        runOnUiThread {
            // Update UI with decibel level (you can replace with your UI logic)
            decibelTextView.text = "Decibel Level: ${decibel.toInt()} dB"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.stop()
        mediaRecorder?.release()
    }
}
