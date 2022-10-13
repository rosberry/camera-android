# Camera Controller

CameraX facade designed to reduce boilerplate code for simple image capture use cases.

## Features

- Switch between front and back cameras
- Automatic device rotation detection for correct EXIV rotation metadata
- Flash modes
- Pinch to zoom
- Tap to focus
- Camera-related events callback for wiring with UI

## Requirements

- Java 8+
- Android API 21+

## Setup

Add a dependency

```groovy
implementation 'com.rosberry.android:camera-controller:1.0.0'
```

## Usage

Initialize `CameraController` with `Context`:

```kotlin
    val cameraController: CameraController = CameraController(context)
```

Set preferred camera with `setFrontCameraPreffered(Boolean)`, you can switch between front and back cameras 
later using `switchCamera()` method.

Setup controller with `androidx.camera.view.PreviewView` instance, `CameraControllerCallback` implementation 
to wire camera events with UI and run it using `start(LifecycleOwner)` method:

```kotlin
    controller.setPreviewView(previewView)
    controller.setCallback(cameraControllerCallback)
    controller.start(lifecycleOwner)
```

Control camera flash modes with `setFlashMode(FlashMode)` and `cycleFlashMode()` methods.
By default `cycleFlashMode()` will cycle through all available flash modes for selected camera. Variety of available 
modes could be limited by providing desired modes to `setAvailableFlashModes(vararg FlashMode)` method. Note that
if `setFlashMode(FlashMode)` argument doesn't exist in list of available modes, component will force use provided mode
though it won't be available for `cycleFlashMode()`.

To take picture call `takePicture(ImageCapture.OutputFileOptions, ImageCapture.OnImageSavedCallback)` or one of the
convenience methods `takePicture(File, ImageCapture.OnImageSavedCallback)`, 
`takePicture(OutputStream, ImageCapture.OnImageSavedCallback)` or 
`takePicture(Uri, ContentValues, ImageCapture.OnImageSavedCallback)`. 

## About

<img src="https://github.com/rosberry/Foundation/blob/master/Assets/full_logo.png?raw=true" height="100" />

This project is owned and maintained by [Rosberry](http://rosberry.com). We build mobile apps for users worldwide 🌏.

Check out our [open source projects](https://github.com/rosberry), read [our blog](https://medium.com/@Rosberry) or give
us a high-five on 🐦 [@rosberryapps](http://twitter.com/RosberryApps).

## License

Camera Controller is available under the MIT license. See the LICENSE file for more info.
