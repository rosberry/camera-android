package com.rosberry.camera

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max

class CameraController(private val activity: Activity) : LifecycleObserver, TextureView.SurfaceTextureListener {

    private val cameraManager by lazy { activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private val cameraStateCallback by lazy { CameraStateCallback() }
    private val captureStateCallback by lazy { CaptureStateCallback() }

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var textureView: WeakReference<TextureView>? = null

    private lateinit var cameraId: String
    private lateinit var previewSize: Size
    private lateinit var previewRequest: CaptureRequest
    private lateinit var requestBuilder: CaptureRequest.Builder

    fun setTextureView(textureView: TextureView) {
        onPause()
        this.textureView = WeakReference(textureView)
        onResume()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        openCamera(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        transformPreview(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun onResume() {
        textureView?.get()
            ?.run {
                when (isAvailable) {
                    true -> openCamera(width, height)
                    false -> surfaceTextureListener = this@CameraController
                }
            }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private fun onPause() {
        session?.close()
        session = null
        camera?.close()
        camera = null
    }

    private fun createPreviewSession() {
        try {
            textureView?.get()
                ?.run {
                    surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
                    camera?.let { camera ->
                        val surface = Surface(surfaceTexture)

                        requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        requestBuilder.addTarget(surface)
                        camera.createCaptureSession(listOf(surface), captureStateCallback, null)
                    }
                }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun openCamera(viewWidth: Int, viewHeight: Int) {
        configureCamera(viewWidth, viewHeight)
        transformPreview(viewWidth, viewHeight)
        try {
            cameraManager.openCamera(cameraId, cameraStateCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun configureCamera(width: Int, height: Int) {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) continue

                val sizes = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(SurfaceTexture::class.java) ?: continue
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                val isRotatedImage = isRotatedImage(sensorOrientation)
                val viewWidth = if (isRotatedImage) height else width
                val viewHeight = if (isRotatedImage) width else height

                previewSize = getPreviewSize(sizes, viewWidth, viewHeight)

                this.cameraId = cameraId

                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    private fun transformPreview(width: Int, height: Int) {
        val displayRotation = activity.windowManager.defaultDisplay.rotation

        Matrix().run {
            val centerX = width / 2f
            val centerY = height / 2f
            val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
            var scale = 1f
            var rotation = 0f

            when (displayRotation) {
                Surface.ROTATION_0,
                Surface.ROTATION_180 -> {
                    setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                    rotation = 90f * (displayRotation)
                }
                Surface.ROTATION_90,
                Surface.ROTATION_270 -> {
                    bufferRect.apply { offset(centerX - centerX(), centerY - centerY()) }
                    scale = max(width.toFloat() / previewSize.width, height.toFloat() / previewSize.height)
                    setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                    rotation = 90f * (displayRotation - 2)
                }
            }
            postScale(scale, scale, centerX, centerY)
            postRotate(rotation, centerX, centerY)
            textureView?.get()?.setTransform(this)
        }
    }

    private fun getPreviewSize(sizes: Array<Size>, viewWidth: Int, viewHeight: Int): Size {
        val viewAspect = viewWidth / viewHeight.toFloat()
        var bestSize = sizes[0]
        var bestAspectDif = abs(bestSize.aspect - viewAspect)
        var bestDif = abs(sizes[0].width - viewWidth) + abs(sizes[0].height - viewHeight)

        for (size in sizes) {
            if (bestAspectDif == 0f && bestDif == 0) break

            val aspectDif = abs(size.aspect - viewAspect)
            if (aspectDif <= bestAspectDif) {
                val dif = abs(size.width - viewWidth) + abs(size.height - viewHeight)
                if (dif < bestDif) {
                    bestSize = size
                    bestAspectDif = aspectDif
                    bestDif = dif
                }
            }
        }
        return bestSize
    }

    private fun isRotatedImage(sensorOrientation: Int?): Boolean {
        return when (activity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0,
            Surface.ROTATION_180 -> sensorOrientation == 90 || sensorOrientation == 270
            Surface.ROTATION_90,
            Surface.ROTATION_270 -> sensorOrientation == 0 || sensorOrientation == 180
            else -> false
        }
    }

    private val Size.aspect: Float
        get() = width / height.toFloat()

    private inner class CameraStateCallback : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            this@CameraController.camera = camera
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            this@CameraController.camera = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            onDisconnected(camera)
        }
    }

    private inner class CaptureStateCallback : CameraCaptureSession.StateCallback() {

        override fun onConfigured(session: CameraCaptureSession) {
            camera ?: return

            this@CameraController.session = session
            try {
                requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                previewRequest = requestBuilder.build()
                session.setRepeatingRequest(previewRequest, null, null)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) = Unit
    }
}