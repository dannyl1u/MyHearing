package com.example.myhearing

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DecibelMeterActivity : ComponentActivity() {
    private lateinit var mMediaRecorder: MediaRecorder
    private var recording = false
    private var job: Job? = null

    private var lastWeightedDBLevel: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_decibel_meter)

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()

        val recordAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if (!this.recording && recordAudioPermission == PackageManager.PERMISSION_GRANTED) {
            startMediaRecorder()
        }

        startListening()
    }

    override fun onPause() {
        super.onPause()

        if (recording) {
            this.mMediaRecorder.stop()
            this.mMediaRecorder.release()
            this.recording = false
        }

        job?.cancel()
    }

    private fun startListening() {
        job = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val dBLevel = calculateWeightedDecibelLevel()
                val dbLevelText = if (dBLevel.isFinite()) {
                    "%.1f dB".format(dBLevel)
                } else {
                    "%.1f dB".format(lastWeightedDBLevel)
                }

                findViewById<TextView>(R.id.textView_decibel_meter).text = dbLevelText
                delay(100L)
            }
        }
    }

    private fun calculateWeightedDecibelLevel(): Double {
        val weightedDBLevel = 0.25 * getCurrentDecibelLevel() + 0.75 * lastWeightedDBLevel

        if (weightedDBLevel.isFinite()) {
            lastWeightedDBLevel = weightedDBLevel
        }

        return weightedDBLevel
    }

    private fun getCurrentDecibelLevel(): Double {
        val amplitude = mMediaRecorder.maxAmplitude
        return 20 * kotlin.math.log10(amplitude / 4.0)
    }

    private fun startMediaRecorder() {
        this.mMediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(applicationContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        this.mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        this.mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        this.mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        this.mMediaRecorder.setOutputFile("${externalCacheDir?.absolutePath}/test.3gp")

        try {
            this.mMediaRecorder.prepare()
            this.mMediaRecorder.start()
            this.recording = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkPermissions() {
        val recordAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        if (recordAudioPermission == PackageManager.PERMISSION_GRANTED) {
            startMediaRecorder()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startMediaRecorder()
            } else if (shouldShowRequestPermissionRationale("android.permission.RECORD_AUDIO")) {
                launchPermissionAskAgainDialog()
            } else {
                launchAppInfoDialog()
            }
        }

    private fun launchAppInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app won't work properly without the \"record audio\" permission!")
            .setPositiveButton("OK") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .setNegativeButton("Open Settings") { dialog: DialogInterface, _: Int ->
                launchAppInfo()
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

    private fun launchPermissionAskAgainDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app won't work properly without the \"record audio\" permission!")
            .setPositiveButton("Deny Anyway") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .setNegativeButton("Ask Again") { dialog: DialogInterface, _: Int ->
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                dialog.dismiss()
            }
            .create()
            .show()
    }
}