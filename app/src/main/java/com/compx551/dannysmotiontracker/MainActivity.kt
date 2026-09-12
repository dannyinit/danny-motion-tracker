package com.compx551.dannysmotiontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    
    // State History Timeline (Max 150 items for ~30 seconds of history)
    private val stateHistory = mutableStateListOf<MotionState>()
    
    // Raw Sensor States
    private var accelX by mutableStateOf(0f)
    private var accelY by mutableStateOf(0f)
    private var accelZ by mutableStateOf(0f)
    private var gyroX by mutableStateOf(0f)
    private var gyroY by mutableStateOf(0f)
    private var gyroZ by mutableStateOf(0f)
    
    // Sliders states are no longer needed
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DannysMotionTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MotionDashboard(
                        metrics = currentMetrics,
                        accelX = accelX, accelY = accelY, accelZ = accelZ,
                        gyroX = gyroX, gyroY = gyroY, gyroZ = gyroZ,
                        history = stateHistory,
                        accelThreshold = motionProcessor.accelVarianceThreshold,
                        gyroThreshold = motionProcessor.gyroMagnitudeThreshold,
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
            // Dominant state for the current 200ms batch
            var batchDominantState = MotionState.IDLE

            // Each sensor record is exactly 21 bytes.
            // Only proceed if there is enough data for a complete record to avoid crashes.
            while (buffer.remaining() >= 21) {
                val type = buffer.get().toInt()
                val timestamp = buffer.long
                val x = buffer.float
                val y = buffer.float
                val z = buffer.float

                if (type == 0) {
                    accelX = x; accelY = y; accelZ = z
                } else if (type == 1) {
                    gyroX = x; gyroY = y; gyroZ = z
                }

                // Process each event independently as it arrives
                val metrics = motionProcessor.processEvent(
                    type = type,
                    timestampNs = timestamp,
                    x = x, y = y, z = z
                )
                currentMetrics = metrics

                // Aggregation: Track the highest priority state seen in this batch
                // Priority: ACTIVE (2) > TWISTING (1) > IDLE (0)
                if (metrics.currentState.ordinal > batchDominantState.ordinal) {
                    batchDominantState = metrics.currentState
                }
            }

            // Append the batch's dominant state to history
            stateHistory.add(batchDominantState)
            if (stateHistory.size > 150) {
                stateHistory.removeAt(0)
            }
        }
    }
}

@Composable
fun MotionDashboard(
    metrics: MotionMetrics,
    accelX: Float, accelY: Float, accelZ: Float,
    gyroX: Float, gyroY: Float, gyroZ: Float,
    history: List<MotionState>,
    accelThreshold: Float,
    gyroThreshold: Float,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Danny's Motion Tracker", 
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // State Display Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Current State", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = when(metrics.currentState) {
                        MotionState.IDLE -> Color.LightGray
                        MotionState.TWISTING -> Color(0xFF64B5F6) // Light Blue
                        MotionState.ACTIVE -> Color(0xFF81C784) // Light Green
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = metrics.currentState.name,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // State History Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("State History (30s)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                StateHistoryTimeline(history = history)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("Live Visualisations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Accel Variance", style = MaterialTheme.typography.labelLarge)
                ThresholdMetricBar(
                    value = metrics.accelVariance,
                    threshold = accelThreshold,
                    activeColor = Color(0xFF81C784)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Gyro Magnitude", style = MaterialTheme.typography.labelLarge)
                ThresholdMetricBar(
                    value = metrics.smoothedGyroMagnitude,
                    threshold = gyroThreshold,
                    activeColor = Color(0xFF64B5F6)
                )

                Spacer(modifier = Modifier.height(24.dp))
                Text("Numeric Metrics", style = MaterialTheme.typography.titleSmall, color = Color.Gray)
                MetricRow("Accel Variance (1s):", metrics.accelVariance)
                MetricRow("Gyro Mag (Smoothed):", metrics.smoothedGyroMagnitude)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Raw Sensor Data Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("Raw Sensor Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Accelerometer", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("X: ") }
                        append(String.format(java.util.Locale.US, "%.2f  ", accelX))
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Y: ") }
                        append(String.format(java.util.Locale.US, "%.2f  ", accelY))
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Z: ") }
                        append(String.format(java.util.Locale.US, "%.2f", accelZ))
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Gyroscope", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("X: ") }
                        append(String.format(java.util.Locale.US, "%.2f  ", gyroX))
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Y: ") }
                        append(String.format(java.util.Locale.US, "%.2f  ", gyroY))
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Z: ") }
                        append(String.format(java.util.Locale.US, "%.2f", gyroZ))
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ThresholdMetricBar(
    value: Float,
    threshold: Float,
    activeColor: Color
) {
    // Threshold is at 50% width.
    val progress = (value / (threshold * 2)).coerceIn(0f, 1f)
    val isTriggered = value >= threshold

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .drawBehind {
                    // Draw background track
                    drawRect(color = Color.LightGray.copy(alpha = 0.3f), size = size)
                    
                    // Draw fill
                    drawRect(
                        color = if (isTriggered) activeColor else Color.Gray,
                        size = Size(width = size.width * progress, height = size.height)
                    )
                    
                    // Draw threshold line (center)
                    drawLine(
                        color = Color.Black,
                        start = Offset(x = size.width / 2, y = 0f),
                        end = Offset(x = size.width / 2, y = size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
        )
    }
}

@Composable
fun StateHistoryTimeline(history: List<MotionState>) {
    val listState = rememberLazyListState()
    
    // Automatically scroll to the end when new items are added
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(history) { state ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        drawRect(
                            color = when (state) {
                                MotionState.IDLE -> Color.LightGray
                                MotionState.TWISTING -> Color(0xFF64B5F6)
                                MotionState.ACTIVE -> Color(0xFF81C784)
                            }
                        )
                    }
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
