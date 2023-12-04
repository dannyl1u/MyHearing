package com.example.myhearing

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.media.AudioRecord
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myhearing.data.MyHearingDatabaseHelper
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
import kotlin.math.pow

// TODO: Tune most companion object vals

class HeatmapActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        const val MAX_DB_INTENSITY = 100.0
        const val LOCATION_UPDATE_INTERVAL_MS = 1000L
        const val DEFAULT_LOCATION_PATTERN = "#.#######"
        val GRADIENT_COLORS = intArrayOf(Color.GREEN, Color.YELLOW, Color.RED)
        val GRADIENT_START_POINTS = floatArrayOf(0.2f, 0.6f, 0.85f)
        const val PROVIDER_MAX_INTENSITY = 1.0
        const val PROVIDER_RADIUS = 35
        const val DEFAULT_ZOOM_LEVEL = 18f
    }

    private lateinit var binding: ActivityHeatmapBinding

    private lateinit var mMap: GoogleMap

    private val latLngMap: MutableMap<LatLng, Int> = mutableMapOf()
    private val weightedHeatmapData = ArrayList<WeightedLatLng>()
    private lateinit var provider: HeatmapTileProvider
    private var providerBuilt = false
    private var mapCentred = false

    private val handler = Handler(Looper.getMainLooper())
    private val handler2 = Handler(Looper.getMainLooper())
    private var decibel = 0.0

    private var gridRows = 0
    private var gridCols = 0

    private lateinit var lastLatLng: LatLng
    private var lastLatLngInit = false

    private var mostRecentTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()
    }

    override fun onMapReady(googleMap: GoogleMap) {
//        val fgLocationPermissions = arrayOf(
//            Manifest.permission.ACCESS_COARSE_LOCATION,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        )
//
//        val fgLocationPermissionsGranted = fgLocationPermissions.all {
//            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
//        }
//
//        if (!fgLocationPermissionsGranted) {
//            return
//        }

//        val mapFragment =
//            supportFragmentManager.findFragmentById(R.id.heatmap_map) as SupportMapFragment
//        val mapView = mapFragment.requireView()
//
//        gridRows = (mapView.height / 50.0).toInt()
//        gridCols = (mapView.width / 50.0).toInt()

        mMap = googleMap

        startUpdateDataRunnable()

//        val locationCallback = object : LocationCallback() {
//            override fun onLocationResult(locationResult: LocationResult) {
//                updateWeightedHeatmapData(locationResult.lastLocation ?: return)
//            }
//        }

//        fusedLocationClient.requestLocationUpdates(
//            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL_MS)
//                .build(),
//            locationCallback,
//            Looper.getMainLooper()
//        )
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
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.heatmap_map) as SupportMapFragment

        val mapView = mapFragment.requireView()
        gridRows = (mapView.height / 50.0).toInt()
        gridCols = (mapView.width / 50.0).toInt()

        mapFragment.getMapAsync(this)
    }

    private fun updateWeightedHeatmapData() {
        val dbHelper = MyHearingDatabaseHelper(this)
        val newData = dbHelper.getRecordsSince(mostRecentTimestamp)

        for (record in newData) {
            val newTimestamp = record.first
            val newLatLng = record.second
            decibel = record.third
            val newDbIntensity = decibel / MAX_DB_INTENSITY

            if (newTimestamp > mostRecentTimestamp) {
                mostRecentTimestamp = newTimestamp
                lastLatLng = newLatLng
            }

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
        }

        if (!lastLatLngInit && weightedHeatmapData.size > 0) {
            startRefreshHeatmapRunnable()
            lastLatLngInit = true
        }
    }

    private fun getAveragedHeatmapData(): ArrayList<WeightedLatLng> {
        val visibleRegionBounds = mMap.projection.visibleRegion.latLngBounds
        val maxLatLng = visibleRegionBounds.northeast
        val minLatLng = visibleRegionBounds.southwest

        val cellSizeLat = (maxLatLng.latitude - minLatLng.latitude) / gridRows
        val cellSizeLng = (maxLatLng.longitude - minLatLng.longitude) / gridCols

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

    private fun startRefreshHeatmapRunnable() {
        val refreshHeatmapRunnable = object : Runnable {
            override fun run() {
                refreshHeatmap(getAveragedHeatmapData())
                handler.postDelayed(this, 500L)
            }
        }

        handler.post(refreshHeatmapRunnable)
    }

    private fun startUpdateDataRunnable() {
        val updateDataRunnable = object : Runnable {
            override fun run() {
                updateWeightedHeatmapData()
                handler2.postDelayed(this, 500L)
            }
        }

        handler2.post(updateDataRunnable)
    }

    private fun refreshHeatmap(averagedHeatmapData: ArrayList<WeightedLatLng>) {
        findViewById<TextView>(R.id.heatmap_tvDecibelLevel).text =
            "Decibel Level: ${decibel.toInt()}"
        val zoomLevel = mMap.cameraPosition.zoom
        findViewById<TextView>(R.id.heatmap_tvZoomLevel).text = "Zoom Level: $zoomLevel"

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
        mMap.addMarker(MarkerOptions().position(lastLatLng).title("Current Location"))

        if (!mapCentred) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lastLatLng, DEFAULT_ZOOM_LEVEL))
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
}
