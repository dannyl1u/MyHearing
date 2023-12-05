package com.example.myhearing

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioRecord
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.myhearing.data.MyHearingDatabaseHelper
import com.example.myhearing.services.LocationAndNoiseService
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var chart: LineChart
    private var dataEntries: ArrayList<Entry> = ArrayList()

    private val RECORD_AUDIO_PERMISSION_CODE = 123
    private var audioRecord: AudioRecord? = null
    private lateinit var noiseLevelTextView: TextView
    private lateinit var settingsButton: Button
    private val handler = Handler(Looper.getMainLooper())

    private var currentMode = Mode.NUMBER
    private lateinit var progressBar: ProgressBar
    private lateinit var horizontalProgressBar: ProgressBar

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("com.example.myhearing", Context.MODE_PRIVATE)
        requestPermissions()

        // Initialize UI components
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        chart = findViewById(R.id.chart)
        setSupportActionBar(toolbar)

        // Drawer toggle
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Navigation items
        navigationView.setNavigationItemSelectedListener { menuItem ->
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
                    startActivity(Intent(this, TestHearing::class.java))
                    true
                }

                R.id.nav_item4 -> {
                    startActivity(Intent(this, CalibrationActivity::class.java))
                    true
                }

                else -> false
            }
        }
        noiseLevelTextView = findViewById(R.id.tvDecibelLevel)
        settingsButton = findViewById(R.id.settingsButton)

        val intentMode = intent.getStringExtra("selectedMode")
        currentMode = convertStringToMode(intentMode ?: "Number")
        Log.d("MainActivity", currentMode.toString())
        setLayoutForCurrentMode()

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        // Start recording service if permission is granted
        if (checkPermission()) {
            startLocationAndNoiseService()
        } else {
            requestPermission()
        }
        // Initialize chart
        initChart()
        chartUpdateHandler.post(chartUpdateRunnable)

    }

    /** Service implementation, transferred over from DecibelMeterActivity
     */
    private val noiseLevelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val noiseLevel = intent?.getFloatExtra("noise_level", 0.0f) ?: 0.0f
            Log.d("MainActivity", "Received Decibel Level: $noiseLevel dB")
            if (noiseLevel.toInt() >= 0) {
                noiseLevelTextView.text = "Decibel Level: ${noiseLevel.toInt()} dB"
            }
            val progress = noiseLevel.toInt().coerceIn(0, 100)
            // Update db meter in UI
            progressBar.progress = progress
            horizontalProgressBar.progress = progress
        }
    }

    private fun startLocationAndNoiseService() {
        val serviceIntent = Intent(this, LocationAndNoiseService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(noiseLevelReceiver)
        chartUpdateHandler.removeCallbacks(chartUpdateRunnable)
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (prefs.getBoolean("app_info_return", false)) {
                    prefs.edit().putBoolean("app_info_return", false).apply()

                    delay(100L)
                    requestPermissions()
                }
            }
        }

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START, false)
        }

        val filter = IntentFilter("com.example.myhearing.NOISE_LEVEL_UPDATE")
        LocalBroadcastManager.getInstance(this).registerReceiver(noiseLevelReceiver, filter)
        chartUpdateHandler.post(chartUpdateRunnable)
    }

    private fun initChart() {
        val dataSet = LineDataSet(dataEntries, "Decibel Level")
        val lineData = LineData(dataSet)
        chart.data = lineData

        val xAxis = chart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(getXAxisValues())
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f

        chart.axisLeft.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
    }

    private fun getXAxisValues(): ArrayList<String> {
        val labels = ArrayList<String>()
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        for (i in 0..9) {
            calendar.add(Calendar.MINUTE, -1)
            labels.add(0, dateFormat.format(calendar.time))
        }

        return labels
    }

    fun updateChartData() {
        val dbHelper = MyHearingDatabaseHelper(this)
        val records = dbHelper.getRecentDecibelRecords()
        // Set color gradient for Linechart
        val startColor = Color.parseColor("#FF7F7F")
        val endColor = Color.WHITE

        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(startColor, endColor)
        )
        dataEntries.clear()

        records.forEachIndexed { index, pair ->
            if (pair.second >= 0) {
                dataEntries.add(Entry(index.toFloat(), pair.second))
            }
        }
        val dataSet = LineDataSet(dataEntries, "Decibel Level")
        // Fill in bottom part of linechart
        dataSet.setDrawFilled(true)
        dataSet.fillDrawable = gradientDrawable

        chart.data = LineData(dataSet)
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    private val chartUpdateHandler = Handler(Looper.getMainLooper())
    private val chartUpdateRunnable = object : Runnable {
        override fun run() {
            updateChartData()
            chartUpdateHandler.postDelayed(this, 1000)
        }
    }

    private fun requestPermissions() {
        val baseFgPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val fgPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            baseFgPermissions + arrayOf(Manifest.permission.FOREGROUND_SERVICE)
        } else {
            baseFgPermissions
        }

        fgPermissionLauncher.launch(fgPermissions)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val fgPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            val hasAudio = hasPermission(Manifest.permission.RECORD_AUDIO)
            val hasCoarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

            if (hasAudio && hasCoarse && hasFine) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            } else if (hasAudio) {
                launchFineLocationDialog()
            } else if (hasCoarse && hasFine) {
                launchAudioDialog()
            } else {
                launchFgDialog()
            }
        }

    private fun launchFineLocationDialog() {
        val fgLocationPermissions = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app won't work properly without your precise location!")
            .setPositiveButton("Ask Again") { dialog: DialogInterface, _: Int ->
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    fgPermissionLauncher.launch(fgLocationPermissions)
                } else {
                    launchAppInfo()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Deny Anyway") { dialog: DialogInterface, _: Int ->
                if (hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun launchAudioDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app won't work properly without the \"record audio\" permission!")
            .setPositiveButton("Ask Again") { dialog: DialogInterface, _: Int ->
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    fgPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                } else {
                    launchAppInfo()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Deny Anyway") { dialog: DialogInterface, _: Int ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun launchFgDialog() {
        val fgPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app won't work properly without the requested permissions!")
            .setPositiveButton("Ask Again") { dialog: DialogInterface, _: Int ->
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) &&
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                ) {
                    fgPermissionLauncher.launch(fgPermissions)
                } else {
                    launchAppInfo()
                }

                dialog.dismiss()
            }
            .setNegativeButton("Deny Anyway") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun launchAppInfo() {
        val editor = getSharedPreferences("com.example.myhearing", Context.MODE_PRIVATE).edit()
        editor.putBoolean("app_info_return", true)
        editor.apply()

        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private val bgLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                launchBgLocationDialog()
            }
        }

    private fun launchBgLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app won't work properly without knowing your location all the time!")
            .setPositiveButton("Ask Again") { dialog: DialogInterface, _: Int ->
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                } else {
                    launchAppInfo()
                }

                dialog.dismiss()
            }
            .setNegativeButton("Deny Anyway") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private enum class Mode {
        NUMBER, GAUGE, HORIZONTALGAUGE
    }

    private fun setLayoutForCurrentMode() {
        // Set the content view based on the current mode
        when (currentMode) {
            Mode.NUMBER -> {
                progressBar = findViewById(R.id.progressBar)
                horizontalProgressBar = findViewById(R.id.horizontalProgressBar)
                progressBar.visibility = View.GONE
                horizontalProgressBar.visibility = View.GONE
            }

            Mode.GAUGE -> {
                progressBar = findViewById(R.id.progressBar)
                horizontalProgressBar = findViewById(R.id.horizontalProgressBar)
                progressBar.visibility = View.VISIBLE
                horizontalProgressBar.visibility = View.GONE
            }

            Mode.HORIZONTALGAUGE -> {
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
            "Circular Gauge" -> Mode.GAUGE
            "Horizontal Gauge" -> Mode.HORIZONTALGAUGE
            else -> throw IllegalArgumentException("Invalid mode: $modeString")
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
