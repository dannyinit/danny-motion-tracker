package com.compx551.dannysmotiontracker

import kotlin.math.sqrt

enum class MotionState {
    IDLE, TWISTING, ACTIVE
}

data class MotionMetrics(
    val accelMagnitude: Float = 0f,
    val gyroMagnitude: Float = 0f,
    val smoothedAccelMagnitude: Float = 0f,
    val smoothedGyroMagnitude: Float = 0f,
    val accelVariance: Float = 0f,
    val currentState: MotionState = MotionState.IDLE
)

class MotionProcessor {

    // Tuning Parameters (can be adjusted live via UI)
    var accelVarianceThreshold: Float = 2.0f
    var gyroMagnitudeThreshold: Float = 1.5f
    
    // EMA Alpha (Lower = heavier smoothing)
    var emaAlpha: Float = 0.1f

    // Sliding Window (Time-based, 1 second = 1,000,000,000 nanoseconds)
    private val windowDurationNs: Long = 1_000_000_000L
    private val accelMagnitudeWindow = mutableListOf<Pair<Long, Float>>()

    // State Variables for Independent Processing
    private var lastSmoothedAccel: Float? = null
    private var lastSmoothedGyro: Float? = null
    private var lastAccelVariance: Float = 0f
    private var lastAccelMag: Float = 0f
    private var lastGyroMag: Float = 0f

    fun processEvent(
        type: Int, timestampNs: Long,
        x: Float, y: Float, z: Float
    ): MotionMetrics {

        if (type == 0) { // Accelerometer
            // 1. Calculate Magnitude
            lastAccelMag = sqrt(x * x + y * y + z * z)

            // 2. Calculate EMA (Exponential Moving Average)
            val smoothedAccel = if (lastSmoothedAccel == null) {
                lastAccelMag
            } else {
                (emaAlpha * lastAccelMag) + ((1 - emaAlpha) * lastSmoothedAccel!!)
            }
            lastSmoothedAccel = smoothedAccel

            // 3. Sliding Window Management (Remove old samples)
            val cutoffTime = timestampNs - windowDurationNs
            accelMagnitudeWindow.removeAll { it.first < cutoffTime }
            
            // Add new sample to window
            accelMagnitudeWindow.add(Pair(timestampNs, smoothedAccel))

            // 4. Calculate Variance
            lastAccelVariance = calculateVariance(accelMagnitudeWindow.map { it.second })
            
        } else if (type == 1) { // Gyroscope
            // 1. Calculate Magnitude
            lastGyroMag = sqrt(x * x + y * y + z * z)

            // 2. Calculate EMA
            val smoothedGyro = if (lastSmoothedGyro == null) {
                lastGyroMag
            } else {
                (emaAlpha * lastGyroMag) + ((1 - emaAlpha) * lastSmoothedGyro!!)
            }
            lastSmoothedGyro = smoothedGyro
        }

        // 5. Classification Logic
        // We use the most recently calculated variance and smoothed gyro for classification
        val state = classifyMotion(lastAccelVariance, lastSmoothedGyro ?: 0f)

        return MotionMetrics(
            accelMagnitude = lastAccelMag,
            gyroMagnitude = lastGyroMag,
            smoothedAccelMagnitude = lastSmoothedAccel ?: 0f,
            smoothedGyroMagnitude = lastSmoothedGyro ?: 0f,
            accelVariance = lastAccelVariance,
            currentState = state
        )
    }

    private fun calculateVariance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val sumSquaredDifferences = values.sumOf { 
            val diff = it - mean
            (diff * diff).toDouble()
        }
        return (sumSquaredDifferences / values.size).toFloat()
    }

    private fun classifyMotion(accelVariance: Float, smoothedGyro: Float): MotionState {
        return when {
            // Is the arm moving forcefully through space? (Ignore gyro)
            accelVariance > accelVarianceThreshold -> MotionState.ACTIVE
            
            // Arm isn't moving wildly, but is the wrist rotating?
            smoothedGyro > gyroMagnitudeThreshold -> MotionState.TWISTING
            
            // Neither -> Resting
            else -> MotionState.IDLE
        }
    }
}
