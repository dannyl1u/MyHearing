package com.example.myhearing

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.myhearing.databinding.ActivityCalibrationBinding
import kotlin.math.ceil
import kotlin.math.pow

// TODO: Tune RECORD_DURATION_MS, RECORD_UPDATE_INTERVAL_MS, and DEFAULT_DECIBELS

class CalibrationActivity : ComponentActivity() {
    companion object {
        const val RECORD_DURATION_MS = 10000L
        const val RECORD_UPDATE_INTERVAL_MS = 500L
        const val DEFAULT_DECIBELS = 10.0
    }

    private lateinit var binding: ActivityCalibrationBinding

    private lateinit var cdt: CountDownTimer
    private var audioRecord: AudioRecord? = null
    private var isCalibrating = false

    private var repeatsLeft = 8
    private var animationShouldStop = false

    private val maxAmplitudeData = ArrayList<Short>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onDestroy() {
        super.onDestroy()

        audioRecord?.stop()
        audioRecord?.release()
    }

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                initAudioRecord()
            } else {
                launchRecordAudioDialog()
            }
        }

    private fun initAudioRecord() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_DENIED
        ) {
            return
        }

        val sampleRateInHz = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            AudioSource.MIC,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            bufferSize
        )
    }

    private fun launchRecordAudioDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Calibration won't work properly without the \"record audio\" permission!")
            .setPositiveButton("Ask Again") { dialog: DialogInterface, _: Int ->
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun startCalibrationTimer() {
        audioRecord?.startRecording()

        binding.calibrationBtnTimerControl.text = "CANCEL"

        cdt = object : CountDownTimer(RECORD_DURATION_MS, RECORD_UPDATE_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                updateCalibrationData()

                val secondsRemaining = ceil(millisUntilFinished / 1000.0).toInt()
                binding.calibrationTvTimer.text = "00:${String.format("%02d", secondsRemaining)}"
                binding.calibrationPbTimer.progress =
                    (100 * secondsRemaining / (RECORD_DURATION_MS / 1000)).toInt()
            }

            override fun onFinish() {
                saveCalibrationFactor()

                binding.calibrationTvTimer.text = "00:00"
                binding.calibrationPbTimer.progress = 0

                val fadeIn = AlphaAnimation(0f, 1f)
                fadeIn.duration = 250
                fadeIn.interpolator = LinearInterpolator()

                val fadeOut = AlphaAnimation(1f, 0f)
                fadeOut.startOffset = fadeIn.duration
                fadeOut.duration = 250
                fadeOut.interpolator = LinearInterpolator()

                val tvAnimation = AnimationSet(false)
                tvAnimation.addAnimation(fadeIn)
                tvAnimation.addAnimation(fadeOut)

                repeatsLeft = 8
                animationShouldStop = false

                tvAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {
                        repeatsLeft -= 1
                    }

                    override fun onAnimationEnd(animation: Animation?) {
                        if (!animationShouldStop) {
                            if (repeatsLeft > 1) {
                                binding.calibrationTvTimer.startAnimation(tvAnimation)
                            } else {
                                resetTimer()
                            }
                        }
                    }

                    override fun onAnimationRepeat(animation: Animation?) {
                        // pass
                    }

                })

                binding.calibrationTvTimer.startAnimation(tvAnimation)
            }
        }

        cdt.start()
    }

    private fun updateCalibrationData() {
        val bufferSize = audioRecord?.bufferSizeInFrames ?: 0
        val audioData = ShortArray(bufferSize)
        val readResult = audioRecord?.read(audioData, 0, bufferSize)

        if (readResult != null && readResult != AudioRecord.ERROR_BAD_VALUE) {
            maxAmplitudeData.add(audioData.max())
        }
    }

    private fun saveCalibrationFactor() {
        val averageMaxAmplitude = maxAmplitudeData.average()
        val calibrationFactor = (10.0).pow(DEFAULT_DECIBELS / 20.0) / averageMaxAmplitude

        val editor = getSharedPreferences("com.example.myhearing", Context.MODE_PRIVATE).edit()
        editor.putFloat("calibration_factor", calibrationFactor.toFloat())
        editor.apply()

        Toast.makeText(this, "Device Calibrated", Toast.LENGTH_SHORT).show()
    }

    fun onTimerControlClick(view: View) {
        if (isCalibrating) {
            cdt.cancel()
            resetTimer()
        } else {
            isCalibrating = true
            startCalibrationTimer()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun resetTimer() {
        audioRecord?.stop()

        binding.calibrationBtnTimerControl.text = "START"
        binding.calibrationTvTimer.text = "00:${String.format("%02d", RECORD_DURATION_MS / 1000)}"
        binding.calibrationPbTimer.progress = 100
        binding.calibrationTvTimer.clearAnimation()

        isCalibrating = false
        animationShouldStop = true

    }
}