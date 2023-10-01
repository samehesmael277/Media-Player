package com.sameh.mediaplayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sameh.mediaplayer.databinding.ActivityMainBinding
import jp.wasabeef.recyclerview.animators.SlideInUpAnimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val audioAdapter: AudioAdapter by lazy {
        AudioAdapter(this)
    }

    private var mediaPlayer: MediaPlayer? = null

    private var isPlaying = false
    private var canClickPlayPauseButton = false

    private var currentAudioList = ArrayList<Audio>()
    private var currentAudioPosition = -1

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updatePlayPauseButtonLabel()
        setupAudioAdapter()
        if (!hasPermissions())
            requestPermissions()
        else
            fetchMusic()
        setActions()
    }

    private fun setActions() {
        audioAdapter.onAudioClickListener { audio, position ->
            startNewAudio(audio.filePath)
            currentAudioPosition = position
        }
        binding.btnStopPause.setOnClickListener {
            playPause()
        }
        binding.btnNext.setOnClickListener {
            moveToNextAudio()
        }
        binding.btnLast.setOnClickListener {
            moveToLastAudio()
        }
        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Seek to the selected position when the user interacts with the SeekBar
                    try {
                        mediaPlayer?.seekTo(progress)
                        updateSeekBarProgress()
                    } catch (e: Exception) {
                        e.message?.toLog()
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Handle seek bar touch start
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Handle seek bar touch end
            }
        })
    }

    private fun startNewAudio(filePath: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                canClickPlayPauseButton = true
                // Release the existing MediaPlayer if it's playing
                mediaPlayer?.apply {
                    stop()
                    reset()
                    release()
                }
                mediaPlayer = MediaPlayer() // Create a new MediaPlayer instance
                mediaPlayer?.apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    this@MainActivity.isPlaying = true
                    updatePlayPauseButtonLabel()
                    binding.seekbar.max = duration
                    // update the SeekBar progress in the main thread
                    withContext(Dispatchers.Main) {
                        binding.tvAudioDurationEnd.text = formatDuration(duration)
                        updateSeekBarProgress()
                    }
                }
                mediaPlayer?.setOnCompletionListener {
                    moveToNextAudio()
                }
            } catch (e: Exception) {
                e.message?.toLog()
            }
        }
    }

    private fun playPause() {
        if (canClickPlayPauseButton) {
            if (isPlaying)
                mediaPlayer?.pause()
            else
                mediaPlayer?.start()
            isPlaying = !isPlaying
            updateSeekBarProgress()
            updatePlayPauseButtonLabel()
        } else {
            "You should select audio first".showToast()
        }
    }

    private fun moveToNextAudio() {
        if (currentAudioPosition >= 0 && currentAudioPosition < currentAudioList.size - 1) {
            val nextAudio = currentAudioList[currentAudioPosition + 1]
            startNewAudio(nextAudio.filePath)
            currentAudioPosition++
            audioAdapter.selectedAudioPosition++
            audioAdapter.notifyItemChanged(currentAudioPosition - 1)
            audioAdapter.notifyItemChanged(currentAudioPosition)
        }
    }

    private fun moveToLastAudio() {
        if (currentAudioPosition > 0) {
            val lastAudio = currentAudioList[currentAudioPosition - 1]
            startNewAudio(lastAudio.filePath)
            currentAudioPosition--
            audioAdapter.selectedAudioPosition--
            audioAdapter.notifyItemChanged(currentAudioPosition + 1)
            audioAdapter.notifyItemChanged(currentAudioPosition)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSeekBarProgress() {
        lifecycleScope.launch {
            while (isPlaying) {
                withContext(Dispatchers.Main) {
                    try {
                        binding.seekbar.progress = mediaPlayer?.currentPosition ?: 0
                        if (mediaPlayer != null)
                            binding.tvAudioCurrentDuration.text =
                                formatDuration(mediaPlayer!!.currentPosition)
                        else
                            binding.tvAudioCurrentDuration.text = "00:00"
                    } catch (e: Exception) {
                        e.message?.toLog()
                    }
                }
                delay(1000) // Update every second
            }
        }
    }

    private fun updatePlayPauseButtonLabel() {
        val buttonText = if (isPlaying) "Pause" else "Play"
        binding.btnStopPause.text = buttonText
    }

    private fun formatDuration(durationInMillis: Int): String {
        val minutes = durationInMillis / 60000
        val seconds = (durationInMillis % 60000) / 1000
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }

    private fun setupAudioAdapter() {
        binding.apply {
            rvAudio.adapter = audioAdapter
            rvAudio.layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            rvAudio.itemAnimator = SlideInUpAnimator().apply {
                addDuration = 200
            }
        }
    }

    @SuppressLint("Range")
    private fun fetchMusic() {
        val musicList = ArrayList<Audio>()
        val contentResolver: ContentResolver = contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cursor: Cursor? = contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )

        if (cursor != null) {
            while (cursor.moveToNext()) {
                val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                val filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
                val durationMs =
                    cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))

                // Get album art
                val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")

                // Convert duration to minutes and seconds
                val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
                val durationSeconds =
                    TimeUnit.MILLISECONDS.toSeconds(durationMs) % TimeUnit.MINUTES.toSeconds(1)

                val audioInfo = Audio(
                    title,
                    artist,
                    filePath,
                    albumArtUri.toString(),
                    String.format("%02d:%02d", durationMinutes, durationSeconds)
                )

                musicList.add(audioInfo)
            }
            cursor.close()
            // Now, the 'musicList' ArrayList contains information about all the music files on your phone
            // You can use this list to display music information or perform any other actions
            audioAdapter.differ.submitList(musicList)
            currentAudioList = musicList
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val permissionsToRequest = arrayOf(
        Manifest.permission.READ_MEDIA_AUDIO
    )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasPermissions(): Boolean {
        for (permission in permissionsToRequest) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false // At least one permission is not granted
            }
        }
        return true // All permissions are granted
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestPermissions() {
        val permissionsNotGranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsNotGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNotGranted,
                PERMISSION_REQUEST_CODE
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allPermissionsGranted = true

            for (grantResult in grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }

            if (allPermissionsGranted)
                fetchMusic()
            else
                requestPermissions()
        }
    }

    private fun String.showToast() {
        Toast.makeText(this@MainActivity, this, Toast.LENGTH_SHORT).show()
    }

    private fun String.toLog(tag: String = "applicationTAG") {
        Log.d(tag, "data: $this")
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}