package com.example.myhearing

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class TestHearing : AppCompatActivity() {
    private lateinit var StartButton : Button
    private lateinit var DevNoteButton : Button

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var noisePlayer: MediaPlayer
    private lateinit var randomAudioResources: List<Int>

    private val clickedImageIds = mutableListOf<String>()
    private var testIteration = 0

    val allAudioResources = listOf(
        R.raw.dog, R.raw.cat, R.raw.car, R.raw.king, R.raw.queen,
        R.raw.jar, R.raw.frog, R.raw.door, R.raw.rat
    )
    val noiseLevelList = listOf(0.05f, 0.5f, 0.7f, 0.05f, 0.5f, 0.7f)
    private var noiseIndex = 0
    val sideCounter = listOf("left", "left", "left", "right", "right", "right")
    private var sideIndex = 0

    private var leftScore = 0
    private var rightScore = 0

    private lateinit var overlayCover : FrameLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_hearing)
        StartButton = findViewById(R.id.startButton)
        overlayCover = findViewById(R.id.overlay)
        overlayCover.bringToFront()
        overlayCover.visibility = View.VISIBLE
        overlayCover.isClickable = true
        overlayCover.isFocusable = true
        overlayCover.bringToFront()

        randomAudioResources = listOf() // init here, otherwise will crash


        StartButton.setOnClickListener {
            audioAndNoise("left")
        }
        mediaPlayer = MediaPlayer.create(this, R.raw.cat)
        noisePlayer = MediaPlayer.create(this,R.raw.noise5_1)


    }



    private fun audioAndNoise(side: String) {
        if (noiseIndex>=6) {
            showToast(" test ends ")
            return
        }
        // Re-instate layoutcover
        overlayCover.bringToFront()
        overlayCover.visibility = View.VISIBLE
        overlayCover.isClickable = true
        overlayCover.isFocusable = true
        overlayCover.bringToFront()

//        showToast("Noise Level: ${noiseLevelList[noiseIndex]}")
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
//        if (noisePlayer.isPlaying) {
//            noisePlayer.stop()
//            noisePlayer.release()
//        }
//
//        val noisePlayer = MediaPlayer.create(this, R.raw.noise5)
        showToast(" noise side: $side")
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
                showToast("Left: $leftScore/9")
            } else {
                rightScore += correctCount
                showToast("Right: $rightScore/9")
            }
            clickedImageIds.clear()
            // Re-instate layoutcover
            overlayCover.bringToFront()
            overlayCover.visibility = View.VISIBLE
            overlayCover.isClickable = true
            overlayCover.isFocusable = true
            overlayCover.bringToFront()


            println(">>>> about to test iteration")
            if (testIteration in 3..5){
                // left ear finish. right ear start
                println(">>> about to run RIGHT")
                audioAndNoise("right")
            } else if (testIteration<3) {
                // still left ear.
                audioAndNoise("left")
                testIteration++
                println(">>>> left side finished once")
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

}
