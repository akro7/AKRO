package com.eko.media.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.eko.media.R
import com.eko.media.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var b: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Animate logo
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        b.ivLogo.startAnimation(pulse)

        // Animate title chars
        b.tvTitle.alpha = 0f
        b.tvTitle.animate().alpha(1f).setDuration(800).setStartDelay(300).start()

        b.tvSubtitle.alpha = 0f
        b.tvSubtitle.animate().alpha(1f).setDuration(600).setStartDelay(700).start()

        b.tvVersion.alpha = 0f
        b.tvVersion.animate().alpha(0.6f).setDuration(400).setStartDelay(1000).start()

        // Navigate after 2s
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 2000)
    }
}
