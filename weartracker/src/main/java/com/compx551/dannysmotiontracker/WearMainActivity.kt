package com.compx551.dannysmotiontracker

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import java.util.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.compx551.dannysmotiontracker.ui.theme.DannysMotionTrackerTheme
import com.compx551.dannysmotiontracker.ui.theme.SensorLabelColor
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.nio.ByteBuffer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

data class SensorData(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

/**
 * Represents a single sensor reading to be sent to the phone.
 */
data class SensorSample(val type: Int, val timestamp: Long, val x: Float, val y: Float, val z: Float)

class WearMainActivity : ComponentActivity(), SensorEventListener {

    // Lazy initialization ensures system services are accessed only after the Activity is created.
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val accelerometer: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private val gyroscope: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }

    // Live UI states
    private var accelData by mutableStateOf(SensorData())
    private var gyroData by mutableStateOf(SensorData())
    private var hasAccel by mutableStateOf(false)
    private var hasGyro by mutableStateOf(false)

    // Data layer connection states
    private var phoneConnected by mutableStateOf(false)
    private var batchCount by mutableStateOf(0)
    private var targetNodeId: String? = null

    // Buffer for batching high-frequency sensor readings
    private val sensorBuffer = mutableListOf<SensorSample>()
    private var batchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DannysMotionTrackerTheme {
                AppScaffold {
                    ScreenScaffold {
                        SensorDashboard(
                            accelData = accelData,
                            gyroData = gyroData,
                            hasAccel = hasAccel,
                            hasGyro = hasGyro,
                            phoneConnected = phoneConnected,
                            batchCount = batchCount
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccel = accelerometer != null
        hasGyro = gyroscope != null

        // Register sensors only while the app is in the foreground to save battery.
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        
        startBatching()
    }

    override fun onPause() {
        super.onPause()
        // Unregister listeners immediately when leaving the app.
        sensorManager.unregisterListener(this)
        stopBatching()
        
        // Clear buffers and reset connection states to ensure a fresh start on resume.
        synchronized(sensorBuffer) {
            sensorBuffer.clear()
        }
        targetNodeId = null
        phoneConnected = false
        batchCount = 0
    }

    /**
     * Starts a coroutine loop that bundles and sends data to the phone every 200ms.
     */
    private fun startBatching() {
        batchJob = lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(200L)
                
                // Self-healing: Automatically look for the phone if not connected.
                if (targetNodeId == null) {
                    try {
                        val nodes = Tasks.await(Wearable.getNodeClient(this@WearMainActivity).connectedNodes)
                        targetNodeId = nodes.firstOrNull()?.id
                        phoneConnected = targetNodeId != null
                    } catch (_: Exception) {
                        targetNodeId = null
                        phoneConnected = false
                    }
                }
                
                targetNodeId?.let { sendBatchedData(it) }
            }
        }
    }

    private fun stopBatching() {
        batchJob?.cancel()
        batchJob = null
    }

    /**
     * Packages all readings currently in the buffer into a compact binary message.
     */
    private fun sendBatchedData(nodeId: String) {
        val samples = synchronized(sensorBuffer) {
            if (sensorBuffer.isEmpty()) return
            val list = sensorBuffer.toList()
            sensorBuffer.clear()
            list
        }

        try {
            // Binary format: [Type(1 byte)] [Timestamp(8 bytes)] [X(4)] [Y(4)] [Z(4)] = 21 bytes per sample.
            val buffer = ByteBuffer.allocate(samples.size * 21)
            for (sample in samples) {
                buffer.put(sample.type.toByte())
                buffer.putLong(sample.timestamp)
                buffer.putFloat(sample.x)
                buffer.putFloat(sample.y)
                buffer.putFloat(sample.z)
            }
            
            // Blocking wait for the task to ensure we detect hardware disconnections via exceptions.
            Tasks.await(
                Wearable.getMessageClient(this).sendMessage(
                    nodeId,
                    "/sensors",
                    buffer.array()
                )
            )
            batchCount++
            phoneConnected = true
        } catch (e: Exception) {
            // Reset connection on failure so the self-healing loop can retry.
            targetNodeId = null
            phoneConnected = false
            batchCount = 0
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        // Convert raw event values into an immutable sample for the buffer.
        val sample = SensorSample(
            type = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) 0 else 1,
            timestamp = event.timestamp,
            x = event.values[0],
            y = event.values[1],
            z = event.values[2]
        )
        
        synchronized(sensorBuffer) {
            sensorBuffer.add(sample)
        }

        // Update local UI states for the watch display.
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
    phoneConnected: Boolean,
    batchCount: Int,
    modifier: Modifier = Modifier
) {
    val columnState = rememberTransformingLazyColumnState()

    // TransformingLazyColumn makes the Wear OS screen scrollable and adaptable to additional content.
    TransformingLazyColumn(
        state = columnState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = "Motion Tracker",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Accelerometer Section
        item {
            Text(
                text = "Accelerometer",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            if (hasAccel) {
                SensorDataRow(accelData)
            } else {
                Text(text = "Sensor Not Available", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Gyroscope Section
        item {
            Text(
                text = "Gyroscope",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item {
            if (hasGyro) {
                SensorDataRow(gyroData)
            } else {
                Text(text = "Sensor Not Available", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Connection Feedback Footer
        item {
            Text(
                text = if (phoneConnected) "Connected • Sent: $batchCount" else "Searching...",
                style = MaterialTheme.typography.labelSmall,
                color = if (phoneConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun SensorDataRow(data: SensorData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SensorValueText(label = "X", value = data.x, modifier = Modifier.weight(1f))
        SensorValueText(label = "Y", value = data.y, modifier = Modifier.weight(1f))
        SensorValueText(label = "Z", value = data.z, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SensorValueText(label: String, value: Float, modifier: Modifier = Modifier) {
    val annotatedString = buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                color = SensorLabelColor,
                fontWeight = FontWeight.ExtraBold
            )
        ) {
            append("$label:")
        }
        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
            append(String.format(Locale.US, "%.1f", value))
        }
    }

    Text(
        text = annotatedString,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun SensorDashboardPreview() {
    DannysMotionTrackerTheme {
        AppScaffold {
            ScreenScaffold {
                SensorDashboard(
                    accelData = SensorData(9.81f, 0.05f, -0.12f),
                    gyroData = SensorData(0.01f, -0.02f, 0.03f),
                    hasAccel = true,
                    hasGyro = true,
                    phoneConnected = true,
                    batchCount = 42
                )
            }
        }
    }
}
