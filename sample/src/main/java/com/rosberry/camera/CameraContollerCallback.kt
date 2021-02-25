package com.rosberry.camera

interface CameraControllerCallback {

    /**
     * Called when auto-focus position changes via tap-to-focus gesture.
     */
    fun onFocusChanged(x: Float, y: Float) { }

    /**
     * Called when auto-focus position resets.
     */
    fun onFocusReset() { }
}