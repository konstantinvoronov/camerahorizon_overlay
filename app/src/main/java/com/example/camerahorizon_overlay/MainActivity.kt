package com.example.camerahorizon_overlay

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import java.lang.Integer.max
import java.lang.Integer.min
import java.lang.Math.abs


class MainActivity : AppCompatActivity() {

    lateinit var sensorManager: SensorManager
    lateinit var sensor_acc: Sensor
    lateinit var sensor_mgn: Sensor

    lateinit var ctx: Context

    lateinit var preview: Preview
    lateinit var bubblelevel_overlay: CameraLevel_Overlay

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    lateinit var viewfinder : PreviewView
    lateinit var container : ViewGroup

    private val TAG = "MainActivity"

    companion object{
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.hide()

        setContentView(R.layout.activity_main)
        container = findViewById<ViewGroup>(R.id.fragment_container)
        val controls = View.inflate(this, R.layout.camera_fragment, container)

        viewfinder =  findViewById(R.id.viewFinder)

        viewfinder.scaleType = PreviewView.ScaleType.FIT_START

        ctx = this

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensor_acc = accelerometer
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensor_mgn = magneticField
        }

        // we need viewfinder initisalized before we start bubblelevel
        viewfinder.post{

            if(this::sensor_acc.isInitialized){
                bubblelevel_overlay = CameraLevel_Overlay(sensorManager, sensor_acc,this)
                sensorManager.registerListener(bubblelevel_overlay, sensor_acc, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
            } else Toast.makeText(ctx, "Cant inititalize accelerometer", Toast.LENGTH_LONG).show()

            if(this::sensor_mgn.isInitialized){
                sensorManager.registerListener(bubblelevel_overlay, sensor_mgn, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
            } else Toast.makeText(ctx, "Cant inititalize magneticField", Toast.LENGTH_LONG).show()


            try {
                if(checkPersmission())
                {
                    startCamera()
                    updateCameraUi()
                }else{ getPermissions() }
            } catch (ex: RuntimeException) {
                Toast.makeText(ctx, "No camera permissions", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateCameraUi()

        imageCapture?.targetRotation = newConfig.orientation
        imageAnalyzer?.targetRotation =  newConfig.orientation
        bubblelevel_overlay.targetRotation = newConfig.orientation

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
        }
    }


    fun updateCameraUi()
    {
        // Remove previous UI if any
            container.findViewById<ConstraintLayout>(R.id.camera_ui)?.let {
                container.removeView(it)
        }

        // Inflate a new view containing all UI for controlling the camera
        val controls = View.inflate(this, R.layout.camera_ui, container)

    }

    fun checkPersmission(): Boolean {

        return (ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

        viewfinder = findViewById<View>(R.id.viewFinder) as PreviewView
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
                    updateCameraUi()
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



        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewfinder.display.getRealMetrics(it) }

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation =

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(Configuration.ORIENTATION_PORTRAIT)
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

            imageAnalyzer = ImageAnalysis.Builder()
                .build()
//                .also {
//                    Log.d(TAG, "ImageAnalysis")
//                    it.setAnalyzer(ContextCompat.getMainExecutor(this), LuminosityAnalyzer { luma ->
//                        Log.d(TAG, "Average luminosity: $luma")
//                    })
//                }

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

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }
}
