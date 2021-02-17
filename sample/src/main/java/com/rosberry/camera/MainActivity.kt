package com.rosberry.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rosberry.camera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val cameraController by lazy { CameraController(this) }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycle.addObserver(cameraController)
        cameraController.setTextureView(binding.textureView)
    }
}