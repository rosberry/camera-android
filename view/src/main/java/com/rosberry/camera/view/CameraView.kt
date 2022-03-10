package com.rosberry.camera.view

import android.animation.LayoutTransition
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.withStyledAttributes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.slider.Slider
import com.rosberry.camera.controller.CameraController
import com.rosberry.camera.controller.CameraControllerCallback
import com.rosberry.camera.controller.FlashMode
import java.io.File
import java.io.OutputStream
import java.math.RoundingMode
import java.text.DecimalFormat

class CameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle), CameraControllerCallback {

    companion object {
        private const val STATE_SUPER = "cameraview_state_super"
        private const val STATE_FRONT_CAMERA = "cameraview_state_front_camera"
    }

    private val controller = CameraController(context)

    private val btnFlash by lazy { findViewById<ImageButton>(R.id.cameraview_btn_flash) }
    private val btnShutter by lazy { findViewById<ImageButton>(R.id.cameraview_btn_shutter) }
    private val btnSwitch by lazy { findViewById<ImageButton>(R.id.cameraview_btn_switch) }
    private val focus by lazy { findViewById<View>(R.id.cameraview_focus) }
    private val textZoom by lazy { findViewById<TextView>(R.id.cameraview_text_zoom) }
    private val preview by lazy { findViewById<PreviewView>(R.id.cameraview_preview) }
    private val slider by lazy { findViewById<Slider>(R.id.cameraview_slider) }
    private val textCallback by lazy { Runnable { textZoom.isVisible = false } }
    private val format: DecimalFormat by lazy { DecimalFormat("#.#").apply { roundingMode = RoundingMode.HALF_UP } }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_cameraview_camera, this)
        context.withStyledAttributes(attrs, R.styleable.CameraView, defStyle, 0) {
            (preview.layoutParams as LayoutParams).dimensionRatio = getString(R.styleable.CameraView_previewRatio)
        }
        this.layoutTransition = LayoutTransition()
        controller.run {
            isTapToFocusEnabled = true
            setPreviewView(preview)
            setAvailableFlashModes(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON)
            setCallback(this@CameraView)
        }
        btnFlash.setOnClickListener { controller.cycleFlashMode() }
        btnSwitch.setOnClickListener { controller.switchCamera() }
        focus.setOnClickListener { controller.resetAutoFocus() }
        slider.addOnChangeListener { _, value, fromUser -> if (fromUser) controller.setLinearZoom(value) }
    }

    override fun onSaveInstanceState(): Parcelable {
        return bundleOf(
            STATE_SUPER to super.onSaveInstanceState(),
            STATE_FRONT_CAMERA to controller.isFrontCameraPreferred
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? Bundle)?.run {
            controller.setFrontCameraPreferred(getBoolean(STATE_FRONT_CAMERA))
            super.onRestoreInstanceState(getParcelable(STATE_SUPER))
        } ?: super.onRestoreInstanceState(state)
    }

    override fun onFlashModeChanged(mode: FlashMode) {
        btnFlash.isVisible = mode != FlashMode.NONE

        when (mode) {
            FlashMode.OFF -> R.drawable.ic_cameraview_flash_off
            FlashMode.AUTO -> R.drawable.ic_cameraview_flash_auto
            FlashMode.ON -> R.drawable.ic_cameraview_flash_on
            else -> return
        }.run { btnFlash.setImageDrawable(ResourcesCompat.getDrawable(resources, this, context.theme)) }

    }

    override fun onCameraFocusChanged(x: Float, y: Float) {
        focus.x = x - focus.width / 2f
        focus.y = y - focus.height / 2f
        focus.isVisible = true
    }

    override fun onCameraFocusReset() {
        focus.isVisible = false
    }

    override fun onZoomRatioChanged(zoom: Float) {
        textZoom.text = context.getString(R.string.camera_zoom_template, format.format(zoom))
    }

    override fun onLinearZoomChanged(zoom: Float) {
        slider.value = zoom
        textZoom.run {
            isVisible = true
            removeCallbacks(textCallback)
            postDelayed(textCallback, 500L)
        }
    }

    override fun onCameraCountAvailable(count: Int) {
        btnSwitch.isVisible = count > 1
    }

    fun start(lifecycleOwner: LifecycleOwner) {
        controller.start(lifecycleOwner, false)
    }

    fun setTakePhotoListener(listener: (() -> Unit)?) {
        btnShutter.setOnClickListener { listener?.invoke() }
    }

    fun takePicture(callback: ImageCapture.OnImageCapturedCallback) {
        controller.takePicture(callback)
    }

    fun takePicture(options: ImageCapture.OutputFileOptions, callback: ImageCapture.OnImageSavedCallback) {
        controller.takePicture(options, callback)
    }

    fun takePicture(file: File, callback: ImageCapture.OnImageSavedCallback) {
        takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            callback
        )
    }

    fun takePicture(outputStream: OutputStream, callback: ImageCapture.OnImageSavedCallback) {
        takePicture(
            ImageCapture.OutputFileOptions.Builder(outputStream).build(),
            callback
        )
    }

    fun takePicture(
        saveCollection: Uri,
        contentValues: ContentValues,
        callback: ImageCapture.OnImageSavedCallback
    ) {
        takePicture(
            ImageCapture.OutputFileOptions.Builder(context.contentResolver, saveCollection, contentValues).build(),
            callback
        )
    }
}