package com.rosberry.camera.view

import android.animation.LayoutTransition
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.camera.core.AspectRatio
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
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
    private val focusRadius: Float by lazy { resources.getDimensionPixelSize(R.dimen.cameraview_focus_size) / 2f }

    private var aspectRatio: Int = -1

    init {
        LayoutInflater.from(context).inflate(R.layout.view_cameraview_camera, this)
        background = ColorDrawable(Color.BLACK)
        context.withStyledAttributes(attrs, R.styleable.CameraView, defStyle, 0) {
            aspectRatio = getInt(R.styleable.CameraView_previewRatio, -1)
        }
        this.layoutTransition = LayoutTransition()
        btnFlash.setOnClickListener { controller.cycleFlashMode() }
        btnSwitch.setOnClickListener { controller.switchCamera() }
        focus.setOnClickListener { controller.resetAutoFocus() }
        slider.addOnChangeListener { _, value, fromUser -> if (fromUser) controller.setLinearZoom(value) }
        setupPreview(aspectRatio)
        controller.run {
            isTapToFocusEnabled = true
            setPreviewView(preview)
            setAvailableFlashModes(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON)
            setCallback(this@CameraView)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as? Bundle)?.run {
            controller.setFrontCameraPreferred(getBoolean(STATE_FRONT_CAMERA))
            super.onRestoreInstanceState(getParcelable(STATE_SUPER))
        } ?: super.onRestoreInstanceState(state)
    }

    override fun onSaveInstanceState(): Parcelable {
        return bundleOf(
            STATE_SUPER to super.onSaveInstanceState(),
            STATE_FRONT_CAMERA to controller.isFrontCameraPreferred
        )
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
        focus.x = x - focusRadius
        focus.y = y - focusRadius
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
        controller.start(
            lifecycleOwner,
            false,
            if (aspectRatio == 0) AspectRatio.RATIO_4_3 else AspectRatio.RATIO_16_9
        )
    }

    fun stop() {
        controller.stop()
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
        takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), callback)
    }

    fun takePicture(outputStream: OutputStream, callback: ImageCapture.OnImageSavedCallback) {
        takePicture(ImageCapture.OutputFileOptions.Builder(outputStream).build(), callback)
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

    private fun setupPreview(aspectRatio: Int) {
        var ratio = when (aspectRatio) {
            AspectRatio.RATIO_16_9 -> 9 / 16f
            AspectRatio.RATIO_4_3 -> 3 / 4f
            else -> return
        }

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val screenRatio = if (isLandscape) getScreenRatio() else 1f / getScreenRatio()

        if (isLandscape) ratio = 1f / ratio

        if (aspectRatio == AspectRatio.RATIO_4_3) {
            ConstraintSet().run {
                clone(this@CameraView)
                if (isLandscape) {
                    connect(R.id.cameraview_preview, ConstraintSet.END, R.id.cameraview_guide, ConstraintSet.START)
                    setHorizontalBias(R.id.cameraview_preview, 0.5f)
                } else {
                    connect(R.id.cameraview_preview, ConstraintSet.BOTTOM, R.id.cameraview_guide, ConstraintSet.TOP)
                    setVerticalBias(R.id.cameraview_preview, 0.5f)
                }
                applyTo(this@CameraView)
            }
        } else if (screenRatio < 2.12f) {
            ConstraintSet().run {
                clone(this@CameraView)
                if (isLandscape) setHorizontalBias(R.id.cameraview_preview, 0.5f)
                else setVerticalBias(R.id.cameraview_preview, 0.5f)
                applyTo(this@CameraView)
            }
        }

        (preview.layoutParams as LayoutParams).dimensionRatio = ratio.toString()
    }

    private fun getScreenRatio(): Float {
        return resources.configuration.run { screenWidthDp / screenHeightDp.toFloat() }
    }
}