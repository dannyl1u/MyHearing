package com.example.myhearing

import android.media.MediaRecorder
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity


class HeatMap : ComponentActivity() {

    private val RECORD_AUDIO_PERMISSION_CODE = 123
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var decibelTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.heat_map)
//        decibelTextView = findViewById(R.id.dbLevelTextView)

//        if (checkPermission()) {
//            initMediaRecorder()
//        } else {
//            requestPermission()
//        }
    }
}