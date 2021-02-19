package com.example.camerahorizon_overlay


import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.Surface
import android.view.View
import android.widget.ImageView
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin


/**
 * Created by Victor on 13/03/2019.
 */

/*
   Kotlyn version updatedd by Konstantin Voronov on 10/02/2021
   i update clean and made more up to date
   updated it to use both magnetic and accelerometer code
   using this kotlin
   https://www.raywenderlich.com/10838302-sensors-tutorial-for-android-getting-started


    some devices may not support have magnetic_field sensor
    i use only accelerometer data

    SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
    val orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles)

    on my old xiaomi redmi go accelerometer sensor is not calibrated and at some point it never rich full gravity point
    which generates random misleading results in some phone positions. it seems safer to use values 0 to g/2

    to stay within safe values i use threshold values

    the app supports both album and landscape phone orientation
 */

class CameraLevel_Overlay(private val sensorManager: SensorManager, private val sensor: Sensor, ctx: Context) : SensorEventListener {

    private val toneGenerator: ToneGenerator
    private val canvasLevelSquare: Canvas
    private val paintRectangle: Paint
    private val bitmapLevelSquare: Bitmap
    private val mlevelsquare: ImageView

    private val paintWhenWhithinLimits: Paint
    private val paintWhenAboveLimits: Paint

    lateinit var p_activity : Activity


    private var tonePlayed: Boolean

    private var x_angle: Double = 0.0
    private var y_angle: Double = 0.0
    private var z_angle: Double = 0.0

    var gravity = FloatArray(3)

    val phoneAngles: DoubleArray
        get() = doubleArrayOf(x_angle, y_angle, z_angle)

    var targetRotation : Int = Configuration.ORIENTATION_PORTRAIT

    companion object {
        private const val TAG = "CameraHorizonAngle"
        private const val GRAVITY = 9.81
        private const val AcceptableYawLimits = 2.0
        private const val AcceptablePitchLimits = 5.0


        val HorizonPitchThresholds = 20.0
        val HorizonYawThresholds = 20.0

        val horizonsquare_width = 300.0
        val horizonsquare_height = 300.0
        val horizonline_length = 100.0
    }

    init {
        mlevelsquare = (ctx as Activity).findViewById<View>(R.id.levelsquare) as ImageView

        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

        bitmapLevelSquare = Bitmap.createBitmap(
            horizonsquare_width.toInt(),  // Width
            horizonsquare_height.toInt(),  // Height
            Bitmap.Config.ARGB_8888 // Config
        )

        p_activity = ctx as Activity

        canvasLevelSquare = Canvas(bitmapLevelSquare)

        paintRectangle = Paint()
        paintRectangle.style = Paint.Style.FILL
        paintRectangle.color = Color.YELLOW
        paintRectangle.isAntiAlias = true

        paintWhenWhithinLimits = Paint()
        paintWhenWhithinLimits.style = Paint.Style.FILL
        paintWhenWhithinLimits.color = Color.GREEN
        paintWhenWhithinLimits.isAntiAlias = true
        paintWhenWhithinLimits.strokeWidth = 5f

        paintWhenAboveLimits = Paint()
        paintWhenAboveLimits.style = Paint.Style.FILL
        paintWhenAboveLimits.color = Color.RED
        paintWhenAboveLimits.isAntiAlias = true
        paintWhenAboveLimits.strokeWidth = 5f

        tonePlayed = false

    }


    fun drawHorizonYawLevel(angle: Double){

        var lpaint = paintWhenWhithinLimits
        if(AcceptableYawLimits < angle.absoluteValue) lpaint = paintWhenAboveLimits

        var langle = angle
        if (HorizonYawThresholds < angle && angle > 0) langle = HorizonYawThresholds
        if (-HorizonYawThresholds > angle && angle < 0) langle = -HorizonYawThresholds

        drawCenterAngleLine(-langle,lpaint)
        drawCenterAngleLine(180-langle,lpaint)
    }
    fun drawHorizonPitchLevel(angle: Double){
        var lpaint = paintWhenWhithinLimits
        if(AcceptablePitchLimits < angle.absoluteValue) lpaint = paintWhenAboveLimits

        var langle = angle

        if (HorizonPitchThresholds < angle && angle > 0) langle = HorizonPitchThresholds
        if (-HorizonPitchThresholds > angle && angle < 0) langle = -HorizonPitchThresholds

        val LineWidth: Int = horizonsquare_width.toInt()

        var startx = 1
        var starty= (horizonsquare_height/2) - langle

        canvasLevelSquare.drawLine(
            (startx).toFloat(),
            (starty - langle).toFloat(),
            (startx+LineWidth).toFloat(),
            (starty - langle).toFloat(),
            lpaint
        )
    }
    private fun drawCenterAngleLine(angle: Double,lpaintLine : Paint) {

        var startx = (horizonsquare_width/2)
        var starty= (horizonsquare_height/2)

        canvasLevelSquare.drawLine(
            (startx).toFloat(),
            (starty).toFloat(),
            (startx+cos(Math.toRadians(angle))* (horizonsquare_width/2)).toFloat(),
            (starty+sin(Math.toRadians(angle)) * (horizonsquare_height/2)).toFloat(),
            lpaintLine
        )
    }


    override fun onSensorChanged(sensorEvent: SensorEvent) {

        if (sensorEvent == null) return

        /* i assume that pictures is taken in still postiion
         and ignore everything above or below G. */

        // average sensor values stored in gravity
        val alpha = 0.3f
        gravity[0] = alpha * gravity.get(0) + (1 - alpha) * sensorEvent.values[0]
        gravity[1] = alpha * gravity.get(1) + (1 - alpha) * sensorEvent.values[1]
        gravity[2] = alpha * gravity.get(2) + (1 - alpha) * sensorEvent.values[2]


        // limit every force to 1G
        var x_force = if (gravity[0] > GRAVITY) GRAVITY else gravity[0].toDouble()
        var y_force = if (gravity[1] > GRAVITY) GRAVITY else gravity[1].toDouble()
        var z_force = if (gravity[2] > GRAVITY) GRAVITY else gravity[2].toDouble()

        x_force = if (x_force < -GRAVITY) -GRAVITY else x_force
        y_force = if (y_force < -GRAVITY) -GRAVITY else y_force
        z_force = if (z_force < -GRAVITY) -GRAVITY else z_force

        // calculate thetha angle for each axis
        x_angle = round(Math.toDegrees(Math.asin(x_force / GRAVITY)) * 100) / 100
        y_angle = round(Math.toDegrees(Math.asin(y_force / GRAVITY)) * 100) / 100
        z_angle = round(Math.toDegrees(Math.asin(z_force / GRAVITY)) * 100) / 100

        update_ui()
    }

    override fun onAccuracyChanged(sensor: Sensor, i: Int) {}

    fun update_ui()
    {
        // show values
//        ssx.setText((x_angle*100/100).toString())
//        ssy.setText((y_angle*100/100).toString())
//        ssz.setText((z_angle*100/100).toString())
//
//        spitch.setText((y_angle*100/100).toString())
//        sroll.setText((x_angle*100/100).toString())
//        syaw.setText((z_angle*100/100).toString())

        // clear the horizonsquare and drow lines
        canvasLevelSquare.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        when (targetRotation)
        {
            Configuration.ORIENTATION_PORTRAIT -> {
                drawHorizonYawLevel(-x_angle)
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                drawHorizonYawLevel(-y_angle)
            }
        }
        drawHorizonPitchLevel(z_angle)

        // show the bitmap
        mlevelsquare.setImageBitmap(bitmapLevelSquare)    }

}
