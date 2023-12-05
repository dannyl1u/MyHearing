package com.example.myhearing

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myhearing.data.MyHearingDatabaseHelper
import com.example.myhearing.databinding.ActivityHeatmapBinding
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.Math.toDegrees
import java.text.DecimalFormat
import kotlin.math.atan
import kotlin.math.pow

// TODO: Tune companion object values

class HeatmapActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        const val DEFAULT_LOCATION_PATTERN = "#.#######"
        const val UPDATE_DATA_DELAY_MS = 1000L
        const val REFRESH_HEATMAP_DELAY_MS = 500L
        const val MAX_DB_INTENSITY = 100.0
        const val CELL_SIZE_PX = 50
        val GRADIENT_COLORS = intArrayOf(Color.GREEN, Color.YELLOW, Color.RED)
        val GRADIENT_START_POINTS = floatArrayOf(0.2f, 0.6f, 0.85f)
        const val PROVIDER_MAX_INTENSITY = 1.0
        const val PROVIDER_RADIUS = 35
        const val DEFAULT_ZOOM_LEVEL = 18f
    }

    private lateinit var binding: ActivityHeatmapBinding
    private lateinit var dbHelper: MyHearingDatabaseHelper
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var mMap: GoogleMap

    private var gridRows = 1
    private var gridCols = 1

    private var latestTimestamp = 0L
    private lateinit var latestLatLng: LatLng
    private var latestLatLngInit = false

    private val latLngMap: MutableMap<LatLng, Int> = mutableMapOf()
    private val weightedHeatmapData = ArrayList<WeightedLatLng>()
    private var averagedHeatmapData = ArrayList<WeightedLatLng>()
    private var weightedHeatmapDataMutex = Mutex()
    private var averagedHeatmapDataMutex = Mutex()

    private lateinit var provider: HeatmapTileProvider
    private var providerBuilt = false
    private var mapCentred = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = MyHearingDatabaseHelper(this)

        lifecycleScope.launch {
            while (true) {
                updateWeightedHeatmapData()
                delay(UPDATE_DATA_DELAY_MS)
            }
        }

        mapFragment =
            supportFragmentManager.findFragmentById(R.id.heatmap_map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val mapView = mapFragment.requireView()
        gridRows = (mapView.height / CELL_SIZE_PX)
        gridCols = (mapView.width / CELL_SIZE_PX)

        lifecycleScope.launch {
            while (!latestLatLngInit || weightedHeatmapData.size == 0) {
                delay(50L)
            }

            while (true) {
                updateAveragedHeatmapData()

                withContext(Dispatchers.Main) {
                    refreshHeatmap()
                }

                delay(REFRESH_HEATMAP_DELAY_MS)
            }
        }
    }

    private suspend fun updateWeightedHeatmapData() {
        val newRecords = withContext(Dispatchers.IO) {
            dbHelper.getRecordsSince(latestTimestamp)
        }

        withContext(Dispatchers.Default) {
            for (record in newRecords) {
                val newTimestamp = record.first
                val newLatLng = record.second

                val dbReading = record.third
                val newDbIntensity = dbReading / MAX_DB_INTENSITY

                if (newTimestamp > latestTimestamp) {
                    latestTimestamp = newTimestamp
                    latestLatLng = newLatLng

                    withContext(Dispatchers.Main) {
                        binding.heatmapTvDecibelReading.text =
                            getString(R.string.heatmap_tvDecibelReading_text, dbReading.toInt())
                    }

                    if (!latestLatLngInit) {
                        latestLatLngInit = true
                    }
                }

                weightedHeatmapDataMutex.withLock {
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
            }
        }
    }

    private suspend fun updateAveragedHeatmapData() {
        val visibleRegionBounds = withContext(Dispatchers.Main) {
            mMap.projection.visibleRegion.latLngBounds
        }

        withContext(Dispatchers.Default) {
            val maxLatLng = visibleRegionBounds.northeast
            val minLatLng = visibleRegionBounds.southwest

            val cellSizeLat = (maxLatLng.latitude - minLatLng.latitude) / gridRows
            val cellSizeLng = (maxLatLng.longitude - minLatLng.longitude) / gridCols

            val cellIntensityMap: MutableMap<Pair<Int, Int>, ArrayList<Double>> = mutableMapOf()

            weightedHeatmapDataMutex.withLock {
                for (wll in weightedHeatmapData) {
                    val latLng = wllToLatLng(wll)

                    val row = ((latLng.latitude - minLatLng.latitude) / cellSizeLat).toInt()
                    val col = ((latLng.longitude - minLatLng.longitude) / cellSizeLng).toInt()
                    val cell = Pair(row, col)

                    val cellIntensities = cellIntensityMap.getOrDefault(cell, ArrayList())
                    cellIntensities.add(wll.intensity)
                    cellIntensityMap[cell] = cellIntensities
                }
            }

            averagedHeatmapDataMutex.withLock {
                averagedHeatmapData = ArrayList()

                for ((cell, cellIntensities) in cellIntensityMap) {
                    val cellCentreLat = minLatLng.latitude + (cell.first + 0.5) * cellSizeLat
                    val cellCentreLng = minLatLng.longitude + (cell.second + 0.5) * cellSizeLng

                    val cellCentre = LatLng(
                        DecimalFormat(DEFAULT_LOCATION_PATTERN).format(cellCentreLat).toDouble(),
                        DecimalFormat(DEFAULT_LOCATION_PATTERN).format(cellCentreLng).toDouble(),
                    )

                    averagedHeatmapData.add(WeightedLatLng(cellCentre, cellIntensities.average()))
                }
            }
        }
    }

    private suspend fun refreshHeatmap() {
        // TODO: Remove before submission
        binding.heatmapTvZoomLevel.text = "Zoom Level: ${mMap.cameraPosition.zoom}"

        averagedHeatmapDataMutex.withLock {
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
        }

        mMap.clear()
        mMap.addTileOverlay(TileOverlayOptions().tileProvider(provider))
        mMap.addMarker(MarkerOptions().position(latestLatLng).title("Current Location"))

        if (!mapCentred) {
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    latestLatLng,
                    DEFAULT_ZOOM_LEVEL
                )
            )
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
