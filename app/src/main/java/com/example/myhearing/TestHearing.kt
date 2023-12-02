package com.example.myhearing

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.google.android.material.snackbar.Snackbar

class TestHearing : AppCompatActivity(), GoodJobFragment.OnOkButtonClickListener {
    private lateinit var StartButton : Button
    private lateinit var leftEar : ImageView
    private lateinit var rightEar : ImageView

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var noisePlayer: MediaPlayer
    private lateinit var randomAudioResources: List<Int>

    private val clickedImageIds = mutableListOf<String>()
    private var testIteration = 0

    val allAudioResources = listOf(
        R.raw.dog, R.raw.cat, R.raw.car, R.raw.king, R.raw.queen,
        R.raw.jar, R.raw.frog, R.raw.door, R.raw.rat
    )
    val noiseLevelList = listOf(0.05f, 0.3f, 0.7f, 0.05f, 0.3f, 0.7f)
    private var noiseIndex = 0
    val sideCounter = listOf("left", "left", "left", "right", "right", "right")
    private var sideIndex = 0

    private var leftScore : Int = 0
    private var rightScore : Int = 0
    private lateinit var leftScoreTV : TextView
    private lateinit var rightScoreTV : TextView


    private lateinit var overlayCover : FrameLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_hearing)
        StartButton = findViewById(R.id.startButton)
        leftEar = findViewById(R.id.leftear)
        rightEar = findViewById(R.id.rightear)
        leftScoreTV = findViewById(R.id.leftScore)
        rightScoreTV = findViewById(R.id.rightScore)

        overlayCover = findViewById(R.id.overlay)
        overlayCover.bringToFront()
        overlayCover.visibility = View.VISIBLE
        overlayCover.isClickable = true
        overlayCover.isFocusable = true
        overlayCover.bringToFront()

        randomAudioResources = listOf() // init here, otherwise will crash


        StartButton.setOnClickListener {
            showGoodJobFragment(leftScore,rightScore)
//            audioAndNoise("left")
        }
        mediaPlayer = MediaPlayer.create(this, R.raw.cat)
        noisePlayer = MediaPlayer.create(this,R.raw.noise5_1)


    }



    private fun audioAndNoise(side: String) {
        if (noiseIndex>=6) {
            showToast(" test ends ")
            leftEar.alpha = 0.2f
            rightEar.alpha = 0.2f
            val leftScorePercent = leftScore*100/9
            val rightScorePercent = rightScore*100/9
            showGoodJobFragment(leftScorePercent,rightScorePercent)

            return
        }
        // Re-instate layoutcover
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
    fun getRandomAudioResources(audioList: List<Int>, count: Int): List<Int> {
        require(count <= audioList.size) { "Count should be less than or equal to the size of the audio list." }

        val shuffledList = audioList.shuffled()
        return shuffledList.subList(0, count)
    }
    private fun playAudioSequence(side : String, vararg audioResources: Int ) {
        var mediaPlayer: MediaPlayer? = null

        fun playNextAudio(index: Int) {
            if (index < audioResources.size) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(resources.openRawResourceFd(audioResources[index]))
                    if (side == "left") {
                        setVolume(1.0f, 0.0f)
                    } else {
                        setVolume(0.0f, 1.0f)
                    }

                    setOnCompletionListener { playNextAudio(index + 1) }
                    prepare()
                    start()
                }
            } else {
                // Release the last MediaPlayer when all audios are played
                mediaPlayer?.release()
            }
        }

        playNextAudio(0)
    }
    private fun playNoise(fl: Float, side: String) {
        if (side=="left") {
            leftEar.alpha = 1f
            rightEar.alpha = 0.2f
        } else if (side == "right") {
            rightEar.alpha = 1f
            leftEar.alpha = 0.2f
        } else {
            leftEar.alpha = 0.2f
            rightEar.alpha = 0.2f
        }

        var noisePlayer: MediaPlayer? = null
        noisePlayer = MediaPlayer().apply {
            setDataSource(resources.openRawResourceFd(R.raw.noise5_1))

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
            overlayCover.visibility = View.GONE
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
        // Do something with the answer and audio resource ID
        clickedImageIds.add(answer)

        // Change the background of the clicked ImageView
        val resourceId = resources.getIdentifier(answer, "id", packageName)
        findViewById<ImageView>(resourceId)?.setBackgroundResource(R.drawable.selected_background)

        if (clickedImageIds.size == 3) {
            // check user selection, give score
            val selectedAudioResources = clickedImageIds.map { getAudioResourceForAnswer(it) }
            val correctCount = selectedAudioResources.count { it in randomAudioResources }

//            showToast("$correctCount/3 correct!")
            if ( sideIndex==0  || sideIndex==1 || sideIndex==2 || sideIndex==3) {
                leftScore += correctCount
//                showToast("Left: $leftScore/9")
                val tempScore = (leftScore*100/9).toInt()
                leftScoreTV.text = "$leftScore/9 correct"
            } else {
                rightScore += correctCount
//                showToast("Right: $rightScore/9")
                val tempScore = (rightScore*100/9).toInt()
                rightScoreTV.text = "$rightScore/9 correct"
            }
            clickedImageIds.clear()
            // Re-instate layoutcover
            overlayCover.bringToFront()
            overlayCover.visibility = View.VISIBLE
            overlayCover.isClickable = true
            overlayCover.isFocusable = true
            overlayCover.bringToFront()


            if (testIteration in 3..5){
                // left ear finish. right ear start
                audioAndNoise("right")
            } else if (testIteration<3) {
                // still left ear.
                audioAndNoise("left")
                testIteration++
            } else {
                // end of test
                showToast("end of test")
            }
        }
    }
// queen > cat > king > rat > frog > door > jar > dog > car

    private fun getAudioResourceForAnswer(answer: String): Int {
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
            else -> 0 // Handle other cases if needed
        }
    }


    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }



    private fun showSequentialSnackbar(messages: List<String>) {
        messages.forEachIndexed { index, message ->
            Handler().postDelayed({
                val snackbar = Snackbar.make(
                    findViewById(android.R.id.content),
                    message,
                    if (index == messages.size - 1) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
                )
                snackbar.show()
            }, index * 3000L) // Delay between messages (adjust as needed)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release the MediaPlayer resources when the activity is destroyed
        mediaPlayer.release()
        noisePlayer?.stop()
        noisePlayer.release()
    }

    override fun onOkButtonClick() {
        finish()
    }
    private fun showGoodJobFragment(leftScore: Int, rightScore: Int) {
        val goodJobFragment = GoodJobFragment()
        val args = Bundle().apply {
            putInt("leftScore", leftScore)
            putInt("rightScore", rightScore)
        }

        goodJobFragment.arguments = args
        goodJobFragment.onOkButtonClickListener = this
        goodJobFragment.show(supportFragmentManager, "GoodJobFragmentTag")
    }
}

class GoodJobFragment : DialogFragment() {
    interface OnOkButtonClickListener {
        fun onOkButtonClick()
    }

    var onOkButtonClickListener: OnOkButtonClickListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_test_end, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val leftScore = arguments?.getInt("leftScore", 0 ) ?:0
        val rightScore = arguments?.getInt("rightScore", 0)?:0
        val leftEarTV:TextView = view.findViewById(R.id.leftEar)
        val rightEarTV:TextView = view.findViewById(R.id.rightEar)

        leftEarTV.text = "Left Ear:\n$leftScore%"
        rightEarTV.text = "Right Ear:\n$rightScore%"

        // Access the Button through the 'view' parameter
        val okButton: Button = view.findViewById(R.id.okButton)
        okButton.setOnClickListener {
            onOkButtonClickListener?.onOkButtonClick()
            dismiss()
        }
    }
}

