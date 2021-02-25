package com.rosberry.camera

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

@SuppressLint("ClickableViewAccessibility")
class CameraController(private val context: Context) {

    /**
     * Controls whether tap-to-focus is enabled.
     */
    var isTapToFocusEnabled: Boolean = true
        set(value) {
            if (!value) setAFPoint()
            field = value
        }

    /**
     * Returns current camera flash mode.
     */
    var flashMode = FlashMode.OFF
        private set

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

    fun start(lifecycleOwner: LifecycleOwner, isFrontCamera: Boolean = true) {
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
                        .setFlashMode(getFlashMode())
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
     * Sets current camera zoom level.
     * @param zoom linear zoom value from 0 to 1, it will be coerced to the nearest value if out of bounds
     * @see androidx.camera.core.CameraControl.setLinearZoom
     */
    fun setZoom(zoom: Float) {
        camera?.cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
    }

    /**
     * Resets auto-focus position to the middle of preview view.
     */
    fun resetAutoFocus() {
        setAFPoint()
    }

    /**
     * Cycles over active camera flash modes.
     * @return last updated [Flash Mode] if flashlight is available for current camera or [FlashMode.OFF]
     */
    fun switchFlashMode(): FlashMode {
        if (!isFlashLightAvailable) return FlashMode.OFF

        flashMode = FlashMode.values()
            .run {
                val index = indexOfFirst { it == flashMode } + 1
                return@run if (index > this.size - 1) this[0] else this[index]
            }
        camera?.cameraControl?.enableTorch(flashMode == FlashMode.TORCH)
        imageCapture?.flashMode = getFlashMode()

        return flashMode
    }

    /**
     * Captures a new still image and saves to a file.
     * @see [ImageCapture.takePicture]
     */
    fun takePicture(
            onPictureTaken: (File) -> Unit,
            onError: ((ImageCaptureException) -> Unit)? = null
    ) {
        val file = File.createTempFile("${System.currentTimeMillis()}", ".jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file)
            .build()

        imageCapture?.takePicture(options, captureExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onPictureTaken(file)
            }

            override fun onError(exception: ImageCaptureException) {
                onError?.invoke(exception)
            }
        })
    }

    private fun bindCamera() {
        try {
            provider?.unbindAll()
            lifecycleOwner?.get()
                ?.let { lifecycleOwner ->
                    camera = provider?.bindToLifecycle(lifecycleOwner, getCameraSelector(), preview, imageCapture)
                    if (!isFlashLightAvailable) flashMode = FlashMode.OFF
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

    private fun getFlashMode(): Int {
        return when (flashMode) {
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
}