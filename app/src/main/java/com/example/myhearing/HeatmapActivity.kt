package com.example.myhearing

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.example.myhearing.data.MyHearingDatabaseHelper
import com.example.myhearing.data.WeightedLocation
import com.example.myhearing.databinding.ActivityHeatmapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.DecimalFormat

class HeatmapActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        const val DEFAULT_LOCATION_PATTERN = "#.#####"
        const val UPDATE_DATA_DELAY_MS = 1000L
        const val REFRESH_HEATMAP_DELAY_MS = 500L
        const val MAX_DB_INTENSITY = 100.0
        const val CELL_SIZE_PX = 40
        val GRADIENT_COLORS = intArrayOf(Color.GREEN, Color.RED)
        val GRADIENT_START_POINTS = floatArrayOf(0.2f, 0.85f)
        const val PROVIDER_MAX_INTENSITY = 1.0
        const val PROVIDER_RADIUS = 30
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
    private var latestDbReading = 0
    private lateinit var latestAveragedLatLng: LatLng
    private var latestLatLngInit = false

    private val latLngMap: MutableMap<LatLng, Int> = mutableMapOf()
    private val weightedHeatmapData = ArrayList<WeightedLocation>()
    private var averagedHeatmapData = ArrayList<WeightedLatLng>()
    private var weightedHeatmapDataMutex = Mutex()
    private var averagedHeatmapDataMutex = Mutex()

    private lateinit var provider: HeatmapTileProvider
    private var providerBuilt = false
    private var mapCentred = false

    private val httpClient = OkHttpClient()
    private val gson = Gson()

    private suspend fun fetchApiData(): List<Triple<Long, LatLng, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("https://myhearingserver.onrender.com/api/v1/getRecent").build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val responseBody = response.body?.string()
                    val apiData: List<ApiRecord> = gson.fromJson(responseBody, object : TypeToken<List<ApiRecord>>() {}.type)

                    apiData.map { record ->
                        val (lat, lng) = record.location.split(", ").map { it.toDouble() }
                        val timestamp = record.timestamp.toLong()
                        val noiseLevel = record.noise_level.toDouble()
                        Triple(timestamp, LatLng(lat, lng), noiseLevel)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    data class ApiRecord(
        val location: String,
        val noise_level: Int,
        val timestamp: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.heatmapToolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.heatmapDrawerLayout,
            binding.heatmapToolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.heatmapDrawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.heatmapNavigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_item1 -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }

                R.id.nav_item2 -> {
                    startActivity(Intent(this, HeatmapActivity::class.java))
                    true
                }

                R.id.nav_item3 -> {
                    startActivity(Intent(this, TestHearingActivity::class.java))
                    true
                }

                R.id.nav_item4 -> {
                    startActivity(Intent(this, CalibrationActivity::class.java))
                    true
                }

                else -> false
            }
        }

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

    override fun onResume() {
        super.onResume()

        if (binding.heatmapDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.heatmapDrawerLayout.closeDrawer(GravityCompat.START, false)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setMaxZoomPreference(19.5f)

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
        val apiRecords = fetchApiData()

        val combinedRecords = newRecords + apiRecords  // Combine records from database and API
        println("Combined records: $combinedRecords")

        withContext(Dispatchers.Default) {
            for (record in newRecords) {
                val newTimestamp = record.first
                val newLatLng = LatLng(
                    DecimalFormat(DEFAULT_LOCATION_PATTERN).format(record.second.latitude)
                        .toDouble(),
                    DecimalFormat(DEFAULT_LOCATION_PATTERN).format(record.second.longitude)
                        .toDouble(),
                )

                val dbReading = record.third.coerceIn(0.0, 300.0)
                val newDbIntensity = (dbReading / MAX_DB_INTENSITY).coerceAtMost(1.0)

                if (newTimestamp > latestTimestamp) {
                    latestTimestamp = newTimestamp
                    latestLatLng = newLatLng
                    latestDbReading = dbReading.toInt()

                    if (!latestLatLngInit) {
                        latestLatLngInit = true
                    }
                }

                weightedHeatmapDataMutex.withLock {
                    if (latLngMap.containsKey(newLatLng)) {
                        weightedHeatmapData.removeIf {
                            newLatLng.latitude == it.lat && newLatLng.longitude == it.lng && newTimestamp > it.time
                        }
                    } else {
                        latLngMap[newLatLng] = 1
                    }

                    weightedHeatmapData.add(
                        WeightedLocation(
                            newLatLng,
                            newDbIntensity,
                            newTimestamp
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                binding.heatmapTvDecibelReading.text =
                    getString(R.string.tvDecibelLevel_text, latestDbReading)
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
                for (wloc in weightedHeatmapData) {
                    val row = ((wloc.lat - minLatLng.latitude) / cellSizeLat).toInt()
                    val col = ((wloc.lng - minLatLng.longitude) / cellSizeLng).toInt()
                    val cell = Pair(row, col)

                    val cellIntensities = cellIntensityMap.getOrDefault(cell, ArrayList())
                    cellIntensities.add(wloc.intensity)
                    cellIntensityMap[cell] = cellIntensities
                }
            }

            val latLngRow = ((latestLatLng.latitude - minLatLng.latitude) / cellSizeLat).toInt()
            val latLngCol = ((latestLatLng.longitude - minLatLng.longitude) / cellSizeLng).toInt()

            val latLngCentreLat = minLatLng.latitude + (latLngRow + 0.5) * cellSizeLat
            val latLngCentreLng = minLatLng.longitude + (latLngCol + 0.5) * cellSizeLng

            latestAveragedLatLng = LatLng(
                DecimalFormat(DEFAULT_LOCATION_PATTERN).format(latLngCentreLat).toDouble(),
                DecimalFormat(DEFAULT_LOCATION_PATTERN).format(latLngCentreLng).toDouble(),
            )

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
        mMap.addMarker(MarkerOptions().position(latestAveragedLatLng).title("Current Location"))

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
}
