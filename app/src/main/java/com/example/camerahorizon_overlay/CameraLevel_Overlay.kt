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
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin


/**
 * Created by Voronov Konstantin
 * me@konstantinvoronov.com
 *
 * CameraLevel_Overlay demonstrates how to make photography style HorizonLevel overlay using Accelerometer sensor ONLY
 * App uses CameraX APIs and is written in Kotlin.
 *
 * Story: Google's current approach to determine phone position works only with both MagneticField and Accelerometer sensor
 *
 *  https://developer.android.com/guide/topics/sensors/sensors_position
 *  Google documentation guides developers to use getRotationMatrix to get phone angles which requires magnetic senors readings
 *
 * SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
 *
 * Using two sensors for CameraLevel is an excessive approach and less universal
 * Many cheap android phone yet lack magneticfield sensor and thus lack positioning feature
 *
 * CameraLevel_Overlay is a simple way to make photography style Horizontal level using only Accelerometer sensor

* You can receive pitch roll and do custom thing
* bubblelevel_overlay.setOnActionListener { pitch, roll -> }
 *  or update CameraLevel_Overlay.update_ui to handle everything inside the class
 *
 *
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


    // tone is playd when angles become accaptable
    private var tonePlayed: Boolean

    private var x_angle: Double = 0.0
    private var y_angle: Double = 0.0
    private var z_angle: Double = 0.0

    var gravity = FloatArray(3)

    val phonePitchYaw: DoubleArray
        get() {
            if (targetRotation == Configuration.ORIENTATION_PORTRAIT)
                return doubleArrayOf(x_angle,z_angle)
            return doubleArrayOf(y_angle,z_angle)
        }
    lateinit var OnActionListenerListener: (Double,Double) -> Unit

    var targetRotation : Int = Configuration.ORIENTATION_PORTRAIT

    companion object {
        private const val TAG = "CameraHorizonAngle"
        private const val GRAVITY = 9.81

        // green lines if within this limit
        private const val AcceptableYawLimit = 2.0
        private const val AcceptablePitchLimit = 5.0

        // lines stop move if above this Threshold
        val HorizonPitchThreshold = 20.0
        val HorizonYawThreshold = 20.0

        // square size
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
        if(AcceptableYawLimit < angle.absoluteValue) lpaint = paintWhenAboveLimits

        var langle = angle
        if (HorizonYawThreshold < angle && angle > 0) langle = HorizonYawThreshold
        if (-HorizonYawThreshold > angle && angle < 0) langle = -HorizonYawThreshold

        drawCenterAngleLine(-langle,lpaint)
        drawCenterAngleLine(180-langle,lpaint)
    }
    fun drawHorizonPitchLevel(angle: Double){
        var lpaint = paintWhenWhithinLimits
        if(AcceptablePitchLimit < angle.absoluteValue) lpaint = paintWhenAboveLimits

        var langle = angle

        if (HorizonPitchThreshold < angle && angle > 0) langle = HorizonPitchThreshold
        if (-HorizonPitchThreshold > angle && angle < 0) langle = -HorizonPitchThreshold

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

        if(::OnActionListenerListener.isInitialized)
            OnActionListenerListener(phonePitchYaw[1],phonePitchYaw[0])

        update_ui()
    }

    fun setOnActionListener(onActionListener: (pitch:Double,yaw:Double) -> Unit) {
        OnActionListenerListener = onActionListener
    }


    override fun onAccuracyChanged(sensor: Sensor, i: Int) {}

    fun update_ui()
    {

        // clear the horizonsquare and drow lines
        canvasLevelSquare.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        drawHorizonPitchLevel(-phonePitchYaw[1])
        drawHorizonYawLevel(-phonePitchYaw[0])

        // show the bitmap
        mlevelsquare.setImageBitmap(bitmapLevelSquare)
    }

}
