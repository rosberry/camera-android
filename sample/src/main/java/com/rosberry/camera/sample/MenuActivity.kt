package com.rosberry.camera.sample

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rosberry.camera.sample.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.run {
            btnController.setOnClickListener { startActivity(ControllerActivity::class.java) }
            btn4to3.setOnClickListener { startActivity(View4to3Activity::class.java) }
            btn16to9.setOnClickListener { startActivity(View16to9Activity::class.java) }
            btnFullscreen.setOnClickListener { startActivity(ViewFullscreenActivity::class.java) }
        }
    }

    private fun <T : AppCompatActivity> startActivity(activityClass: Class<T>) {
        startActivity(Intent(this, activityClass))
    }
}