package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.AspectRatio.RATIO_4_3
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.ui.theme.CameraComposeWorkshopTheme
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (permissionGranted()) {
            initView()
        } else {
            requestPermission()
        }

    }

    private fun permissionGranted() =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), 0
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initView()
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initView() {
        setContent {
            CameraComposeWorkshopTheme {
                Surface(color = MaterialTheme.colors.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        var lens by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
                        var imageCapture by remember {
                            mutableStateOf(
                                ImageCapture.Builder().setCaptureMode(CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .setTargetAspectRatio(RATIO_4_3).build()
                            )
                        }
                        CameraPreview(
                            cameraLens = lens,
                            imageCapture = imageCapture
                        )
                        Controls(
                            onLensChange = { lens = switchLens(lens) },
                            onPictureTaken = {
                                imageCapture.takePicture(
                                    ContextCompat.getMainExecutor(applicationContext),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        @SuppressLint("UnsafeOptInUsageError")
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val result = image.image

                                            result?.cropRect?.set(600, 425, 20, 20)
                                            val buffer: ByteBuffer =
                                                (result?.getPlanes()?.get(0)?.getBuffer()
                                                    ?: null) as ByteBuffer
                                            val bytes = ByteArray(buffer.capacity())
                                            buffer.get(bytes)
                                            val bitmapImage = BitmapFactory.decodeByteArray(
                                                bytes,
                                                0,
                                                bytes.size,
                                                null
                                            )
                                            image.close()
                                            val resizedBitmap = Bitmap.createBitmap(bitmapImage,0,0,500,500)
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            super.onError(exception)
                                        }
                                    })
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun CameraPreview(
        modifier: Modifier = Modifier,
        cameraLens: Int,
        imageCapture: ImageCapture,
        scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER
    ) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current
        val previewView = remember { PreviewView(context) }
        val cameraProvider = remember(cameraLens) {
            ProcessCameraProvider.getInstance(context)
                .configureCamera(previewView, lifecycleOwner, cameraLens, context, imageCapture)
        }
        AndroidView(
            modifier = modifier,
            factory = {
                previewView.apply {
                    this.scaleType = scaleType
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                previewView
            })
    }
}

@Composable
fun Controls(
    onLensChange: () -> Unit,
    onPictureTaken: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Button(
                onClick = onLensChange,
                modifier = Modifier.wrapContentSize()
            ) { Text(text = "Switch Camera") }
            Button(onClick = onPictureTaken, modifier = Modifier.wrapContentSize()) {
                Text(text = "Take A Picture")
            }
        }
    }
}

private fun ListenableFuture<ProcessCameraProvider>.configureCamera(
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    cameraLens: Int,
    context: Context,
    imageCapture: ImageCapture
): ListenableFuture<ProcessCameraProvider> {
    addListener({
        val preview = androidx.camera.core.Preview.Builder()
            .build()
            .apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
        try {
            get().apply {
                unbindAll()
                bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(cameraLens).build(),
                    imageCapture,
                    preview
                )
            }
        } catch (exc: Exception) {
        }
    }, ContextCompat.getMainExecutor(context))
    return this
}

private fun switchLens(lens: Int) = if (CameraSelector.LENS_FACING_FRONT == lens) {
    CameraSelector.LENS_FACING_BACK
} else {
    CameraSelector.LENS_FACING_FRONT
}

