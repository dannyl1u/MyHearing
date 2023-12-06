package com.example.myhearing.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myhearing.R
import com.example.myhearing.data.MyHearingDatabaseHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.CoroutineContext
import kotlin.math.log10

class LocationAndNoiseService : Service(), CoroutineScope {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var lastNoiseLevel: Int? = null

    private var audioRecord: AudioRecord? = null
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val serviceJob = Job()
    override val coroutineContext: CoroutineContext
        get() = serviceJob

    override fun onCreate() {
        super.onCreate()

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    lastLatitude = location.latitude
                    lastLongitude = location.longitude
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "LocationAndNoiseServiceChannel",
                "Location and Noise Tracking",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Channel for Location and Noise Service"
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        initAudioRecord()
    }

    private fun initAudioRecord() {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
    }


    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "LocationAndNoiseServiceChannel")
            .setContentTitle("Location and Noise Service")
            .setContentText("Tracking location and noise")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(SERVICE_NOTIFICATION_ID, notification)

        startTracking()

        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    private fun startTracking() {
        trackLocation()

        launch {
            var elapsedTime = 0L
            val apiUpdateInterval = 5000L
            val broadcastInterval = 1000L

            while (isActive) {
                trackNoiseLevel()

                if (elapsedTime >= apiUpdateInterval) {
                    sendLocationAndNoiseData()
                    elapsedTime = 0
                } else {
                    saveDataToDatabase()
                    elapsedTime += broadcastInterval
                }

                delay(broadcastInterval)
            }
        }
    }

    private fun trackLocation() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .apply {
                setWaitForAccurateLocation(false)
                setMinUpdateIntervalMillis(1000)
                setMaxUpdateDelayMillis(1000)
            }.build()

        try {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationTracking", "Location permission not granted", e)
        }
    }

    private fun trackNoiseLevel() {
        val bufferSize = audioRecord?.bufferSizeInFrames ?: 0
        val audioData = ShortArray(bufferSize)
        val readResult = audioRecord?.read(audioData, 0, bufferSize)

        if (readResult != null && readResult > 0) {
            val maxAmplitude = audioData.maxOrNull() ?: 0

            val prefs = getSharedPreferences("com.example.myhearing", Context.MODE_PRIVATE)
            val calibrationFactor = prefs.getFloat("calibration_factor", 1f)

            val decibel = 20 * log10(maxAmplitude.toDouble() * calibrationFactor)
            lastNoiseLevel = decibel.toInt()

            Log.d("NoiseTracking", "Decibel: $decibel")
            val intent = Intent("com.example.myhearing.NOISE_LEVEL_UPDATE")
            intent.putExtra("noise_level", decibel.toFloat())
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    private fun sendLocationAndNoiseData() {
        val latitude = lastLatitude ?: return
        val longitude = lastLongitude ?: return
        val noiseLevel = lastNoiseLevel ?: return
        val timestamp =
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())

        val jsonData = """
        {
            "latitude": "$latitude",
            "longitude": "$longitude",
            "noise_level": "$noiseLevel",
            "timestamp": "$timestamp"
        }
    """.trimIndent()

        launch(Dispatchers.IO) {
            try {
                val url = URL("https://myhearingserver.onrender.com/api/v1/insert")
                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    outputStream.write(jsonData.toByteArray())
                    outputStream.flush()
                    outputStream.close()

                    Log.d("NetworkRequest", "Response Code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("NetworkRequest", "Error: ${e.message}")
            }
        }
    }

    private fun saveDataToDatabase() {
        val latitude = lastLatitude ?: return
        val longitude = lastLongitude ?: return
        val noiseLevel = lastNoiseLevel ?: return

        launch(Dispatchers.IO) {
            try {
                val insertQuery = "INSERT INTO ${MyHearingDatabaseHelper.TABLE_NAME} " +
                        "(dateTime, dbLevel, comment, location) " +
                        "VALUES (?, ?, ?, ?)"
                val dbHelper = MyHearingDatabaseHelper(this@LocationAndNoiseService)
                val db = dbHelper.writableDatabase
                val statement = db.compileStatement(insertQuery)
                val currentTimeMillis = System.currentTimeMillis()
                statement.bindLong(1, currentTimeMillis)
                statement.bindDouble(2, noiseLevel.toDouble())
                statement.bindString(3, "")
                statement.bindString(4, "$latitude, $longitude")
                statement.executeInsert()

            } catch (e: Exception) {
                Log.e("Database error", "Error: ${e.message}")
            }
        }
    }

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 1
    }


}

