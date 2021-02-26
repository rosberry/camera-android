package com.rosberry.android.library.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import com.rosberry.android.library.controller.CameraController
import com.rosberry.android.library.controller.CameraControllerCallback
import com.rosberry.android.library.databinding.CameraViewBinding

class CameraView : ConstraintLayout, CameraControllerCallback {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private val binding = CameraViewBinding.inflate(LayoutInflater.from(context), this, true)

    private val controller by lazy {
        CameraController(context).apply {
            setPreviewView(binding.preview)
            setCallback(this@CameraView)
        }
    }

    fun attachLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        controller.start(lifecycleOwner)
    }
}