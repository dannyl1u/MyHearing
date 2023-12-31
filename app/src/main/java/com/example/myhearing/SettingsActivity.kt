package com.example.myhearing

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var modeSpinner: Spinner
    private lateinit var applySettingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        modeSpinner = findViewById(R.id.spinnerMode)
        applySettingsButton = findViewById(R.id.applyButton)

        val modeOptions = arrayOf("Number", "Circular Gauge", "Horizontal Gauge")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeOptions)
        modeSpinner.adapter = adapter

        applySettingsButton.setOnClickListener {
            applySettings()
        }
    }

    private fun applySettings() {
        val selectedMode = modeSpinner.selectedItem.toString()

        val prefs = getSharedPreferences("Settings", 0)
        prefs.edit().putString("selectedMode", selectedMode).apply()

        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("selectedMode", selectedMode)
        startActivity(intent)

        finish()
    }
}