package com.rosberry.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rosberry.camera.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream

private const val REQUEST_CODE_CAMERA = 407

@SuppressLint("ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var cameraController: CameraController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraController = CameraController(this)
        cameraController.setPreviewView(binding.preview)

        binding.btnTorch.setOnClickListener { toggleTorch() }
        binding.btnTorch.text = FlashMode.OFF.name
        binding.btnShoot.setOnClickListener { takePicture() }
        binding.btnCamera.setOnClickListener { switchCamera() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQUEST_CODE_CAMERA)
        }
    }

    private fun switchCamera() {
        cameraController.switchCamera()
    }

    private fun startCamera() {
        cameraController.start(this)
    }

    private fun toggleTorch() {
        binding.btnTorch.text = cameraController.switchFlashMode().name
    }

    private fun takePicture() {
        cameraController.takePicture({ showPreview(it) })
    }

    private fun showPreview(file: File) {
        FileInputStream(file).use {
            val options = BitmapFactory.Options()
            options.inSampleSize = 4

            val bitmap = BitmapFactory.decodeStream(it, null, options)
            runOnUiThread { binding.imagePreview.setImageBitmap(bitmap) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_CAMERA
                && grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED
                && grantResults.getOrNull(1) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}