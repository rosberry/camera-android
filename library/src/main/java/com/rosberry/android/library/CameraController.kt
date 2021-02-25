package com.rosberry.android.library

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

@SuppressLint("ClickableViewAccessibility")
class CameraController(private val context: Context) {

    /**
     * Returns current camera flash mode.
     */
    var flashMode = FlashMode.OFF
        private set

    /**
     * Controls whether tap-to-focus is enabled.
     */
    var isTapToFocusEnabled: Boolean = false
        set(value) {
            if (!value) setAFPoint()
            field = value
        }

    /**
     * Controls whether pinch-to-zoom gesture is enabled.
     */
    var isPinchZoomEnabled: Boolean = false

    private val isFlashLightAvailable get() = camera?.cameraInfo?.hasFlashUnit() == true

    private val captureExecutor by lazy { Executors.newSingleThreadExecutor() }
    private val cameraTouchListener by lazy {
        View.OnTouchListener { _, event ->
            if (isPinchZoomEnabled) cameraGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                if (!isScaling && isTapToFocusEnabled) setAFPoint(event.x, event.y)
                isScaling = false
            }
            true
        }
    }
    private val cameraGestureDetector by lazy {
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                val zoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val scale = detector?.scaleFactor ?: 1f

                camera?.cameraControl?.setZoomRatio(zoom * scale)
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
                isScaling = true
                return true
            }
        })
    }

    private var camera: Camera? = null
    private var callback: WeakReference<CameraControllerCallback>? = null
    private var imageCapture: ImageCapture? = null
    private var lifecycleOwner: WeakReference<LifecycleOwner>? = null
    private var preview: Preview? = null
    private var previewView: WeakReference<PreviewView>? = null
    private var provider: ProcessCameraProvider? = null
    private var isFrontCamera = true
    private var isScaling = false

    /**
     * Sets the [PreviewView] to provide a Surface for Preview.
     */
    fun setPreviewView(previewView: PreviewView) {
        this.previewView = WeakReference(previewView).apply {
            get()?.setOnTouchListener(cameraTouchListener)
        }
    }

    /**
     * Sets the [CameraControllerCallback] to monitor certain camera events.
     */
    fun setCallback(callback: CameraControllerCallback) {
        this.callback = WeakReference(callback)
    }

    /**
     * Creates preview and capture use cases and binds them to lifecycle owner.
     * @param lifecycleOwner [LifecycleOwner] to bind camera use cases to
     * @param isFrontCamera controls whether initial camera will be front camera, default value is `true`
     */
    fun start(lifecycleOwner: LifecycleOwner, isFrontCamera: Boolean = this.isFrontCamera) {
        this.lifecycleOwner = WeakReference(lifecycleOwner)
        this.isFrontCamera = isFrontCamera
        ProcessCameraProvider
            .getInstance(context)
            .run {
                addListener({
                    provider = get()
                    preview = Preview.Builder()
                        .build()
                        .apply { setSurfaceProvider(previewView?.get()?.surfaceProvider) }
                    imageCapture = ImageCapture.Builder()
                        .setFlashMode(getInternalFlashMode(flashMode))
                        .build()
                    bindCamera()
                }, ContextCompat.getMainExecutor(context))
            }
    }

    /**
     * Switches between default front and back camera.
     * @return true if current active camera is front camera
     */
    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        bindCamera()
    }

    /**
     * Sets current camera linear zoom level.
     * @param zoom linear zoom value from 0 to 1, it will be coerced to the nearest value if out of bounds
     * @see androidx.camera.core.CameraControl.setLinearZoom
     */
    fun setLinearZoom(zoom: Float) {
        camera?.cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
    }

    /**
     * Sets current camera zoom ratio.
     * @param zoom ratio value, it wiil be coerced to the minimum/maximum available value if out of bounds
     */
    fun setZoomRatio(zoomRatio: Float) {
        camera?.run {
            val info = cameraInfo.zoomState.value
            cameraControl.setZoomRatio(zoomRatio.coerceIn(info?.minZoomRatio, info?.maxZoomRatio))
        }
    }

    /**
     * Resets auto-focus position to the middle of preview view.
     */
    fun resetAutoFocus() {
        setAFPoint()
    }

    /**
     * Sets current active camera flash mode to provided value. If the camera have no flashlight available, flash mode will be set to `FlashMode.OFF`.
     * Default value is `FlashMode.OFF`
     */
    fun setFlashMode(mode: FlashMode) {
        flashMode = when {
            !isFlashLightAvailable || mode == FlashMode.OFF -> FlashMode.OFF
            else -> mode
        }
        imageCapture?.flashMode = getInternalFlashMode(mode)
        camera?.cameraControl?.enableTorch(flashMode == FlashMode.TORCH)
        callback?.get()?.onFlashModeChanged(flashMode)
    }

    /**
     * Captures a new still image and saves to provided file.
     */
    fun takePicture(
            file: File,
            callback: ImageCapture.OnImageSavedCallback
    ) {
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        takePicture(options, callback)
    }

    /**
     * Captures a new still image and writes to provided output stream.
     */
    fun takePicture(
            outputStream: OutputStream,
            callback: ImageCapture.OnImageSavedCallback
    ) {
        val options = ImageCapture.OutputFileOptions.Builder(outputStream).build()
        takePicture(options, callback)
    }

    /**
     * Captures a new still image and saves to `MediaStore` with provided parameters.
     */
    fun takePicture(
            saveCollection: Uri,
            contentValues: ContentValues,
            callback: ImageCapture.OnImageSavedCallback
    ) {
        val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, saveCollection, contentValues).build()
        takePicture(options, callback)
    }

    /**
     * Captures a new still image and saves to a file along with application specified metadata.
     * @see ImageCapture.OutputFileOptions
     */
    fun takePicture(
            options: ImageCapture.OutputFileOptions,
            callback: ImageCapture.OnImageSavedCallback
    ) {
        imageCapture?.takePicture(options, captureExecutor, callback)
    }

    /**
     * Captures a new still image for in memory access.
     * @see ImageCapture.takePicture
     */
    fun takePicture(callback: ImageCapture.OnImageCapturedCallback) {
        imageCapture?.takePicture(captureExecutor, callback)
    }

    private fun bindCamera() {
        try {
            provider?.unbindAll()
            lifecycleOwner?.get()
                ?.let { lifecycleOwner ->
                    camera = provider?.bindToLifecycle(lifecycleOwner, getCameraSelector(), preview, imageCapture)
                    camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { onZoomStateChanged(it) }
                    setFlashMode(flashMode)
                }
                ?: throw IllegalStateException()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCameraSelector(): CameraSelector {
        return when (isFrontCamera) {
            true -> CameraSelector.DEFAULT_FRONT_CAMERA
            false -> CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun getInternalFlashMode(mode: FlashMode): Int {
        return when (mode) {
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    private fun setAFPoint(focusX: Float? = null, focusY: Float? = null) {
        previewView?.get()
            ?.run {
                val reset = focusX == null && focusY == null
                val x = focusX ?: width / 2f
                val y = focusY ?: height / 2f
                val point = meteringPointFactory.createPoint(x, y)
                val action = FocusMeteringAction.Builder(point)
                    .build()
                camera?.cameraControl?.startFocusAndMetering(action)

                callback?.get()
                    ?.apply {
                        when (reset) {
                            true -> onFocusReset()
                            false -> onFocusChanged(x, y)
                        }
                    }
            }
    }

    private fun onZoomStateChanged(zoomState: ZoomState) {
        callback?.get()
            ?.apply {
                onLinearZoomChanged(zoomState.linearZoom)
                onZoomRatioChanged(zoomState.zoomRatio)
            }
    }
}