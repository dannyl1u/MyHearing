package com.example.myhearing

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TestHearing : AppCompatActivity() {
    private lateinit var StartButton : Button
    private lateinit var mediaPlayer: MediaPlayer
    private val clickedImageIds = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_hearing)
        StartButton = findViewById(R.id.startButton)

        // Initialize MediaPlayer
        mediaPlayer = MediaPlayer()

        StartButton.setOnClickListener {
            // Play the audio files in sequence
            playAudio(R.raw.queen)
        }
    }
    private fun playAudio(audioResource: Int) {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.reset()
        }

        mediaPlayer = MediaPlayer.create(this, audioResource)

        mediaPlayer.setOnCompletionListener {
            // This listener is called when the audio playback is complete
            // You can add logic here to play the next audio file in sequence
            when (audioResource) {
                R.raw.queen -> playAudio(R.raw.cat)
                R.raw.cat -> playAudio(R.raw.king)
                R.raw.king -> {
                    // This is the last audio file in the sequence
                    // Add any additional logic or actions here
                    showToast("All audio files played")
                }
            }
        }

        mediaPlayer.start()
    }
    fun onImageClick(view: View) {
        // Check which image was clicked based on its ID
        when (view.id) {
            R.id.dog -> handleImageClick("dog")
            R.id.cat -> handleImageClick("cat")
            R.id.car -> handleImageClick("car")
            R.id.king -> handleImageClick("king")
            R.id.queen -> handleImageClick("queen")
            R.id.jar -> handleImageClick("jar")
            R.id.frog -> handleImageClick("frog")
            R.id.door -> handleImageClick("door")
            R.id.rat -> handleImageClick("rat")
        }
    }

    private fun handleImageClick(answer: String) {
//        showToast("Selected: $answer")
        clickedImageIds.add(answer)
        // Change the background of the clicked ImageView
        val resourceId = resources.getIdentifier(answer, "id", packageName)
        findViewById<ImageView>(resourceId)?.setBackgroundResource(R.drawable.selected_background)


        if (clickedImageIds.size == 3) {
            // check answer
            if (clickedImageIds[0] == "queen" && clickedImageIds[1]=="cat" && clickedImageIds[2]=="king") {
                showToast("3/3 correct!")
            } else {
                showToast("incorrect answer.")
            }
        }
    }
// queen > cat > king > rat > frog > door > jar > dog > car
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
