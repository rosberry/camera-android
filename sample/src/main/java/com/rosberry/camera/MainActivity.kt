package com.rosberry.camera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.rosberry.camera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var camera: Camera? = null

    private var torchEnabled = false

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnTorch.setOnClickListener { toggleTorch() }
        binding.slider.addOnChangeListener { _, value, _ -> setZoom(value) }
        startCamera()
    }

    private fun startCamera() {
        ProcessCameraProvider
            .getInstance(this)
            .run {
                addListener({
                    val selector = CameraSelector.DEFAULT_BACK_CAMERA
                    val provider = get()
                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(binding.preview.surfaceProvider) }

                    try {
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(this@MainActivity, selector, preview)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(this@MainActivity))
            }
    }

    private fun setZoom(zoom: Float) {
        camera?.cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
    }

    private fun toggleTorch() {
        torchEnabled = !torchEnabled
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            camera?.cameraControl?.enableTorch(torchEnabled)
        }
    }

}