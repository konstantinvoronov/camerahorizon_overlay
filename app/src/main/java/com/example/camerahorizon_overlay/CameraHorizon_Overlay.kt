package com.example.camerahorizon_overlay


import android.app.Activity
import android.content.Context
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
import kotlin.math.*


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

class CameraHorizon_Overlay(private val sensorManager: SensorManager, private val sensor: Sensor, ctx: Context) : SensorEventListener {

    private val userMessage: TextView
    private val ssx: TextView
    private val ssy: TextView
    private val ssz: TextView
    private val spitch: TextView
    private val sroll: TextView
    private val syaw: TextView

    private val toneGenerator: ToneGenerator
    private val canvasY: Canvas
    private val canvasX: Canvas
    private val canvasLevelSquare: Canvas
    private val rectangleY: Rect
    private val rectangleX: Rect
    private val paintRectangle: Paint

    private val bitmapVertical: Bitmap
    private val bitmapHorizontal: Bitmap
    private val bitmapLevelSquare: Bitmap

    private val mImageViewY: ImageView
    private val mImageViewX: ImageView
    private val mlevelsquare: ImageView
    private val paintLine: Paint


    private val paintCorrect: Paint
    private val paintWrong: Paint


    var isCameraEnabled: Boolean
        private set
    private var tonePlayed: Boolean
    private var thetaX: Double
    private var thetaY: Double
    private var thetaZ: Double = 0.0


    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)


    fun drawHorizonYawLevel(angle: Double){
        val HorizonPitchThresholds = 2.0

        var lpaint = paintCorrect
        if(HorizonPitchThresholds < angle.absoluteValue) lpaint = paintWrong

        drawCenterAngleLine(-angle,lpaint)
        drawCenterAngleLine(180-angle,lpaint)
    }

    fun drawHorizonPitchLevel(angle: Double){
        val Width = 300.0
        val Height = 300.0
        val Length = 100.0

        val HorizonPitchThresholds = 3.0

        var lpaint = paintCorrect
        if(HorizonPitchThresholds < angle.absoluteValue) lpaint = paintWrong

        val limit = 20.0
        var langle = 0.0

        if (limit < angle) langle = limit else langle = angle

        val LineWidth: Int = 298

        var startx = 1
        var starty= (Height/2) - langle

        canvasLevelSquare.drawLine(
            (startx).toFloat(),
            (starty - langle).toFloat(),
            (startx+LineWidth).toFloat(),
            (starty - langle).toFloat(),
            lpaint
        )
    }

    fun checkSpeed() : Boolean
    {
        // check ExosureSpeed - Speed should be at least 1/Focal length
        // get camera focal lenth
        // get camera current shutter speed
        return true
    }

    // i am going to use this example for that
    // https://github.com/tbouron/ShakeDetector/blob/master/library/src/main/java/com/github/tbouron/shakedetector/library/ShakeDetector.java
    fun detectExcessiveShakiness()
    {

    }

    // dont know how to use it yet.
    fun CameraIsTooFar()
    {

    }

    // dont know how to use it yet.
    // https://ssaurel.medium.com/learn-to-create-a-luminosity-detector-application-on-android-c8c64726b2cb
    fun OutsideLuminiousity()
    {

    }

    private fun drawCenterAngleLine(angle: Double,lpaintLine : Paint) {
        val Width = 300
        val Height = 300
        val Length = 100

        val handRadius: Int = 298

        var startx = (Width/2)
        var starty= (Height/2)

        canvasLevelSquare.drawLine(
            (startx).toFloat(),
            (starty).toFloat(),
            (startx+cos(Math.toRadians(angle))* (Width/2)).toFloat(),
            (starty+sin(Math.toRadians(angle)) * (Height/2)).toFloat(),
            lpaintLine
        )
    }


    override fun onSensorChanged(sensorEvent: SensorEvent) {

        if (sensorEvent == null) return

        if (sensorEvent.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(sensorEvent.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (sensorEvent.sensor.type == Sensor.TYPE_MAGNETIC_FIELD)
            System.arraycopy(sensorEvent.values, 0, magnetometerReading, 0, magnetometerReading.size)

        updateOrientationAngles()


        /* original calculations from viktors example */

        var gx =
            if (sensorEvent.values[0] > GRAVITY) GRAVITY else sensorEvent.values[0].toDouble()
        var gy =
            if (sensorEvent.values[1] > GRAVITY) GRAVITY else sensorEvent.values[1].toDouble()
        var gz =
            if (sensorEvent.values[2] > GRAVITY) GRAVITY else sensorEvent.values[2].toDouble()

        gx = if (gx < -GRAVITY) -GRAVITY else gx
        gy = if (gy < -GRAVITY) -GRAVITY else gy
        gz = if (gz < -GRAVITY) -GRAVITY else gz

        // another pitch and roll
        thetaX = round(Math.toDegrees(Math.asin(gx / GRAVITY)) * 100) / 100 // pitch 0-90 looking up or down when 0 camera is facing earth or sky when closer to 90 facing horizon
        thetaY = round(Math.toDegrees(Math.asin(gy / GRAVITY)) * 100) / 100 // roll 0-90
        thetaZ = round(Math.toDegrees(Math.asin(gz / GRAVITY)) * 100) / 100 // roll 0-90

        /* calculations from https://wiki.dfrobot.com/How_to_Use_a_Three-Axis_Accelerometer_for_Tilt_Sensing */

        //Roll & Pitch are the angles which rotate by the axis X and y
        var pitch : Double = 0.00
        var yaw : Double = 0.00

        var x_Buff : Float = gx.toFloat()
        var y_Buff : Float = gy.toFloat()
        var z_Buff : Float = gz.toFloat()

        pitch = round((atan2(y_Buff , z_Buff) * 57.3) * 100) / 100
        yaw = round((atan2((0 - x_Buff) , sqrt(y_Buff * y_Buff + z_Buff * z_Buff)) * 57.3) * 100) / 100
        //pitch = round((atan2((0 - x_Buff) , sqrt(y_Buff * y_Buff + z_Buff * z_Buff)) * 57.3) * 100) / 100



        ssx.setText((x_Buff*100/100).toString())
        ssy.setText((y_Buff*100/100).toString())
        ssz.setText((z_Buff*100/100).toString())

        spitch.setText((yaw*100/100).toString())
        sroll.setText((pitch*100/100).toString())
        syaw.setText((thetaZ*100/100).toString())


        //      Log.d(TAG,"sx: ${gx} sy: ${gy} sz: ${gz} thetaX: ${thetaX} thetaY ${thetaY} roll:${roll} pitch:${pitch} roll2:${roll2} pitch2:${pitch2}")
   //     Log.d("thetaX","${thetaX}")
     //   Log.d("thetaY","${thetaY}")
       // Log.d("roll","${roll}")
       // Log.d("pitch","${pitch}")

        canvasY.drawRect(rectangleY, paintRectangle)
        canvasX.drawRect(rectangleX, paintRectangle)
        canvasLevelSquare.drawColor(Color.WHITE)

        drawHorizonYawLevel(yaw)
        drawHorizonPitchLevel(thetaZ)

// roll 90 pitch 0 - landscape orintation faceing horizon
        // roll 0 p 0 - facing bottom or sky
        // roll 90 piych 0 albom orientation facing horizon

        // Draw Thresholds
        canvasY.drawLine(
            0f,
            getLineLocation(MAX_DEGREE).toFloat(),
            canvasY.width.toFloat(),
            getLineLocation(MAX_DEGREE).toFloat(),
            paintLine
        )
        canvasY.drawLine(
            0f,
            getLineLocation(MIN_DEGREE).toFloat(),
            canvasY.width.toFloat(),
            getLineLocation(MIN_DEGREE).toFloat(),
            paintLine
        )
        canvasX.drawLine(
            getLineLocation(MAX_DEGREE).toFloat(),
            0f,
            getLineLocation(MAX_DEGREE).toFloat(),
            canvasX.height.toFloat(),
            paintLine
        )
        canvasX.drawLine(
            getLineLocation(MIN_DEGREE).toFloat(),
            0f,
            getLineLocation(MIN_DEGREE).toFloat(),
            canvasX.height.toFloat(),
            paintLine
        )
        canvasY.drawLine(
            0f,
            getLineLocation(thetaY).toFloat(),
            canvasY.width.toFloat(),
            getLineLocation(thetaY).toFloat(),
            paintLine
        )
        canvasX.drawLine(
            getLineLocation(thetaX).toFloat(),
            0f,
            getLineLocation(thetaX).toFloat(),
            canvasX.height.toFloat(),
            paintLine
        )

        mImageViewY.setImageBitmap(bitmapVertical)
        mImageViewX.setImageBitmap(bitmapHorizontal)
        mlevelsquare.setImageBitmap(bitmapLevelSquare)

        if (thetaX >= MIN_DEGREE && thetaX <= MAX_DEGREE && thetaY >= MIN_DEGREE && thetaY <= MAX_DEGREE && gz > 0.0) {
            isCameraEnabled = true
            userMessage.setBackgroundColor(Color.GREEN)
            userMessage.setText(R.string.photo_authorized)
            if (!tonePlayed) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)
                tonePlayed = true
            }
        } else {
            isCameraEnabled = false
            tonePlayed = false
            userMessage.setBackgroundColor(Color.RED)
            if (thetaY > 0) {
                userMessage.setText(R.string.phone_up)
            } else {
                userMessage.setText(R.string.phone_down)
            }
        }
    }
    private fun getDirection(angle: Double): String {
        var direction = ""

        if (angle >= 350 || angle <= 10)
            direction = "N"
        if (angle < 350 && angle > 280)
            direction = "NW"
        if (angle <= 280 && angle > 260)
            direction = "W"
        if (angle <= 260 && angle > 190)
            direction = "SW"
        if (angle <= 190 && angle > 170)
            direction = "S"
        if (angle <= 170 && angle > 100)
            direction = "SE"
        if (angle <= 100 && angle > 80)
            direction = "E"
        if (angle <= 80 && angle > 10)
            direction = "NE"

        return direction
    }

    fun updateOrientationAngles() {
        //orientation[0] = Azimuth (rotation around the -ve z-axis)
        //orientation[1] = Pitch (rotation around the x-axis)
        //orientation[2] = Roll (rotation around the y-axis)

        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        val orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val degrees = (Math.toDegrees(orientation.get(0).toDouble()) + 360.0) % 360.0
        val angle = round(degrees * 100) / 100
        val direction = getDirection(degrees)
    }

    private fun getLineLocation(angle: Double): Int {
        val value = (-angle + 90.0) * 1.111
        return value.toInt()
    }

    override fun onAccuracyChanged(sensor: Sensor, i: Int) {}

    val phoneAngles: DoubleArray
        get() = doubleArrayOf(thetaX, thetaY)

    companion object {
        private const val TAG = "BubbleLevel"
        private const val GRAVITY = 9.81
        private const val MIN_DEGREE = -20.0
        private const val MAX_DEGREE = 20.0
    }

    init {
        mImageViewY = (ctx as Activity).findViewById<View>(R.id.iv) as ImageView
        mImageViewX = ctx.findViewById<View>(R.id.ihorizintal) as ImageView
        mlevelsquare = ctx.findViewById<View>(R.id.levelsquare) as ImageView

        userMessage =
            ctx.findViewById<View>(R.id.user_message) as TextView

        ssx = ctx.findViewById<View>(R.id.ssx) as TextView
        ssy = ctx.findViewById<View>(R.id.ssy) as TextView
        ssz = ctx.findViewById<View>(R.id.ssz) as TextView


        spitch = ctx.findViewById<View>(R.id.roll) as TextView
        sroll = ctx.findViewById<View>(R.id.pitch) as TextView
        syaw = ctx.findViewById<View>(R.id.yaw) as TextView

        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

        bitmapVertical = Bitmap.createBitmap(
            50,  // Width
            200,  // Height
            Bitmap.Config.ARGB_8888 // Config
        )

        bitmapHorizontal = Bitmap.createBitmap(
            200,  // Width
            50,  // Height
            Bitmap.Config.ARGB_8888 // Config
        )

        bitmapLevelSquare = Bitmap.createBitmap(
            300,  // Width
            300,  // Height
            Bitmap.Config.ARGB_8888 // Config
        )

        canvasY = Canvas(bitmapVertical)
        canvasY.drawColor(Color.LTGRAY)
        canvasX = Canvas(bitmapHorizontal)
        canvasLevelSquare = Canvas(bitmapLevelSquare)

        canvasY.drawColor(Color.LTGRAY)
        rectangleY = Rect(0, 0, canvasY.width, canvasY.height)
        rectangleX = Rect(0, 0, canvasX.width, canvasX.height)
        paintRectangle = Paint()
        paintRectangle.style = Paint.Style.FILL
        paintRectangle.color = Color.YELLOW
        paintRectangle.isAntiAlias = true
        paintLine = Paint()
        paintLine.style = Paint.Style.FILL
        paintLine.color = Color.BLACK
        paintLine.isAntiAlias = true
        paintLine.strokeWidth = 2f

        paintCorrect = Paint()
        paintCorrect.style = Paint.Style.FILL
        paintCorrect.color = Color.GREEN
        paintCorrect.isAntiAlias = true
        paintCorrect.strokeWidth = 3f

        paintWrong = Paint()
        paintWrong.style = Paint.Style.FILL
        paintWrong.color = Color.RED
        paintWrong.isAntiAlias = true
        paintWrong.strokeWidth = 3f



        isCameraEnabled = false
        tonePlayed = false
        thetaX = 0.0
        thetaY = 0.0
    }
}
