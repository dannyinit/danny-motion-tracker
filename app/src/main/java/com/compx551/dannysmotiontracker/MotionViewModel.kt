package com.compx551.dannysmotiontracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.nio.ByteBuffer

/**
 * ViewModel responsible for persisting sensor data and motion history across configuration changes.
 */
class MotionViewModel : ViewModel() {

    private val motionProcessor = MotionProcessor()

    // Current processed metrics (magnitude, variance, state)
    var currentMetrics by mutableStateOf(MotionMetrics())
        private set

    // Sliding window of motion states for the history timeline
    val stateHistory = mutableStateListOf<MotionState>()
    
    // Explicit counter to trigger UI updates even when history size remains constant
    var historyUpdateCounter by mutableIntStateOf(0)
        private set

    // Latest raw sensor readings received from the watch
    var accelX by mutableStateOf(0f)
        private set
    var accelY by mutableStateOf(0f)
        private set
    var accelZ by mutableStateOf(0f)
        private set
    var gyroX by mutableStateOf(0f)
        private set
    var gyroY by mutableStateOf(0f)
        private set
    var gyroZ by mutableStateOf(0f)
        private set

    val accelThreshold = motionProcessor.accelVarianceThreshold
    val gyroThreshold = motionProcessor.gyroMagnitudeThreshold

    /**
     * Parses a batch of sensor samples and updates the state.
     * Identifies the highest priority state in the batch for historical logging.
     */
    fun processSensorBatch(data: ByteArray) {
        val buffer = ByteBuffer.wrap(data)

        // Track the highest priority state encountered in this specific 200ms batch
        var batchDominantState = MotionState.IDLE

        // Iterate through all 21-byte sensor records in the message
        while (buffer.remaining() >= 21) {
            val type = buffer.get().toInt()
            val timestamp = buffer.long
            val x = buffer.float
            val y = buffer.float
            val z = buffer.float

            if (type == 0) { // Accelerometer
                accelX = x; accelY = y; accelZ = z
            } else if (type == 1) { // Gyroscope
                gyroX = x; gyroY = y; gyroZ = z
            }

            // Update processing metrics and state classification
            val metrics = motionProcessor.processEvent(
                type = type,
                timestampNs = timestamp,
                x = x, y = y, z = z
            )
            currentMetrics = metrics

            // Priority aggregation: ACTIVE > TWISTING > IDLE
            if (metrics.currentState.ordinal > batchDominantState.ordinal) {
                batchDominantState = metrics.currentState
            }
        }

        // Record the batch summary in history
        stateHistory.add(batchDominantState)
        
        // Maintain a sliding window of ~30 seconds (150 batches at 5Hz)
        if (stateHistory.size > 150) {
            stateHistory.removeAt(0)
        }

        // Increment counter to signal an update to the UI
        historyUpdateCounter++
    }
}
