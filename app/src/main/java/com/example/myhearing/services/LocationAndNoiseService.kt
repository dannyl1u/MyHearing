package com.example.myhearing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.CoroutineContext

class LocationAndNoiseService : Service(), CoroutineScope {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var lastNoiseLevel: Int? = null


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
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
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
            while (isActive) {
                trackNoiseLevel()
                sendLocationAndNoiseData()
                delay(5000)
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


    private suspend fun trackNoiseLevel() {
        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(getExternalFilesDir(null)?.absolutePath + "/test.3gp")        }

        try {
            recorder.prepare()
            recorder.start()

            delay(1000) // delay for 1 second to get max amplitude

            val amplitude = recorder.maxAmplitude
            lastNoiseLevel = amplitude
            Log.d("NoiseTracking", "Amplitude: $amplitude")

            recorder.stop()
            recorder.release()
        } catch (e: IOException) {
            Log.e("NoiseTracking", "Error in recording audio", e)
        } catch (e: SecurityException) {
            Log.e("NoiseTracking", "Audio recording permission not granted", e)
        }
    }

    private fun sendLocationAndNoiseData() {
        val latitude = lastLatitude ?: return
        val longitude = lastLongitude ?: return
        val noiseLevel = lastNoiseLevel ?: return
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

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


    companion object {
        private const val SERVICE_NOTIFICATION_ID = 1
    }


}

