package com.rosberry.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import com.rosberry.camera.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlin.math.max

private const val REQUEST_CODE_CAMERA = 100

class MainActivity : AppCompatActivity() {

    private val cameraStateCallback = CameraStateCallback()
    private val captureStateCallback = CaptureStateCallback()
    private val surfaceTextureListener = SurfaceTextureListener()

    private val cameraManager by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    private lateinit var cameraId: String
    private lateinit var binding: ActivityMainBinding
    private lateinit var textureView: TextureView
    private lateinit var previewSize: Size
    private lateinit var previewRequest: CaptureRequest
    private lateinit var requestBuilder: CaptureRequest.Builder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        textureView = binding.textureView
    }

    override fun onResume() {
        super.onResume()

        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        session?.close()
        session = null
        camera?.close()
        camera = null
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_CAMERA && grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
            if (textureView.isAvailable) {
                openCamera(textureView.width, textureView.height)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun openCamera(width: Int, height: Int) {
        when (checkSelfPermission(this, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                configureCamera(width, height)
                transformPreview(width, height)
                try {
                    cameraManager.openCamera(cameraId, cameraStateCallback, null)
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            else -> ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA)
        }
    }

    private fun createPreviewSession() {
        try {
            textureView.surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)

            camera?.apply {
                val surface = Surface(textureView.surfaceTexture)

                requestBuilder = createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                requestBuilder.addTarget(surface)

                createCaptureSession(listOf(surface), captureStateCallback, null)
            }
        } catch (e: CameraAccessException) {
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
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0,
            Surface.ROTATION_180 -> sensorOrientation == 90 || sensorOrientation == 270
            Surface.ROTATION_90,
            Surface.ROTATION_270 -> sensorOrientation == 0 || sensorOrientation == 180
            else -> false
        }
    }

    private fun transformPreview(width: Int, height: Int) {
        val rotation = windowManager.defaultDisplay.rotation

        Matrix().run {
            val centerX = width / 2f
            val centerY = height / 2f

            when (rotation) {
                Surface.ROTATION_180 -> postRotate(180f, centerX, centerY)
                Surface.ROTATION_90, Surface.ROTATION_270 -> {
                    val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
                    val scale = max(width.toFloat() / previewSize.width, height.toFloat() / previewSize.height)
                    val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
                        .apply { offset(centerX - centerX(), centerY - centerY()) }

                    setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                    postScale(scale, scale, centerX, centerY)
                    postRotate(90f * (rotation - 2), centerX, centerY)
                }
            }
            textureView.setTransform(this)
        }
    }

    private val Size.aspect: Float
        get() = width / height.toFloat()

    private inner class CameraStateCallback : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            this@MainActivity.camera = camera
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            this@MainActivity.camera = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            onDisconnected(camera)
        }
    }

    private inner class CaptureStateCallback : CameraCaptureSession.StateCallback() {

        override fun onConfigured(session: CameraCaptureSession) {
            camera ?: return
            this@MainActivity.session = session
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

    private inner class SurfaceTextureListener : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            transformPreview(width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }
}