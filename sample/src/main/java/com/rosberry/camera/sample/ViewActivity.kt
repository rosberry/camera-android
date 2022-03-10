package com.rosberry.camera.sample

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import com.rosberry.camera.sample.databinding.ActivityViewBinding
import com.rosberry.camera.view.CameraView
import java.io.File

class ViewActivity : AppCompatActivity(), ImageCapture.OnImageSavedCallback {

    companion object {

        private const val REQUEST_CODE_CAMERA = 407

        private const val GRANTED = PackageManager.PERMISSION_GRANTED
        private const val DIR_NAME = "Camera Component"
    }

    private val isLegacySdk: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private lateinit var binding: ActivityViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (grantResults.all { result -> result == PackageManager.PERMISSION_GRANTED }) startCamera()
            else Toast.makeText(this, "Provide gallery and camera permissions to continue", Toast.LENGTH_SHORT).show()
        } else super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onImageSaved(results: ImageCapture.OutputFileResults) {
        results.savedUri?.apply {
            if (isLegacySdk) MediaScannerConnection.scanFile(
                this@ViewActivity,
                arrayOf(toFile().absolutePath),
                arrayOf("image/jpeg"),
                null
            )
        }?.let(::showCapturedBitmap)
    }

    override fun onError(exception: ImageCaptureException) {
        exception.printStackTrace()
    }

    private fun startCamera() {
        binding.cameraView.run {
            setTakePhotoListener { if (!isLegacySdk) takePicture() else takePictureLegacy() }
            start(this@ViewActivity)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun CameraView.takePicture() {
        takePicture(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + DIR_NAME)
            },
            this@ViewActivity
        )
    }

    private fun CameraView.takePictureLegacy() {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), DIR_NAME)
            .apply { mkdirs() }
            .run { File(this, "${System.currentTimeMillis()}.jpg") }
            .run { takePicture(this, this@ViewActivity) }
    }

    private fun showCapturedBitmap(capturedUri: Uri?) {
        capturedUri
            ?.run { contentResolver.openInputStream(this) }
            ?.run { BitmapFactory.decodeStream(this, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
            ?.run { runOnUiThread { binding.imagePreview.setImageBitmap(this) } }
    }
}