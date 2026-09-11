package com.compx551.dannysmotiontracker

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.compx551.dannysmotiontracker.ui.theme.DannysMotionTrackerTheme

data class SensorData(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

class WearMainActivity : ComponentActivity(), SensorEventListener {

    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val accelerometer: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private val gyroscope: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }

    private var accelData by mutableStateOf(SensorData())
    private var gyroData by mutableStateOf(SensorData())
    private var hasAccel by mutableStateOf(true)
    private var hasGyro by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DannysMotionTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SensorDashboard(
                        accelData = accelData,
                        gyroData = gyroData,
                        hasAccel = hasAccel,
                        hasGyro = hasGyro,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccel = accelerometer != null
        hasGyro = gyroscope != null

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelData = SensorData(event.values[0], event.values[1], event.values[2])
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroData = SensorData(event.values[0], event.values[1], event.values[2])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for current scope
    }
}

@Composable
fun SensorDashboard(
    accelData: SensorData,
    gyroData: SensorData,
    hasAccel: Boolean,
    hasGyro: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            // Padding added to ensure content doesn't clip immediately on round screens
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Danny's Motion Tracker",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Accelerometer",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (hasAccel) {
            Text(text = "X: ${"%.2f".format(accelData.x)}")
            Text(text = "Y: ${"%.2f".format(accelData.y)}")
            Text(text = "Z: ${"%.2f".format(accelData.z)}")
        } else {
            Text(text = "Sensor Not Available")
        }

        Text(
            text = "Gyroscope",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        if (hasGyro) {
            Text(text = "X: ${"%.2f".format(gyroData.x)}")
            Text(text = "Y: ${"%.2f".format(gyroData.y)}")
            Text(text = "Z: ${"%.2f".format(gyroData.z)}")
        } else {
            Text(text = "Sensor Not Available")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SensorDashboardPreview() {
    DannysMotionTrackerTheme {
        SensorDashboard(
            accelData = SensorData(9.81f, 0.05f, -0.12f),
            gyroData = SensorData(0.01f, -0.02f, 0.03f),
            hasAccel = true,
            hasGyro = true
        )
    }
}