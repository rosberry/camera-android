package com.rosberry.android.library

interface CameraControllerCallback {

    /**
     * Called when flash mode changed due to user interaction or camera switching.
     */
    fun onFlashModeChanged(mode: FlashMode) {}

    /**
     * Called when auto-focus position changes via tap-to-focus gesture.
     */
    fun onFocusChanged(x: Float, y: Float) {}

    /**
     * Called when auto-focus position resets.
     */
    fun onFocusReset() {}

    /**
     * Called when zoom ratio changed.
     */
    fun onZoomRatioChanged(zoom: Float) {}

    /**
     * Called when linear zoom changed.
     */
    fun onLinearZoomChanged(zoom: Float) {}
}