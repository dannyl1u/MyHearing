package com.example.myhearing

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myhearing.databinding.ActivityHeatmapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import java.lang.Math.toDegrees
import java.text.DecimalFormat
import kotlin.math.atan
import kotlin.math.log10
import kotlin.math.pow

// TODO: Get location/decibels from database
// TODO: Tune most companion object vals
// TODO: Generate GRID_ROWS/GRID_COLS dynamically based on screen size

class HeatmapActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        const val MAX_DB_INTENSITY = 100.0
        const val LOCATION_UPDATE_INTERVAL_MS = 1000L
        const val DEFAULT_LOCATION_PATTERN = "#.#######"
        const val GRID_ROWS = 50
        const val GRID_COLS = 30
        val GRADIENT_COLORS = intArrayOf(Color.GREEN, Color.RED)
        val GRADIENT_START_POINTS = floatArrayOf(0.4f, 1f)
        const val PROVIDER_MAX_INTENSITY = 1.0
        const val PROVIDER_RADIUS = 50
        const val DEFAULT_ZOOM_LEVEL = 21f
    }

    private lateinit var binding: ActivityHeatmapBinding

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap

    private val latLngMap: MutableMap<LatLng, Int> = mutableMapOf()
    private val weightedHeatmapData = ArrayList<WeightedLatLng>()
    private lateinit var provider: HeatmapTileProvider
    private var providerBuilt = false
    private var mapCentred = false

    private var audioRecord: AudioRecord? = null
    private val handler = Handler(Looper.getMainLooper())
    private var decibel = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()

        initAudioRecord()
        startSoundCheckRunnable()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        val fgLocationPermissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val fgLocationPermissionsGranted = fgLocationPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!fgLocationPermissionsGranted) {
            return
        }

        mMap = googleMap

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                updateWeightedHeatmapData(locationResult.lastLocation ?: return)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL_MS)
                .build(),
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun requestPermissions() {
        val fgLocationPermissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        fgLocationPermissionLauncher.launch(fgLocationPermissions)
    }

    private val fgLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == false) {
                    launchFineLocationDialog()
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        initMap()
                    }
                }
            } else {
                launchFineLocationDialog()
            }
        }

    private fun launchFineLocationDialog() {
        val fgLocationPermissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Heatmap won't work properly without your precise location!")
            .setPositiveButton("Ask Again") { dialog: DialogInterface, _: Int ->
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    fgLocationPermissionLauncher.launch(fgLocationPermissions)
                } else {
                    launchAppInfo()
                }

                dialog.dismiss()
            }
            .setNegativeButton("Deny Anyway") { dialog: DialogInterface, _: Int ->
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        initMap()
                    }
                }

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

    private val bgLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                initMap()
            } else {
                launchBgLocationDialog()
            }
        }

    private fun launchBgLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Heatmap won't work properly without knowing your location all the time!")
            .setPositiveButton("Ask Again") { dialog: DialogInterface, _: Int ->
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    } else {
                        initMap()
                    }
                } else {
                    launchAppInfo()
                }

                dialog.dismiss()
            }
            .setNegativeButton("Deny Anyway") { dialog: DialogInterface, _: Int ->
                initMap()
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun initMap() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.heatmap_map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun updateWeightedHeatmapData(newLocation: Location) {
        val newLatLng = LatLng(
            DecimalFormat(DEFAULT_LOCATION_PATTERN).format(newLocation.latitude).toDouble(),
            DecimalFormat(DEFAULT_LOCATION_PATTERN).format(newLocation.longitude).toDouble()
        )

        val newDbIntensity = getNewDbIntensity()

        if (latLngMap.containsKey(newLatLng)) {
            weightedHeatmapData.removeIf {
                val tempLatLng = wllToLatLng(it)
                tempLatLng.latitude == newLatLng.latitude && tempLatLng.longitude == newLatLng.longitude
            }

            weightedHeatmapData.add(WeightedLatLng(newLatLng, newDbIntensity))
        } else {
            latLngMap[newLatLng] = 1
            weightedHeatmapData.add(WeightedLatLng(newLatLng, newDbIntensity))
        }

        refreshHeatmap(newLatLng, getAveragedHeatmapData())
    }

    private fun getNewDbIntensity(): Double {
//        val dbLevels = arrayOf(10, 20, 30, 40, 50, 60, 70, 80, 85)
//        val randIndex = Random(System.currentTimeMillis()).nextInt(dbLevels.size)
//        val randDbIntensity = dbLevels[randIndex]

//        Log.e("new intensity", (randDbIntensity.toDouble() / MAX_DB_INTENSITY).toString())

//        return randDbIntensity.toDouble() / MAX_DB_INTENSITY
        return decibel / MAX_DB_INTENSITY
    }

    private fun getAveragedHeatmapData(): ArrayList<WeightedLatLng> {
        val visibleRegionBounds = mMap.projection.visibleRegion.latLngBounds
        val maxLatLng = visibleRegionBounds.northeast
        val minLatLng = visibleRegionBounds.southwest

        val cellSizeLat = (maxLatLng.latitude - minLatLng.latitude) / GRID_ROWS
        val cellSizeLng = (maxLatLng.longitude - minLatLng.longitude) / GRID_COLS

        val cellIntensityMap: MutableMap<Pair<Int, Int>, ArrayList<Double>> = mutableMapOf()

        for (wll in weightedHeatmapData) {
            val latLng = wllToLatLng(wll)

            val row = ((latLng.latitude - minLatLng.latitude) / cellSizeLat).toInt()
            val col = ((latLng.longitude - minLatLng.longitude) / cellSizeLng).toInt()
            val cell = Pair(row, col)

            val cellIntensities = cellIntensityMap.getOrDefault(cell, ArrayList())
            cellIntensities.add(wll.intensity)
            cellIntensityMap[cell] = cellIntensities
        }

        val averagedHeatmapData = ArrayList<WeightedLatLng>()

        for ((cell, cellIntensities) in cellIntensityMap) {
            val cellCentreLat = minLatLng.latitude + (cell.first + 0.5) * cellSizeLat
            val cellCentreLng = minLatLng.longitude + (cell.second + 0.5) * cellSizeLng

            val cellCentre = LatLng(
                DecimalFormat(DEFAULT_LOCATION_PATTERN).format(cellCentreLat).toDouble(),
                DecimalFormat(DEFAULT_LOCATION_PATTERN).format(cellCentreLng).toDouble(),
            )

            averagedHeatmapData.add(WeightedLatLng(cellCentre, cellIntensities.average()))
        }

        Log.e("averaged data", averagedHeatmapData.map { it.intensity }.toString())

        return averagedHeatmapData
    }

    private fun refreshHeatmap(newLatLng: LatLng, averagedHeatmapData: ArrayList<WeightedLatLng>) {
        if (!providerBuilt) {
            provider = HeatmapTileProvider.Builder()
                .weightedData(averagedHeatmapData)
                .gradient(Gradient(GRADIENT_COLORS, GRADIENT_START_POINTS))
                .maxIntensity(PROVIDER_MAX_INTENSITY)
                .radius(PROVIDER_RADIUS)
                .build()

            providerBuilt = true
        } else {
            provider.setWeightedData(averagedHeatmapData)
        }

        mMap.clear()
        mMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))
        mMap.addMarker(MarkerOptions().position(newLatLng).title("Current Location"))

        if (!mapCentred) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLatLng, DEFAULT_ZOOM_LEVEL))
            mapCentred = true
        }
    }

    private fun wllToLatLng(wll: WeightedLatLng): LatLng {
        val tau = 2.0 * Math.PI

        val lat = toDegrees(2.0 * (atan(Math.E.pow(tau * (0.5 - wll.point.y))) - (Math.PI / 4.0)))
        val lng = toDegrees(tau * (wll.point.x - 0.5))

        return LatLng(
            DecimalFormat(DEFAULT_LOCATION_PATTERN).format(lat).toDouble(),
            DecimalFormat(DEFAULT_LOCATION_PATTERN).format(lng).toDouble()
        )
    }

    private fun startSoundCheckRunnable() {
        val soundCheckRunnable = object : Runnable {
            override fun run() {
                updateDecibelLevel()
                handler.postDelayed(this, 1000L)
            }
        }

        handler.post(soundCheckRunnable)
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
            MediaRecorder.AudioSource.MIC,
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
            decibel = 20 * log10(maxAmplitude.toDouble() * 0.25)
            Log.e("DecibelMeter", "Decibel: $decibel")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecord?.stop()
        audioRecord?.release()
    }
}
