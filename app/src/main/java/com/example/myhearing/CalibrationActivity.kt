package com.example.myhearing

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.myhearing.databinding.ActivityCalibrationBinding
import kotlin.math.pow

// TODO: Tune RECORD_DURATION_MS, RECORD_UPDATE_INTERVAL_MS, and DEFAULT_DECIBELS
// TODO: Add button and timer visualization
// TODO: Add use info for user

class CalibrationActivity : ComponentActivity() {
    companion object {
        const val RECORD_DURATION_MS = 10000L
        const val RECORD_UPDATE_INTERVAL_MS = 1000L
        const val DEFAULT_DECIBELS = 20.0
    }

    private lateinit var binding: ActivityCalibrationBinding

    private val handler = Handler(Looper.getMainLooper())
    private var audioRecord: AudioRecord? = null

    private val maxAmplitudeData = ArrayList<Short>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacksAndMessages(null)

        audioRecord?.stop()
        audioRecord?.release()
    }

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                initAudioRecord()
            } else {
                launchRecordAudioDialog()
            }
        }

    private fun initAudioRecord() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_DENIED
        ) {
            return
        }

        val sampleRateInHz = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            AudioSource.MIC,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()

        startCalibrationRunnable()
    }

    private fun launchRecordAudioDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Calibration won't work properly without the \"record audio\" permission!")
            .setPositiveButton("Ask Again") { dialog: DialogInterface, _: Int ->
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    launchAppInfo()
                }

                dialog.dismiss()
            }
            .setNegativeButton("Deny Anyway") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun launchAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun startCalibrationRunnable() {
        val startTime = System.currentTimeMillis()

        val calibrationRunnable = object : Runnable {
            override fun run() {
                updateCalibrationData()

                val timeElapsed = System.currentTimeMillis() - startTime
                binding.calibrationTvTimer.text = (timeElapsed / 1000).toString()

                if (timeElapsed > RECORD_DURATION_MS) {
                    saveCalibrationFactor()
                    handler.removeCallbacksAndMessages(null)
                } else {
                    handler.postDelayed(this, RECORD_UPDATE_INTERVAL_MS)
                }
            }
        }

        handler.post(calibrationRunnable)
    }

    private fun updateCalibrationData() {
        val bufferSize = audioRecord?.bufferSizeInFrames ?: 0
        val audioData = ShortArray(bufferSize)
        val readResult = audioRecord?.read(audioData, 0, bufferSize)

        if (readResult != null && readResult != AudioRecord.ERROR_BAD_VALUE) {
            maxAmplitudeData.add(audioData.max())
        }
    }

    private fun saveCalibrationFactor() {
        val averageMaxAmplitude = maxAmplitudeData.average()
        val calibrationFactor = (10.0).pow(DEFAULT_DECIBELS / 20.0) / averageMaxAmplitude

        val editor = getSharedPreferences("com.example.myhearing", Context.MODE_PRIVATE).edit()
        editor.putFloat("calibration_factor", calibrationFactor.toFloat())
        editor.apply()
    }
}