package com.example.myhearing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder.AudioSource
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.myhearing.databinding.ActivityCalibrationBinding
import kotlin.math.ceil
import kotlin.math.pow

class CalibrationActivity : ComponentActivity() {
    companion object {
        const val RECORD_DURATION_MS = 10000L
        const val RECORD_UPDATE_INTERVAL_MS = 250L
        const val DEFAULT_DECIBELS = 10.0
    }

    private lateinit var binding: ActivityCalibrationBinding

    private var audioRecord: AudioRecord? = null
    private var isCalibrating = false
    private var repeatsLeft = 8
    private var animationShouldStop = false

    private val maxAmplitudeData = ArrayList<Short>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initAudioRecord()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isCalibrating) {
            resetTimer("Calibration canceled")
        }

        audioRecord?.release()
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

    private fun startCalibrationTimer() {
        audioRecord?.startRecording()

        binding.calibrationBtnTimerControl.text =
            getString(R.string.calibration_btnTimerControl_cancel)

        cdt.start()
    }

    private val cdt = object : CountDownTimer(RECORD_DURATION_MS, RECORD_UPDATE_INTERVAL_MS) {
        override fun onTick(millisUntilFinished: Long) {
            updateCalibrationData()

            val secondsRemaining = ceil(millisUntilFinished / 1000.0).toInt()
            binding.calibrationTvTimer.text = getString(
                R.string.calibration_tvTimer_text,
                String.format("%02d", secondsRemaining)
            )
            binding.calibrationPbTimer.progress =
                (100 * secondsRemaining / (RECORD_DURATION_MS / 1000)).toInt()
        }

        override fun onFinish() {
            saveCalibrationFactor()

            binding.calibrationTvTimer.text =
                getString(R.string.calibration_tvTimer_text, String.format("%02d", 0))
            binding.calibrationPbTimer.progress = 0

            val alphaIn = AlphaAnimation(0f, 1f)
            alphaIn.duration = 250
            alphaIn.interpolator = LinearInterpolator()

            val alphaOut = AlphaAnimation(1f, 0f)
            alphaOut.startOffset = alphaIn.duration
            alphaOut.duration = 250
            alphaOut.interpolator = LinearInterpolator()

            val tvAnimation = AnimationSet(false)
            tvAnimation.addAnimation(alphaIn)
            tvAnimation.addAnimation(alphaOut)

            repeatsLeft = 8
            animationShouldStop = false

            tvAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    repeatsLeft -= 1
                }

                override fun onAnimationEnd(animation: Animation?) {
                    if (!animationShouldStop) {
                        if (repeatsLeft > 0) {
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

        showToast("Device Calibrated")
    }

    fun onTimerControlClick(view: View) {
        if (hasRecordAudioPermission()) {
            if (isCalibrating) {
                resetTimer("Calibration canceled")
            } else {
                isCalibrating = true
                startCalibrationTimer()
            }
        } else {
            showToast("Requires \"record audio\" permission")
        }
    }

    private fun resetTimer(toastText: String? = null) {
        cdt.cancel()
        audioRecord?.stop()

        binding.calibrationBtnTimerControl.text =
            getString(R.string.calibration_btnTimerControl_start)
        binding.calibrationTvTimer.text = getString(
            R.string.calibration_tvTimer_text,
            String.format("%02d", RECORD_DURATION_MS / 1000)
        )

        binding.calibrationPbTimer.progress = 100
        binding.calibrationTvTimer.clearAnimation()

        animationShouldStop = true
        isCalibrating = false

        if (toastText != null) {
            showToast(toastText)
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
