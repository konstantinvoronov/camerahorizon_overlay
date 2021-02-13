package com.example.camerahorizon_overlay

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CameraHorizonActivity : AppCompatActivity() {

    lateinit var sensorManager: SensorManager
    lateinit var sensor_acc: Sensor
    lateinit var sensor_mgn: Sensor

    lateinit var act: Activity
    lateinit var ctx: Context

    lateinit var preview: Preview
    lateinit var bubblelevel_overlay: CameraHorizon_Overlay

    private var camera: Camera? = null
    lateinit var viewfinder : PreviewView
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private val TAG = "FullScreenActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_comerahorizon)

        viewfinder =  findViewById(R.id.viewFinder)

        // for better quality i need to implement this https://stackoverflow.com/questions/46020451/how-to-increase-the-resolution-in-camera-preview
        // not neseccirely though. i use camerax it does a lot automatically
        // https://developer.android.com/training/camerax/configuration
        // https://developer.android.com/training/camerax/preview
        viewfinder.scaleType = PreviewView.ScaleType.FIT_START

        act = this
        ctx = this

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager


        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensor_acc = accelerometer
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensor_mgn = magneticField
        }

        if(this::sensor_acc.isInitialized){
            bubblelevel_overlay = CameraHorizon_Overlay(sensorManager, sensor_acc,this)
            sensorManager.registerListener(bubblelevel_overlay, sensor_acc, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        } else Toast.makeText(ctx, "Cant inititalize accelerometer", Toast.LENGTH_LONG).show()

        if(this::sensor_mgn.isInitialized){
            sensorManager.registerListener(bubblelevel_overlay, sensor_mgn, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        } else Toast.makeText(ctx, "Cant inititalize magneticField", Toast.LENGTH_LONG).show()


        try {
            if(checkPersmission())
            {
                startCamera()
            }else{ getPermissions() }
        } catch (ex: RuntimeException) {
            Toast.makeText(ctx, "No camera permissions", Toast.LENGTH_LONG).show()
        }



        // i will need this to do overlay
        // https://stackoverflow.com/questions/64903838/capture-overlay-using-previewview-of-camerax

        // and that is for detectors
        // https://developers.google.com/android/reference/com/google/android/gms/vision/CameraSource
        // https://developers.google.com/android/reference/com/google/android/gms/vision/Detector
    }

    fun checkPersmission(): Boolean {

        return (ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

    }

    private fun getPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,Manifest.permission.CAMERA) !== PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) !== PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) !== PackageManager.PERMISSION_GRANTED)
        {
            Log.i(TAG,"Accessing camera permission.")
            ActivityCompat.requestPermissions(this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE),
                1
            )
        } else Log.i(TAG,"Camera permission already granted.")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCamera()

                } else {
                    Toast.makeText(ctx, "No camera permissions access granted", Toast.LENGTH_LONG).show()
                }
                return

            }

            else -> {

            }
        }
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        Log.d(TAG, "Start Camera")



        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                .build()

//            var sizes = StreamConfigurationMap.getOutputSizes()
//            var sizes= StreamConfigurationMap.isOutputSupportedFor(ImageFormat.JPEG)

            imageCapture = ImageCapture.Builder()
//                .setTargetResolution(Size(640, 480))
//                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    Log.d(TAG, "ImageAnalysis")
                    it.setAnalyzer(ContextCompat.getMainExecutor(this), LuminosityAnalyzer { luma ->
                        Log.d(TAG, "Average luminosity: $luma")
                    })
                }

            // Select back camera
            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture,imageAnalyzer)
                preview?.setSurfaceProvider(viewfinder.createSurfaceProvider(camera?.cameraInfo))
            } catch(exc: Exception) {
                Log.d(TAG, "ERROR: Use case binding failed: $exc")
            }

        }, ContextCompat.getMainExecutor(this))

    }

}