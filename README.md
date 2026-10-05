# Wear OS Motion Tracker

A two-device Android system that tracks wrist motion in real time. A Wear OS app streams accelerometer and gyroscope data to a companion phone app. The phone app processes the data, classifies the motion as **Idle**, **Twisting** or **Active**, and shows the results on a live dashboard.

Built for COMPX551 - Mobile and Wearable Computing.

## Features

**Watch app (`weartracker`)**

- Shows live accelerometer and gyroscope readings (x, y, z).
- Sends sensor samples to the phone in batches every 200 ms.
- Shows whether the phone is connected and finds the phone again if the connection drops.
- Registers sensors only while the app is open to save battery.

**Phone app (`app`)**

- Receives sensor batches over the Wearable Data Layer API.
- Smooths sensor readings and classifies motion in real time.
- Shows a live dashboard with raw readings, processed metrics and meters that compare each metric with its threshold.
- Shows a timeline of motion states for the last 30 seconds. The history is kept when the screen rotates.

## How it works

```
Watch                                         Phone
─────                                         ─────
Accelerometer ─┐                              MessageClient listener
Gyroscope ─────┴─► buffer ─► binary batch ──► MotionViewModel
                   (every 200 ms, "/sensors")   └─► MotionProcessor
                                                     ├─ magnitude
                                                     ├─ EMA smoothing
                                                     ├─ 1 s variance window
                                                     └─ classification ─► dashboard
```

### Data transfer

The watch packs each sensor sample into a 21-byte binary record and sends the whole batch as one message on the `/sensors` path:

| Field                                          | Type  | Size  |
| ---------------------------------------------- | ----- | ----- |
| Sensor type (0 = accelerometer, 1 = gyroscope) | byte  | 1     |
| Timestamp (ns)                                 | long  | 8     |
| x, y, z                                        | float | 3 × 4 |

### Motion processing

[`MotionProcessor`](app/src/main/java/com/compx551/dannysmotiontracker/MotionProcessor.kt) does the following for each sample:

1. **Magnitude:** computes `√(x² + y² + z²)`, so the result does not depend on how the watch is oriented.
2. **Smoothing:** applies an exponential moving average (α = 0.1) to reduce noise.
3. **Variance:** computes the variance of the acceleration magnitude over a 1-second sliding window, based on sample timestamps.
4. **Classification:**

| State    | Rule                                     | Meaning                                           |
| -------- | ---------------------------------------- | ------------------------------------------------- |
| Active   | acceleration variance > 0.8              | The arm is moving through space.                  |
| Twisting | smoothed gyroscope magnitude > 2.5 rad/s | The wrist is rotating but the arm stays in place. |
| Idle     | neither of the above                     | The wrist is at rest.                             |

Each batch is summarised by its highest-priority state (Active > Twisting > Idle). The summary is added to a 150-entry history, which covers about 30 seconds at 5 batches per second.

## Tech stack

- Kotlin
- Jetpack Compose (Material 3 on the phone, Wear Compose on the watch)
- Wearable Data Layer API (`MessageClient`, `NodeClient`)
- Android Sensor framework
- AndroidX ViewModel and Kotlin coroutines

## Project structure

```
app/          Phone app: MainActivity, MotionViewModel, MotionProcessor
weartracker/  Wear OS app: WearMainActivity
```

## Running the project

**Requirements:** Android Studio, an Android phone or emulator (API 30 or later), and a Wear OS watch or emulator paired with the phone.

1. Clone the repository and open it in Android Studio.
2. Run the `app` configuration on the phone.
3. Run the `weartracker` configuration on the watch.
4. Open both apps. The watch shows "Searching..." until it finds the phone, then "Connected • Sent: N", and the phone dashboard starts updating.

Both apps use the same application ID (`com.compx551.dannysmotiontracker`), which the Wearable Data Layer requires before the two apps can exchange messages.
