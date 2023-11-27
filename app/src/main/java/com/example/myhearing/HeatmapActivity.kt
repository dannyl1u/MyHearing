package com.example.myhearing

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import java.text.DecimalFormat
import kotlin.random.Random

class HeatmapActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityHeatmapBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap
    private var mapCentred = false

    private val weightedHeatmapData = ArrayList<WeightedLatLng>()
    private val weightedLatLngMap: MutableMap<LatLng, WeightedLatLng> = mutableMapOf()
    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermissions()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                performLocationCallback(locationResult)
            }
        }
    }

    private fun performLocationCallback(locationResult: LocationResult) {
        locationResult.lastLocation?.let { location ->
            val currentLatLng = LatLng(location.latitude, location.longitude)

            val dbLevels = arrayOf(0, 10, 20, 30, 40)
            val randIndex = Random(System.currentTimeMillis()).nextInt(dbLevels.size)
            val randDbLevel = dbLevels[randIndex]
            val dbWeight = randDbLevel.toDouble() / 85.0

            addNewWeightedLatLng(currentLatLng, dbWeight)

            mMap.clear()
            mMap.addMarker(
                MarkerOptions().position(currentLatLng).title("Current Location")
            )

            val colours = intArrayOf(
                Color.rgb(0, 255, 0),
                Color.rgb(255, 0, 0)
            )

            val gradient = Gradient(colours, floatArrayOf(0.4f, 1f))

            val provider: HeatmapTileProvider = HeatmapTileProvider.Builder()
                .weightedData(weightedHeatmapData)
                .gradient(gradient)
                .maxIntensity(1.0)
                .radius(50)
                .build()

            mMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))

            if (!mapCentred) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                mapCentred = true
            }
        }
    }

    private fun addNewWeightedLatLng(latLng: LatLng, dbWeight: Double) {
        val roundedLatLng = LatLng(
            DecimalFormat("#.#####").format(latLng.latitude).toDouble(),
            DecimalFormat("#.#####").format(latLng.longitude).toDouble()
        )

        if (weightedLatLngMap[roundedLatLng] != null) {
            val tempWeightedLatLng = WeightedLatLng(roundedLatLng, 0.5)
            weightedHeatmapData.removeIf { it.point.x == tempWeightedLatLng.point.x && it.point.y == tempWeightedLatLng.point.y }
        }

        weightedLatLngMap[roundedLatLng] = WeightedLatLng(roundedLatLng, dbWeight)
        weightedHeatmapData.add(WeightedLatLng(roundedLatLng, dbWeight))
    }

//    override fun onPause() {
//        super.onPause()
//        fusedLocationClient.removeLocationUpdates(locationCallback)
//    }

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
                initMap()

                if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == false) {
                    // launch dialog
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            } else {
                // launch dialog
            }
        }

    private val bgLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                // launch dialog
            }
        }

    private fun initMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        fusedLocationClient.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build(),
            locationCallback,
            Looper.getMainLooper()
        )
    }
}
