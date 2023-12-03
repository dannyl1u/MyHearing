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
import com.example.myhearing.data.MyHearingDatabaseHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.log10


class DecibelMeterDemoActivity : ComponentActivity() {

    private var audioRecord: AudioRecord? = null
    private lateinit var noiseLevelTextView: TextView
    private lateinit var settingsButton: Button
    private lateinit var backButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMillis = 1000L
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationString: String = ""


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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

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
        val hasRecordAudioPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        return hasRecordAudioPermission && hasFineLocationPermission && hasCoarseLocationPermission
    }

    private fun requestPermission() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initAudioRecord()
                startSoundCheckRunnable()
            } else {
            }
        }
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
        // Check if recorded correctly
        if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("DecibelMeter", "AudioRecord is not initialized.")
            return
        }


        val bufferSize = audioRecord?.bufferSizeInFrames ?: 0
        val audioData = ShortArray(bufferSize)
        val readResult = audioRecord?.read(audioData, 0, bufferSize)

        if (readResult != null && readResult != AudioRecord.ERROR_BAD_VALUE) {
            val maxAmplitude = audioData.max()
            val decibel = 20 * log10(maxAmplitude.toDouble())
            Log.d("DecibelMeter", "Decibel: $decibel")
            runOnUiThread {
                if (decibel >= 0) {
                    noiseLevelTextView.text = "Decibel Level: ${decibel.toInt()} dB"
                }
            }

            if (decibel >= 0) {
                saveDataToDatabase(this, decibel)
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
//        stopSoundCheckRunnable()
        super.onDestroy()
//        audioRecord?.stop()
//        audioRecord?.release()
    }

    private fun saveDataToDatabase(context: Context, dbLevel: Double) {
        val insertQuery = "INSERT INTO ${MyHearingDatabaseHelper.TABLE_NAME} " +
                "(time, dbLevel, comment, location) " +
                "VALUES (?, ?, ?, ?)"
        val dbHelper = MyHearingDatabaseHelper(context)
        val db = dbHelper.writableDatabase
        val statement = db.compileStatement(insertQuery)

        // We can process data here and bind it to the statement
        // Fetch Time
        val currentTimeMillis = System.currentTimeMillis()
        statement.bindLong(1, currentTimeMillis)

        statement.bindDouble(2, dbLevel)
        statement.bindString(3, "test comment")

        // Fetch Location
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            Log.d("Location Denied", "Location Permission Denied, cannot save locations.")
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude
                locationString = "Lat: $latitude, Lng: $longitude"
            }
        }
        statement.bindString(4, locationString)

        statement.executeInsert()
        Log.d("Save Data to Database", "Saved data successfully with time = $currentTimeMillis, dbLevel = $dbLevel, location = $locationString.")
        db.close()
    }

    // Release AudioRecord to prevent crashing upon finish()
    private fun stopSoundCheckRunnable() {
        handler.removeCallbacksAndMessages(null)
    }

}