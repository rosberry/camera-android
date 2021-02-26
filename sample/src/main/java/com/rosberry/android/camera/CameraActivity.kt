package com.rosberry.android.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rosberry.android.camera.databinding.ActivityCameraBinding

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cameraView.attachLifecycleOwner(this)
    }
}