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
    val noiseLevelList = listOf(0.2f, 0.8f, 1.5f, 0.2f, 0.8f, 1.5f)
    private var noiseIndex = 0

    private lateinit var overlay: View
    private lateinit var enableButton: Button
    private lateinit var gridView: GridView

    private lateinit var dogIV : ImageView
    private lateinit var catIV : ImageView
    private lateinit var carIV : ImageView
    private lateinit var kingIV : ImageView
    private lateinit var queenIV : ImageView
    private lateinit var jarIV : ImageView
    private lateinit var frogIV : ImageView
    private lateinit var doorIV : ImageView
    private lateinit var ratIV : ImageView

    private lateinit var overlayCover : FrameLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_hearing)
        StartButton = findViewById(R.id.startButton)
        overlayCover = findViewById(R.id.overlay)


        randomAudioResources = listOf() // init here, otherwise will crash

        StartButton.setOnClickListener {
            audioAndNoise("left")
        }
        mediaPlayer = MediaPlayer.create(this, R.raw.cat)


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
        showToast("Noise Level: ${noiseLevelList[noiseIndex]}")
        randomAudioResources = getRandomAudioResources(allAudioResources, 3)
        playAudioSequence(*randomAudioResources.toIntArray())
        playNoise(noiseLevelList[noiseIndex], side)
        noiseIndex++

    }
    fun getRandomAudioResources(audioList: List<Int>, count: Int): List<Int> {
        require(count <= audioList.size) { "Count should be less than or equal to the size of the audio list." }

        val shuffledList = audioList.shuffled()
        return shuffledList.subList(0, count)
    }
    private fun playAudioSequence(vararg audioResources: Int) {
        var mediaPlayer: MediaPlayer? = null

        fun playNextAudio(index: Int) {
            if (index < audioResources.size) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(resources.openRawResourceFd(audioResources[index]))
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
        val noisePlayer = MediaPlayer.create(this, R.raw.noise5)
        if (side == "left") {
            // left ear
            noisePlayer.setVolume(fl, 0.0f)
        } else {
            // right ear
            noisePlayer.setVolume(0.0f, fl)
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
            // Check if the selected audio resources match the user clicks
            val selectedAudioResources = clickedImageIds.map { getAudioResourceForAnswer(it) }
            if (selectedAudioResources == randomAudioResources) {
//                showToast("3/3 correct!")
            } else {
                showToast("Incorrect answer.")
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
        noisePlayer.release()
    }

}
