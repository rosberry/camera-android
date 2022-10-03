package com.rosberry.camera.sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toFile
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.rosberry.camera.controller.CameraController
import com.rosberry.camera.controller.CameraControllerCallback
import com.rosberry.camera.controller.FlashMode
import com.rosberry.camera.sample.databinding.ActivityControllerBinding
import java.io.File
import java.io.FileInputStream
import java.math.RoundingMode
import java.text.DecimalFormat

@SuppressLint("ClickableViewAccessibility")
class ControllerActivity : AppCompatActivity(), CameraControllerCallback, ImageCapture.OnImageSavedCallback {

    companion object {

        private const val REQUEST_CODE_CAMERA = 407
    }

    private val cameraController: CameraController by lazy { CameraController(this) }

    private val textVisibilityCallback by lazy { Runnable { binding.textZoom.isInvisible = !isSliderVisible } }

    private val format = DecimalFormat("#.#").apply { roundingMode = RoundingMode.HALF_UP }

    private var isSliderVisible = false

    private lateinit var binding: ActivityControllerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityControllerBinding
            .inflate(layoutInflater)
            .apply {
                setContentView(root)

                btnTorch.setOnClickListener { toggleTorch() }
                btnShoot.setOnClickListener { takePicture() }
                btnCamera.setOnClickListener { switchCamera() }
                btnFocus.setOnClickListener { resetFocus() }
                btnPinch.setOnClickListener { togglePinchZoom() }
                btnPinch.alpha = if (cameraController.isPinchZoomEnabled) 1f else 0.3f
                btnZoom.setOnClickListener { toggleLinearZoom() }
                btnZoom.alpha = if (isSliderVisible) 1f else 0.3f
                slider.addOnChangeListener { _, value, fromUser -> if (fromUser) cameraController.setLinearZoom(value) }
            }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA)
        }
    }

    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
        outputFileResults.savedUri
            ?.toFile()
            ?.let(::showPreview)
    }

    override fun onError(exception: ImageCaptureException) {}

    private fun switchCamera() {
        cameraController.switchCamera()
    }

    private fun startCamera() {
        cameraController.run {
            setPreviewView(binding.preview)
            isTapToFocusEnabled = true
            setCallback(this@ControllerActivity)
            start(this@ControllerActivity, false)
        }
    }

    private fun toggleTorch() {
        cameraController.cycleFlashMode()
    }

    private fun resetFocus() {
        cameraController.resetAutoFocus()
    }

    private fun takePicture() {
        File.createTempFile("${System.currentTimeMillis()}", ".jpg")
            .let { file -> cameraController.takePicture(file, this) }
    }

    private fun togglePinchZoom() {
        cameraController.isPinchZoomEnabled = !cameraController.isPinchZoomEnabled
        binding.btnPinch.alpha = if (cameraController.isPinchZoomEnabled) 1f else 0.3f
    }

    private fun toggleLinearZoom() {
        isSliderVisible = !isSliderVisible
        binding.slider.isInvisible = !isSliderVisible
        binding.textZoom.isInvisible = !isSliderVisible
        binding.btnZoom.alpha = if (isSliderVisible) 1f else 0.3f
    }

    private fun showPreview(file: File) {
        FileInputStream(file)
            .use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
            .let { bitmap -> runOnUiThread { binding.imagePreview.setImageBitmap(bitmap) } }
    }

    private fun onZoomChanged() {
        if (isSliderVisible) return

        binding.textZoom.apply {
            isInvisible = false
            removeCallbacks(textVisibilityCallback)
            postDelayed(textVisibilityCallback, 500)
        }
    }

    override fun onFlashModeChanged(mode: FlashMode) {
        binding.btnTorch.isVisible = mode != FlashMode.NONE

        val drawableId = when (mode) {
            FlashMode.OFF -> R.drawable.ic_flash_off
            FlashMode.AUTO -> R.drawable.ic_flash_auto
            FlashMode.ON -> R.drawable.ic_flash_on
            FlashMode.TORCH -> R.drawable.ic_torch
            else -> return
        }
        binding.btnTorch.setImageDrawable(ResourcesCompat.getDrawable(resources, drawableId, theme))
    }

    override fun onCameraFocusChanged(x: Float, y: Float) {
        binding.btnFocus.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_focus, theme))
    }

    override fun onCameraFocusReset() {
        binding.btnFocus.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_focus_auto, theme))
    }

    override fun onZoomRatioChanged(zoom: Float) {
        binding.textZoom.text = "${format.format(zoom)}x"
        onZoomChanged()
    }

    override fun onLinearZoomChanged(zoom: Float) {
        binding.slider.value = zoom
    }

    override fun onCameraCountAvailable(count: Int) {
        binding.btnCamera.isVisible = count > 1
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_CAMERA && grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}