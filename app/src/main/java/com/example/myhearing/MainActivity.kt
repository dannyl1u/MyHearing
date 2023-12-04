package com.example.myhearing

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.example.myhearing.data.MyHearingDatabaseHelper
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.navigation.NavigationView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var chart: LineChart
    private var dataEntries: ArrayList<Entry> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

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
                    val intent = Intent(this, DecibelMeterDemoActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.nav_item2 -> {
                    val intent = Intent(this, HeatmapActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.nav_item3 -> {
                    val intent = Intent(this, TestHearing::class.java)
                    startActivity(intent)
                    true
                }

                R.id.nav_item4 -> {
                    val intent = Intent(this, CalibrationActivity::class.java)
                    startActivity(intent)
                    true
                }

                else -> false
            }
        }

        // Initialize chart
        initChart()
        chartUpdateHandler.post(chartUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        chartUpdateHandler.removeCallbacks(chartUpdateRunnable)
    }

    override fun onResume() {
        super.onResume()
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

        dataEntries.clear()

        records.forEachIndexed { index, pair ->
            if (pair.second >= 0) {
                dataEntries.add(Entry(index.toFloat(), pair.second))
            }
        }

        val dataSet = LineDataSet(dataEntries, "Decibel Level")
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
}
