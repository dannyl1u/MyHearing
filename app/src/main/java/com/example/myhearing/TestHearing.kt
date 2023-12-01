package com.example.myhearing

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TestHearing : AppCompatActivity() {
    private lateinit var StartButton : Button
    private lateinit var DevNoteButton : Button

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var noisePlayer: MediaPlayer
    private lateinit var randomAudioResources: List<Int>

    private val clickedImageIds = mutableListOf<String>()

    val allAudioResources = listOf(
        R.raw.dog, R.raw.cat, R.raw.car, R.raw.king, R.raw.queen,
        R.raw.jar, R.raw.frog, R.raw.door, R.raw.rat
    )
    val noiseLevelList = listOf(0.2f, 0.6f, 1.0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_hearing)
        StartButton = findViewById(R.id.startButton)

        StartButton.setOnClickListener {
//            for (i in 1..3) {
            randomAudioResources = getRandomAudioResources(allAudioResources, 3)
            println(">>> random audio res: $randomAudioResources")
            playAudioSequence(*randomAudioResources.toIntArray())
            playNoise(noiseLevelList[0])

//            }
        }
        mediaPlayer = MediaPlayer.create(this, R.raw.cat)


    }
    private fun playNoise(fl: Float) {
        val noisePlayer = MediaPlayer.create(this, R.raw.noise)
        noisePlayer.setVolume(fl,0.0f)
        noisePlayer.start()

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


    fun onImageClick(view: View) {
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
                showToast("3/3 correct!")
            } else {
                showToast("Incorrect answer.")
            }
            clickedImageIds.clear()
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
