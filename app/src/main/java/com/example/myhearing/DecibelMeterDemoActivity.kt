package com.example.myhearing

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.os.Build
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class DecibelMeterDemoActivity : ComponentActivity() {

    private val RECORD_AUDIO_PERMISSION_CODE = 123
    private var audioRecord: AudioRecord? = null
    private lateinit var noiseLevelTextView: TextView
    private lateinit var settingsButton: Button
    private lateinit var backButton: Button
    private val handler = Handler(Looper.getMainLooper())

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
            startLocationAndNoiseService()
        } else {
            requestPermission()
        }


    }

    private val noiseLevelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val noiseLevel = intent?.getFloatExtra("noise_level", 0.0f) ?: 0.0f
            Log.d("DecibelMeterDemoActivity", "Received Decibel Level: $noiseLevel dB")
            noiseLevelTextView.text = "Decibel Level: ${noiseLevel.toInt()} dB"
            val progress = noiseLevel.toInt().coerceIn(0, 100)
            // Update both ProgressBar's progress
            progressBar.progress = progress
            horizontalProgressBar.progress = progress
        }
    }


    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.example.myhearing.NOISE_LEVEL_UPDATE")
        LocalBroadcastManager.getInstance(this).registerReceiver(noiseLevelReceiver, filter)
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(noiseLevelReceiver)
        super.onPause()
    }

    private fun startLocationAndNoiseService() {
        val serviceIntent = Intent(this, LocationAndNoiseService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
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