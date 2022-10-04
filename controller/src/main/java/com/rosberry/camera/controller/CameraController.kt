package com.rosberry.camera.controller

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import androidx.annotation.MainThread
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraUnavailableException
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@SuppressLint("ClickableViewAccessibility")
open class CameraController(
    private val context: Context
) : LifecycleEventObserver {

    /**
     * Returns current camera flash mode.
     */
    var flashMode = FlashMode.OFF
        private set

    /**
     * Returns is preferred camera is front camera.
     */
    var isFrontCameraPreferred = true
        private set

    /**
     * Controls whether tap-to-focus is enabled.
     */
    var isTapToFocusEnabled: Boolean = false
        set(value) {
            if (!value) resetAutoFocus()
            field = value
        }

    /**
     * Controls whether pinch-to-zoom gesture is enabled.
     */
    var isPinchZoomEnabled: Boolean = false

    /**
     * Sets the rotation of the intended target for images from this configuration.
     *
     * Valid values: [Surface.ROTATION_0], [Surface.ROTATION_90], [Surface.ROTATION_180], [Surface.ROTATION_270].
     * Rotation values are relative to the "natural" rotation, [Surface.ROTATION_0].
     *
     * Note that setting property manually might have no effect if `isOrientationListenerEnabled` was enabled
     * when invoking [start].
     *
     * @see ImageCapture.Builder.setTargetRotation
     */
    var rotation: Int = Surface.ROTATION_0
        set(value) {
            imageCapture?.targetRotation = value
            field = value
        }

    protected open val isFlashLightAvailable get() = camera?.cameraInfo?.hasFlashUnit() == true

    protected val captureExecutor: Executor by lazy { Executors.newSingleThreadExecutor() }
    protected val frontCameraSelector: CameraSelector by lazy { CameraSelector.DEFAULT_FRONT_CAMERA }
    protected val backCameraSelector: CameraSelector by lazy { CameraSelector.DEFAULT_BACK_CAMERA }
    protected val preview: Preview by lazy { Preview.Builder().build() }

    protected var imageCapture: ImageCapture? = null
        private set

    private var flashModes: Array<out FlashMode> = FlashMode.values()
    private var camera: Camera? = null
    private var callback: WeakReference<CameraControllerCallback>? = null
    private var lifecycleOwner: WeakReference<LifecycleOwner>? = null
    private var previewView: WeakReference<PreviewView>? = null
    private var provider: ProcessCameraProvider? = null
    private var hasFrontCamera: Boolean = false
    private var hasBackCamera: Boolean = false

    private val cameraTouchListener: View.OnTouchListener by lazy { TouchListener() }
    private val orientationListener: OrientationEventListener by lazy { OrientationListener(context) }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> orientationListener.enable()
            Lifecycle.Event.ON_PAUSE -> orientationListener.disable()
            else -> return
        }
    }

    /**
     * Sets the `PreviewView` to provide a Surface for Preview.
     *
     * @param previewView [PreviewView] used to display camera preview
     * @param isTouchEnabled controls if `previewView` will be supplied with internal touch handler.
     * Note that if set to `false` [isTapToFocusEnabled] and [isPinchZoomEnabled] values won't have effect
     */
    fun setPreviewView(
        previewView: PreviewView,
        isTouchEnabled: Boolean = true
    ) {
        this.previewView = WeakReference(previewView).apply {
            get()?.let { previewView ->
                if (isTouchEnabled) previewView.setOnTouchListener(cameraTouchListener)

                preview.setSurfaceProvider(previewView.surfaceProvider)
            }
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
     *
     * @param lifecycleOwner [LifecycleOwner] to bind camera use cases to
     * @param isFrontCamera controls whether preferred initial camera will be front camera, default value is `true`
     * @param isOrientationListenerEnabled controls if internal [OrientationEventListener] bound to `lifecycleOwner`'s
     * [Lifecycle] will update [rotation], default value is `true`
     * @param aspectRatio desired [AspectRatio] for [ImageCapture] use case
     *
     * @see ImageCapture.Builder.setTargetRotation
     * @see ImageCapture.Builder.setTargetAspectRatio
     */
    fun start(
        lifecycleOwner: LifecycleOwner,
        isFrontCamera: Boolean = this.isFrontCameraPreferred,
        isOrientationListenerEnabled: Boolean = true,
        @AspectRatio.Ratio aspectRatio: Int = AspectRatio.RATIO_4_3
    ) {
        if (provider != null) return

        this.isFrontCameraPreferred = isFrontCamera
        this.lifecycleOwner = WeakReference(lifecycleOwner).apply {
            if (isOrientationListenerEnabled) get()?.lifecycle?.addObserver(this@CameraController)
        }

        imageCapture = ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setTargetAspectRatio(aspectRatio)
            .setFlashMode(getInternalFlashMode(flashMode))
            .build()

        ProcessCameraProvider
            .getInstance(context)
            .run { addListener({ onRetrieveProvider(get()) }, ContextCompat.getMainExecutor(context)) }
    }

    /**
     * Unbinds all use cases from the lifecycle and removes them from CameraX
     * if it's necessary to release camera before [LifecycleOwner] passed to [start] method reaches
     * `Lifecycle.State.DESTROYED`
     *
     * @throws IllegalStateException If not called on main thread.
     * @see ProcessCameraProvider.unbindAll
     * @see start
     */
    @MainThread
    fun stop() {
        provider?.unbindAll()
        camera = null
        hasFrontCamera = false
        hasBackCamera = false
        lifecycleOwner?.get()?.lifecycle?.removeObserver(this)
        lifecycleOwner?.clear()
        lifecycleOwner = null
        provider = null
    }

    /**
     * Switches between default front and back camera.
     */
    @MainThread
    fun switchCamera() {
        isFrontCameraPreferred = !isFrontCameraPreferred
        provider?.let(::bindCamera)
    }

    /**
     * Controls whether preferred camera is front camera.
     * Invoking this method will also cause camera rebind if preferred camera changed.
     */
    @MainThread
    fun setFrontCameraPreferred(isFrontCameraPreferred: Boolean) {
        if (this.isFrontCameraPreferred != isFrontCameraPreferred) {
            this.isFrontCameraPreferred = isFrontCameraPreferred
            provider?.let(::bindCamera)
        }
    }

    /**
     * Sets current camera linear zoom level.
     *
     * @param zoom linear zoom value from 0 to 1, it will be coerced to the nearest value if out of bounds
     * @see androidx.camera.core.CameraControl.setLinearZoom
     */
    fun setLinearZoom(zoom: Float) {
        camera?.cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
    }

    /**
     * Sets current camera zoom ratio.
     *
     * @param zoomRatio zoom ratio value, it will be coerced to the minimum/maximum available value if out of bounds
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
        previewView?.get()?.setAFPoint()
    }

    /**
     * Sets current active camera flash mode to provided value.
     * If the camera have no flashlight available, flash mode will be set to `FlashMode.NONE`.
     * Default value is `FlashMode.OFF`.
     */
    fun setFlashMode(mode: FlashMode) {
        flashMode = when {
            !isFlashLightAvailable -> FlashMode.NONE
            mode == FlashMode.NONE -> FlashMode.OFF
            else -> mode
        }
        imageCapture?.flashMode = getInternalFlashMode(mode)
        camera?.cameraControl?.enableTorch(flashMode == FlashMode.TORCH)
        callback?.get()?.onFlashModeChanged(flashMode)
    }

    /**
     *  Sets [FlashMode]s available to cycle via `cycleFlashMode()` invocation.
     *
     *  Pass no arguments to set all available flash modes.
     *
     *  @throws IllegalStateException when provided with empty collection.
     *  @see cycleFlashMode
     */
    fun setAvailableFlashModes(vararg modes: FlashMode) {
        flashModes = if (modes.isEmpty()) FlashMode.values() else modes
    }

    /**
     * Cycles through available flash modes.
     *
     * If the camera have no flashlight available, flash mode will be set to `FlashMode.NONE`.
     * Default value is `FlashMode.OFF`.
     *
     * @see setAvailableFlashModes
     */
    fun cycleFlashMode() {
        flashModes.run {
            val currentIndex = indexOf(flashMode)
            setFlashMode(get(if (currentIndex < size - 1) currentIndex + 1 else 0))
        }
    }

    /**
     * Captures a new still image for in memory access.
     *
     * @see ImageCapture.takePicture
     */
    fun takePicture(callback: ImageCapture.OnImageCapturedCallback) {
        imageCapture?.takePicture(captureExecutor, callback)
    }

    /**
     * Captures a new still image and saves to a file along with application specified metadata.
     *
     * @see ImageCapture.OutputFileOptions
     */
    fun takePicture(
        options: ImageCapture.OutputFileOptions,
        callback: ImageCapture.OnImageSavedCallback
    ) {
        imageCapture?.takePicture(options, captureExecutor, callback)
    }

    /**
     * Captures a new still image and saves to provided file.
     */
    fun takePicture(
        file: File,
        callback: ImageCapture.OnImageSavedCallback
    ) {
        val options = ImageCapture.OutputFileOptions.Builder(file)
            .build()
        takePicture(options, callback)
    }

    /**
     * Captures a new still image and writes to provided output stream.
     */
    fun takePicture(
        outputStream: OutputStream,
        callback: ImageCapture.OnImageSavedCallback
    ) {
        val options = ImageCapture.OutputFileOptions.Builder(outputStream)
            .build()
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
        val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, saveCollection, contentValues)
            .build()
        takePicture(options, callback)
    }

    @Throws(CameraUnavailableException::class)
    protected fun getCameraSelector(): CameraSelector {
        return when {
            isFrontCameraPreferred && hasFrontCamera -> frontCameraSelector
            hasBackCamera -> backCameraSelector
            hasFrontCamera -> frontCameraSelector
            else -> throw CameraUnavailableException(CameraUnavailableException.CAMERA_UNKNOWN_ERROR)
        }
    }

    private fun onRetrieveProvider(provider: ProcessCameraProvider) {
        this.provider = provider
        hasFrontCamera = provider.hasCamera(frontCameraSelector)
        hasBackCamera = provider.hasCamera(backCameraSelector)
        callback?.get()?.onCameraCountAvailable(provider.availableCameraInfos.size)
        bindCamera(provider)
    }

    @MainThread
    private fun bindCamera(provider: ProcessCameraProvider) {
        try {
            provider.unbindAll()
            lifecycleOwner?.get()?.let { lifecycleOwner ->
                camera?.cameraInfo?.zoomState?.removeObservers(lifecycleOwner)
                camera = provider.bindToLifecycle(lifecycleOwner, getCameraSelector(), preview, imageCapture)
                camera?.cameraInfo?.zoomState?.observe(lifecycleOwner, ::onZoomStateChanged)
                setFlashMode(flashMode)
                resetAutoFocus()
            } ?: throw IllegalStateException()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getInternalFlashMode(mode: FlashMode): Int {
        return when (mode) {
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    private fun PreviewView.setAFPoint(
        focusX: Float? = null,
        focusY: Float? = null
    ) {
        val x = focusX ?: (width / 2f)
        val y = focusY ?: (height / 2f)

        camera?.cameraControl?.startFocusAndMetering(
            meteringPointFactory.createPoint(x, y)
                .let(FocusMeteringAction::Builder)
                .build()
        )
        callback?.get()?.apply {
            when (focusX == null && focusY == null) {
                true -> onCameraFocusReset()
                false -> onCameraFocusChanged(x, y)
            }
        }
    }

    private fun onZoomStateChanged(zoomState: ZoomState) {
        callback?.get()?.apply {
            onLinearZoomChanged(zoomState.linearZoom)
            onZoomRatioChanged(zoomState.zoomRatio)
        }
    }

    private inner class TouchListener : View.OnTouchListener {

        private val cameraGestureDetector by lazy { ScaleGestureDetector(context, ScaleGestureListener()) }

        private var isScaling = false

        override fun onTouch(view: View?, event: MotionEvent): Boolean {
            if (isPinchZoomEnabled) cameraGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                if (!isScaling && isTapToFocusEnabled) previewView?.get()?.setAFPoint(event.x, event.y)
                isScaling = false
            }

            return true
        }

        private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val zoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

                camera?.cameraControl?.setZoomRatio(zoom * detector.scaleFactor)
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                return true
            }
        }
    }

    private inner class OrientationListener(context: Context) : OrientationEventListener(context) {

        override fun onOrientationChanged(orientation: Int) {
            rotation = when (orientation) {
                ORIENTATION_UNKNOWN -> return
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
        }
    }
}