package com.eko.media.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.eko.media.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var mediaController: MediaController? = null

    companion object {
        private const val EXTRA_URL       = "player_url"
        private const val EXTRA_TITLE     = "player_title"
        private const val EXTRA_THUMBNAIL = "player_thumb"

        fun start(context: Context, url: String, title: String, thumbnail: String) {
            context.startActivity(Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL,       url)
                putExtra(EXTRA_TITLE,     title)
                putExtra(EXTRA_THUMBNAIL, thumbnail)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        val url   = intent.getStringExtra(EXTRA_URL)   ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
        val thumb = intent.getStringExtra(EXTRA_THUMBNAIL) ?: ""

        b.tvPlayerTitle.text = title

        // Show thumbnail while loading
        if (thumb.isNotBlank()) {
            Glide.with(this).load(thumb).into(b.ivPlayerThumb)
        }

        // Setup MediaController
        mediaController = MediaController(this)
        mediaController!!.setAnchorView(b.videoView)
        b.videoView.setMediaController(mediaController)

        // Load video
        b.videoView.setVideoURI(Uri.parse(url))

        b.videoView.setOnPreparedListener { mp ->
            b.progressBar.visibility = View.GONE
            b.ivPlayerThumb.visibility = View.GONE
            mp.start()
            // Smooth scaling
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
        }

        b.videoView.setOnErrorListener { _, what, extra ->
            b.progressBar.visibility = View.GONE
            b.tvError.visibility     = View.VISIBLE
            b.tvError.text           = "Playback error ($what,$extra)\nURL may require download first."
            true
        }

        b.videoView.setOnCompletionListener {
            b.btnReplay.visibility = View.VISIBLE
        }

        b.btnReplay.setOnClickListener {
            b.btnReplay.visibility = View.GONE
            b.videoView.start()
        }

        // Back button
        b.btnBack.setOnClickListener { finish() }

        b.videoView.start()
    }

    override fun onPause() {
        super.onPause()
        if (b.videoView.isPlaying) b.videoView.pause()
    }

    override fun onDestroy() {
        b.videoView.stopPlayback()
        super.onDestroy()
    }
}
