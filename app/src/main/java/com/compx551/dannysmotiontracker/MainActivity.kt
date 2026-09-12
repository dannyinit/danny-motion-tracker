package com.compx551.dannysmotiontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.compx551.dannysmotiontracker.ui.theme.DannysMotionTrackerTheme
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import java.nio.ByteBuffer

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private val motionProcessor = MotionProcessor()

    // Live UI states
    private var currentMetrics by mutableStateOf(MotionMetrics())
    
    // Sliders states
    private var uiAccelThreshold by mutableFloatStateOf(motionProcessor.accelVarianceThreshold)
    private var uiGyroThreshold by mutableFloatStateOf(motionProcessor.gyroMagnitudeThreshold)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DannysMotionTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MotionDashboard(
                        metrics = currentMetrics,
                        accelThreshold = uiAccelThreshold,
                        gyroThreshold = uiGyroThreshold,
                        onAccelThresholdChange = { 
                            uiAccelThreshold = it
                            motionProcessor.accelVarianceThreshold = it 
                        },
                        onGyroThresholdChange = { 
                            uiGyroThreshold = it
                            motionProcessor.gyroMagnitudeThreshold = it 
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/sensors") {
            val buffer = ByteBuffer.wrap(messageEvent.data)
            
            // Iterate through all samples in the batched message.
            while (buffer.hasRemaining()) {
                val type = buffer.get().toInt()
                val timestamp = buffer.long
                val x = buffer.float
                val y = buffer.float
                val z = buffer.float

                // Process each event independently as it arrives
                currentMetrics = motionProcessor.processEvent(
                    type = type,
                    timestampNs = timestamp,
                    x = x, y = y, z = z
                )
                // Update the respective state variables to trigger a UI recomposition.
            }
        }
    }
}

@Composable
fun MotionDashboard(
    metrics: MotionMetrics,
    accelThreshold: Float,
    gyroThreshold: Float,
    onAccelThresholdChange: (Float) -> Unit,
    onGyroThresholdChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Motion State", 
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        
        // Large State Display
        Surface(
            color = when(metrics.currentState) {
                MotionState.IDLE -> Color.LightGray
                MotionState.TWISTING -> Color(0xFF64B5F6) // Light Blue
                MotionState.ACTIVE -> Color(0xFF81C784) // Light Green
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = metrics.currentState.name,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics Display
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text("Live Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            MetricRow("Accel Mag (Smoothed):", metrics.smoothedAccelMagnitude)
            MetricRow("Gyro Mag (Smoothed):", metrics.smoothedGyroMagnitude)
            MetricRow("Accel Variance (1s):", metrics.accelVariance)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Developer Tuning Tools
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Developer Tuning", style = MaterialTheme.typography.titleSmall, color = Color.Gray)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Accel Variance Threshold: ${"%.1f".format(accelThreshold)}")
            Slider(
                value = accelThreshold,
                onValueChange = onAccelThresholdChange,
                valueRange = 0.5f..20f
            )

            Text("Gyro Mag Threshold: ${"%.1f".format(gyroThreshold)}")
            Slider(
                value = gyroThreshold,
                onValueChange = onGyroThresholdChange,
                valueRange = 0.5f..10f
            )
        }
    }
}

@Composable
fun MetricRow(label: String, value: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = String.format(java.util.Locale.US, "%.3f", value), 
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
