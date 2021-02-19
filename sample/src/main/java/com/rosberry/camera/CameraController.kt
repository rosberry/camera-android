package com.rosberry.camera

import android.annotation.SuppressLint
import android.content.Context
import android.view.ScaleGestureDetector
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
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
     * Returns if flashlight available for active camera.
     */
    val isFlashLightAvailable get() = camera?.cameraInfo?.hasFlashUnit() == true

    private val captureExecutor by lazy { Executors.newSingleThreadExecutor() }
    private val cameraTouchListener by lazy {
        View.OnTouchListener { _, event -> cameraGestureDetector.onTouchEvent(event) }
    }
    private val cameraGestureDetector by lazy {
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                val zoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val scale = detector?.scaleFactor ?: 1f

                camera?.cameraControl?.setZoomRatio(zoom * scale)
                return true
            }
        })
    }

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lifecycleOwner: WeakReference<LifecycleOwner>? = null
    private var preview: Preview? = null
    private var previewView: WeakReference<PreviewView>? = null
    private var provider: ProcessCameraProvider? = null

    private var flashMode = FlashMode.OFF
    private var isFrontCamera = true

    /**
     * Sets the [PreviewView] to provide a Surface for Preview.
     */
    fun setPreviewView(previewView: PreviewView) {
        this.previewView = WeakReference(previewView).apply {
            get()?.setOnTouchListener(cameraTouchListener)
        }
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
        camera?.cameraControl?.enableTorch(flashMode == FlashMode.FORCE)
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
                ?.let { lifecycleOwner -> camera = provider?.bindToLifecycle(lifecycleOwner, getCameraSelector(), preview, imageCapture) }
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
}