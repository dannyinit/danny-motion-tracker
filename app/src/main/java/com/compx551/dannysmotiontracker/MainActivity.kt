package com.compx551.dannysmotiontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.compx551.dannysmotiontracker.ui.theme.DannysMotionTrackerTheme
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import java.nio.ByteBuffer

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    // Live UI states for the latest sensor readings received from the watch.
    private var accelX by mutableStateOf(0f)
    private var accelY by mutableStateOf(0f)
    private var accelZ by mutableStateOf(0f)
    private var gyroX by mutableStateOf(0f)
    private var gyroY by mutableStateOf(0f)
    private var gyroZ by mutableStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DannysMotionTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SensorDisplay(
                        accelX, accelY, accelZ,
                        gyroX, gyroY, gyroZ,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register the message listener only while the app is in the foreground.
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        // Unregister the listener to prevent unnecessary background processing.
        Wearable.getMessageClient(this).removeListener(this)
    }

    /**
     * Callback triggered when a message arrives from the watch via the Wear OS Data Layer.
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        // Verify the message path matches our expected sensor stream.
        if (messageEvent.path == "/sensors") {
            val buffer = ByteBuffer.wrap(messageEvent.data)
            
            // Iterate through all samples in the batched message.
            while (buffer.hasRemaining()) {
                val type = buffer.get().toInt()
                val timestamp = buffer.long // Extracted but not used in current UI display
                val x = buffer.float
                val y = buffer.float
                val z = buffer.float

                // Update the respective state variables to trigger a UI recomposition.
                if (type == 0) { // Accelerometer
                    accelX = x
                    accelY = y
                    accelZ = z
                } else if (type == 1) { // Gyroscope
                    gyroX = x
                    gyroY = y
                    gyroZ = z
                }
            }
        }
    }
}

@Composable
fun SensorDisplay(
    ax: Float, ay: Float, az: Float,
    gx: Float, gy: Float, gz: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Danny's Motion Tracker", style = MaterialTheme.typography.headlineMedium)
        
        Column(
            modifier = Modifier.padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Accelerometer", fontWeight = FontWeight.Bold)
            Text(text = "X: ${"%.2f".format(ax)}, Y: ${"%.2f".format(ay)}, Z: ${"%.2f".format(az)}")
        }

        Column(
            modifier = Modifier.padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Gyroscope", fontWeight = FontWeight.Bold)
            Text(text = "X: ${"%.2f".format(gx)}, Y: ${"%.2f".format(gy)}, Z: ${"%.2f".format(gz)}")
        }
    }
}
