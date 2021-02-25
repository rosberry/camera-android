package com.rosberry.android.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.rosberry.android.camera.databinding.ActivityMainBinding
import com.rosberry.android.library.CameraController
import com.rosberry.android.library.CameraControllerCallback
import com.rosberry.android.library.FlashMode
import java.io.File
import java.io.FileInputStream
import java.math.RoundingMode
import java.text.DecimalFormat

private const val REQUEST_CODE_CAMERA = 407

@SuppressLint("ClickableViewAccessibility")
class MainActivity : AppCompatActivity(), CameraControllerCallback {

    private lateinit var binding: ActivityMainBinding

    private val cameraController: CameraController by lazy { CameraController(this) }

    private val format = DecimalFormat("#.#").apply { roundingMode = RoundingMode.HALF_UP }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnTorch.setOnClickListener { toggleTorch() }
        binding.btnShoot.setOnClickListener { takePicture() }
        binding.btnCamera.setOnClickListener { switchCamera() }
        binding.btnFocus.setOnClickListener { resetFocus() }
        binding.btnZoom.setOnClickListener { togglePinchZoom() }
        binding.btnZoom.alpha = if (cameraController.isPinchZoomEnabled) 1f else 0.3f
        binding.slider.addOnChangeListener { _, value, fromUser -> if (fromUser) cameraController.setLinearZoom(value) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA)
        }
    }

    private fun switchCamera() {
        cameraController.switchCamera()
        binding.btnTorch.setImageDrawable(getFlashDrawable(cameraController.flashMode))
    }

    private fun startCamera() {
        cameraController.setCallback(this)
        cameraController.setPreviewView(binding.preview)
        cameraController.start(this, false)
    }

    private fun toggleTorch() {
        val drawable = getFlashDrawable(cameraController.switchFlashMode())
        binding.btnTorch.setImageDrawable(drawable)
    }

    private fun resetFocus() {
        cameraController.resetAutoFocus()
    }

    private fun takePicture() {
        cameraController.takePicture({ showPreview(it) })
    }

    private fun togglePinchZoom() {
        cameraController.isPinchZoomEnabled = !cameraController.isPinchZoomEnabled
        binding.btnZoom.alpha = if (cameraController.isPinchZoomEnabled) 1f else 0.3f
    }

    private fun showPreview(file: File) {
        FileInputStream(file).use {
            val options = BitmapFactory.Options()
            options.inSampleSize = 4

            val bitmap = BitmapFactory.decodeStream(it, null, options)
            runOnUiThread { binding.imagePreview.setImageBitmap(bitmap) }
        }
    }

    override fun onFocusChanged(x: Float, y: Float) {
        binding.btnFocus.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_focus, theme))
    }

    override fun onFocusReset() {
        binding.btnFocus.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_focus_auto, theme))
    }

    override fun onZoomRatioChanged(zoom: Float) {
        binding.textZoom.text = "${format.format(zoom)}x"
    }

    override fun onLinearZoomChanged(zoom: Float) {
        binding.slider.value = zoom
    }

    private fun getFlashDrawable(mode: FlashMode): Drawable? {
        val drawableId = when (mode) {
            FlashMode.OFF -> R.drawable.ic_flash_off
            FlashMode.AUTO -> R.drawable.ic_flash_auto
            FlashMode.ON -> R.drawable.ic_flash_on
            FlashMode.TORCH -> R.drawable.ic_torch
        }
        return ResourcesCompat.getDrawable(resources, drawableId, theme)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_CAMERA && grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}