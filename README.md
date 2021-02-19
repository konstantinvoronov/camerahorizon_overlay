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
 *
 */
