package com.rosberry.camera.sample

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rosberry.camera.sample.databinding.ActivityViewBinding
import java.io.File
import java.io.FileInputStream

class ViewActivity : AppCompatActivity() {

    companion object {

        private const val REQUEST_CODE_CAMERA = 407
    }

    private lateinit var binding: ActivityViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_CAMERA && grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun startCamera() {
        binding.cameraView.run {
            setTakePhotoListener {
                val file = File.createTempFile("${System.currentTimeMillis()}", ".jpg")
                takePicture(file, object : ImageCapture.OnImageSavedCallback {

                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        FileInputStream(file).use {
                            val options = BitmapFactory.Options()
                            options.inSampleSize = 4

                            val bitmap = BitmapFactory.decodeStream(it, null, options)
                            runOnUiThread { binding.imagePreview.setImageBitmap(bitmap) }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                    }
                })
            }
            start(this@ViewActivity)
        }
    }
}