package com.example.myhearing

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.DialogFragment
import com.example.myhearing.databinding.ActivityTestHearingBinding

class TestHearingActivity : AppCompatActivity(), ResultFragment.OnOkButtonClickListener {
    private lateinit var binding: ActivityTestHearingBinding

    private lateinit var startButton: Button
    private lateinit var leftEar: ImageView
    private lateinit var rightEar: ImageView

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var noisePlayer: MediaPlayer
    private lateinit var randomAudioResources: List<Int>

    private val clickedImageIds = mutableListOf<String>()
    private var testIteration = 0

    private val allAudioResources = listOf(
        R.raw.dog, R.raw.cat, R.raw.car, R.raw.king, R.raw.queen,
        R.raw.jar, R.raw.frog, R.raw.door, R.raw.rat
    )
    private val noiseLevelList = listOf(0.05f, 0.3f, 0.7f, 0.05f, 0.3f, 0.7f)
    private var noiseIndex = 0
    private val sideCounter = listOf("left", "left", "left", "right", "right", "right")
    private var sideIndex = 0

    private var leftScore: Int = 0
    private var rightScore: Int = 0
    private lateinit var leftScoreTV: TextView
    private lateinit var rightScoreTV: TextView

    private lateinit var overlayCover: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTestHearingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.testHearingToolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.testHearingDrawerLayout,
            binding.testHearingToolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.testHearingDrawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.testHearingNavigationView.setNavigationItemSelectedListener { menuItem ->
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

        startButton = findViewById(R.id.startButton)
        leftEar = findViewById(R.id.left_ear)
        rightEar = findViewById(R.id.right_ear)
        leftScoreTV = findViewById(R.id.leftScore)
        rightScoreTV = findViewById(R.id.rightScore)

        overlayCover = findViewById(R.id.overlay)
        overlayCover.bringToFront()
        overlayCover.visibility = View.VISIBLE
        overlayCover.isClickable = true
        overlayCover.isFocusable = true
        overlayCover.bringToFront()

        randomAudioResources = listOf() // init here, otherwise will crash

        startButton.setOnClickListener {
            audioAndNoise("left")
            startButton.visibility = GONE
        }
        mediaPlayer = MediaPlayer.create(this, R.raw.cat)
        noisePlayer = MediaPlayer.create(this, R.raw.noise)
        // a frag pop up to suggest ear phones.
        val headphoneFragment = EarPhoneFragment()
        headphoneFragment.show(supportFragmentManager, "HeadPhoneFragmentTag")

    }

    override fun onResume() {
        super.onResume()

        if (binding.testHearingDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.testHearingDrawerLayout.closeDrawer(GravityCompat.START, false)
        }
    }

    private fun audioAndNoise(side: String) {
        if (noiseIndex >= 6) {
            leftEar.alpha = 0.2f
            rightEar.alpha = 0.2f
            val leftScorePercent = leftScore * 100 / 9
            val rightScorePercent = rightScore * 100 / 9
            showResultFragment(leftScorePercent, rightScorePercent)

            return
        }

        overlayCover.bringToFront()
        overlayCover.visibility = View.VISIBLE
        overlayCover.isClickable = true
        overlayCover.isFocusable = true
        overlayCover.bringToFront()

        randomAudioResources = getRandomAudioResources(allAudioResources, 3)
        playAudioSequence(sideCounter[sideIndex], *randomAudioResources.toIntArray())
        playNoise(noiseLevelList[noiseIndex], sideCounter[sideIndex])
        noiseIndex++
        sideIndex++

    }

    private fun getRandomAudioResources(audioList: List<Int>, count: Int): List<Int> {
        require(count <= audioList.size) { "Count should be less than or equal to the size of the audio list." }

        val shuffledList = audioList.shuffled()
        return shuffledList.subList(0, count)
    }

    private fun playAudioSequence(side: String, vararg audioResources: Int) {
        var mediaPlayer: MediaPlayer? = null

        fun playNextAudio(index: Int) {
            if (index < audioResources.size) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(resources.openRawResourceFd(audioResources[index]))
                    if (side == "left") {
                        setVolume(1.0f, 0.0f) // 100% on left ear. 0% on right
                    } else {
                        setVolume(0.0f, 1.0f) // 0% on left ear, 100% on right
                    }

                    setOnCompletionListener { playNextAudio(index + 1) }
                    prepare()
                    start()
                }
            } else {
                mediaPlayer?.release()
            }
        }

        playNextAudio(0)
    }

    private fun playNoise(fl: Float, side: String) {
        when (side) {
            "left" -> {
                leftEar.alpha = 1f
                rightEar.alpha = 0.2f
            }

            "right" -> {
                rightEar.alpha = 1f
                leftEar.alpha = 0.2f
            }

            else -> {
                leftEar.alpha = 0.2f
                rightEar.alpha = 0.2f
            }
        }

        val noisePlayer: MediaPlayer?
        noisePlayer = MediaPlayer().apply {
            setDataSource(resources.openRawResourceFd(R.raw.noise))

            if (side == "left") {
                // left ear
                setVolume(fl, 0.0f)
            } else {
                // right ear
                setVolume(0.0f, fl)
            }
            prepare()
            start()
        }


        noisePlayer.setOnCompletionListener { mediaPlayer ->
            // finished playing a audio
            // remove overlayCover
            overlayCover.visibility = GONE
            mediaPlayer.release()
        }
        // start audio
        noisePlayer.start()
    }


    fun onImageClick(view: View) {
        if (randomAudioResources.isNotEmpty()) {
            println("<<< there IS random audio")
            // Check which image was clicked based on its ID
            when (view.id) {
                R.id.dog -> handleImageClick("dog", R.raw.dog)
                R.id.cat -> handleImageClick("cat", R.raw.cat)
                R.id.car -> handleImageClick("car", R.raw.car)
                R.id.king -> handleImageClick("king", R.raw.king)
                R.id.queen -> handleImageClick("queen", R.raw.queen)
                R.id.jar -> handleImageClick("jar", R.raw.jar)
                R.id.frog -> handleImageClick("frog", R.raw.frog)
                R.id.door -> handleImageClick("door", R.raw.door)
                R.id.rat -> handleImageClick("rat", R.raw.rat)
            }
        } else {
            println("<<<<< no random audio yet")
        }
    }


    private fun handleImageClick(answer: String, audioResource: Int) {
        clickedImageIds.add(answer)

        when (audioResource) {
            R.id.dog -> findViewById<ImageView>(R.id.dog).setBackgroundResource(R.drawable.selected_background)
            R.id.cat -> findViewById<ImageView>(R.id.cat).setBackgroundResource(R.drawable.selected_background)
            R.id.car -> findViewById<ImageView>(R.id.car).setBackgroundResource(R.drawable.selected_background)
            R.id.king -> findViewById<ImageView>(R.id.king).setBackgroundResource(R.drawable.selected_background)
            R.id.queen -> findViewById<ImageView>(R.id.queen).setBackgroundResource(R.drawable.selected_background)
            R.id.jar -> findViewById<ImageView>(R.id.jar).setBackgroundResource(R.drawable.selected_background)
            R.id.frog -> findViewById<ImageView>(R.id.frog).setBackgroundResource(R.drawable.selected_background)
            R.id.door -> findViewById<ImageView>(R.id.door).setBackgroundResource(R.drawable.selected_background)
            R.id.rat -> findViewById<ImageView>(R.id.rat).setBackgroundResource(R.drawable.selected_background)
        }

        if (clickedImageIds.size == 3) {
            // check user selection, give score
            val selectedAudioResources = clickedImageIds.map { getAudioResourceForAnswer(it) }
            val correctCount = selectedAudioResources.count { it in randomAudioResources }

            if (sideIndex == 0 || sideIndex == 1 || sideIndex == 2 || sideIndex == 3) {
                leftScore += correctCount
                leftScoreTV.text = getString(R.string.testHearing_leftScoreResult, leftScore)
            } else {
                rightScore += correctCount
                rightScoreTV.text = getString(R.string.testHearing_rightScoreResult, rightScore)
            }
            clickedImageIds.clear()

            overlayCover.bringToFront()
            overlayCover.visibility = View.VISIBLE
            overlayCover.isClickable = true
            overlayCover.isFocusable = true
            overlayCover.bringToFront()


            if (testIteration in 3..5) {
                // left ear finish. right ear start
                audioAndNoise("right")
            } else if (testIteration < 3) {
                // still left ear.
                audioAndNoise("left")
                testIteration++
            } else {
                // end of test
//                showToast("end of test")
            }
        }
    }

    private fun getAudioResourceForAnswer(answer: String): Int {
        // queen > cat > king > rat > frog > door > jar > dog > car

        return when (answer) {
            "dog" -> R.raw.dog
            "cat" -> R.raw.cat
            "car" -> R.raw.car
            "king" -> R.raw.king
            "queen" -> R.raw.queen
            "jar" -> R.raw.jar
            "frog" -> R.raw.frog
            "door" -> R.raw.door
            "rat" -> R.raw.rat
            else -> 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaPlayer.release()
        noisePlayer.stop()
        noisePlayer.release()
    }

    override fun onOkButtonClick() {
        finish()
    }

    private fun showResultFragment(leftScore: Int, rightScore: Int) {
        val resultFragment = ResultFragment()
        val args = Bundle().apply {
            putInt("leftScore", leftScore)
            putInt("rightScore", rightScore)
        }

        resultFragment.arguments = args
        resultFragment.onOkButtonClickListener = this
        resultFragment.show(supportFragmentManager, "ResultFragmentTag")
    }
}

class ResultFragment : DialogFragment() {
    interface OnOkButtonClickListener {
        fun onOkButtonClick()
    }

    var onOkButtonClickListener: OnOkButtonClickListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val leftScore = arguments?.getInt("leftScore", 0) ?: 0
        val rightScore = arguments?.getInt("rightScore", 0) ?: 0
        val leftEarTV: TextView = view.findViewById(R.id.leftEar)
        val rightEarTV: TextView = view.findViewById(R.id.rightEar)
        val overallTV: TextView = view.findViewById(R.id.overallResult)

        leftEarTV.text = getString(R.string.testHearing_leftScorePercent, leftScore)
        rightEarTV.text = getString(R.string.testHearing_rightScorePercent, rightScore)

        val overallScore = (leftScore + rightScore) / 2
        if (overallScore >= 66.66) {
            // Green: pretty good
            overallTV.text = getString(R.string.testHearing_overallScore_1, overallScore)
            overallTV.setTextColor(Color.parseColor("#00bf10")) //green
        } else if (overallScore >= 44.44) {
            // Orange : could use improvement
            overallTV.text = getString(R.string.testHearing_overallScore_2, overallScore)
            overallTV.setTextColor(Color.parseColor("#e38800")) //orange
        } else {
            // Red : HORRIBLE
            overallTV.text = getString(R.string.testHearing_overallScore_3, overallScore)
            overallTV.setTextColor(Color.parseColor("#bf1506")) //red
        }

        val okButton: Button = view.findViewById(R.id.okButton)
        okButton.setOnClickListener {
            onOkButtonClickListener?.onOkButtonClick()
            dismiss()
        }
    }
}

class EarPhoneFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_earphone, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val videoView: VideoView = view.findViewById(R.id.earPhoneVideo)
        val videoPath = "android.resource://" + requireContext().packageName + "/" + R.raw.earphone
        videoView.setVideoURI(Uri.parse(videoPath))
        val mediaController = MediaController(requireContext())
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        videoView.start()

        val okButton: Button = view.findViewById(R.id.okButton)
        okButton.setOnClickListener {
            dismiss()
        }
    }
}
