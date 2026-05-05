# Audio Stream Design Decision

## Why Two Separate Audio Managers?

The app delivers two types of notifications with fundamentally different user-facing contracts:

| Type | User Expectation | Required Behavior |
|------|-----------------|-------------------|
| Standard reminder | "Notify me, but respect my phone settings" | Silent if device is muted |
| Full-screen alert | "Wake me up no matter what" | Play even in silent mode |

A single audio manager cannot satisfy both contracts simultaneously, so two separate managers with different `AudioAttributes` configurations were built.

---

## AudioAttributes Configuration

### Standard Notifications — `USAGE_NOTIFICATION_EVENT`

```kotlin
// Standard notification audio — respects silent mode
AudioAttributes.Builder()
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
    .build()
```

**Behavior:**
- Obeys the device's ringer mode (silent, vibrate, normal)
- Routed through the notification volume channel
- Appropriate for pre-prayer reminders the user may want to silence

### Full-Screen Alerts — `USAGE_ALARM`

```kotlin
// Alarm audio — bypasses silent mode
AudioAttributes.Builder()
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .setUsage(AudioAttributes.USAGE_ALARM)
    .build()
```

**Behavior:**
- Plays at alarm volume regardless of ringer mode
- Will play even when device is set to silent
- Loops continuously until the user dismisses the alert
- Equivalent to an alarm clock app — intentional by design

---

## Audio Focus Strategy

Both managers request `AUDIOFOCUS_GAIN_TRANSIENT` — the app needs audio temporarily and is willing to give it back.

### Android 8+ (API 26+) — `AudioFocusRequest`

```kotlin
val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    .setAudioAttributes(audioAttributes)
    .build()
audioManager.requestAudioFocus(focusRequest)
```

### Below Android 8 — Legacy API

```kotlin
@Suppress("DEPRECATION")
audioManager.requestAudioFocus(
    null,
    AudioManager.STREAM_ALARM, // or STREAM_NOTIFICATION
    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
)
```

---

## Silent Mode Detection (Standard Notifications Only)

Before playing standard notification audio, the ringer mode is checked:

```kotlin
// Generic pattern
val ringerMode = audioManager.ringerMode
if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
    // Skip audio, fire callback immediately
    onComplete?.invoke()
    return
}
```

This check is intentionally absent from the full-screen alarm manager — alarm audio is always played.

---

## MediaPlayer Lifecycle

Both managers follow the same lifecycle:

```
stopAudio()           ← Always stop/release any existing instance first
     │
     ▼
MediaPlayer.create()
     │
     ▼
setAudioAttributes()
     │
     ▼
setDataSource()
     │
     ▼
prepare()
     │
     ▼
start()
     │
     ▼
onCompletion / onError
     │
     ▼
release() + releaseAudioFocus()
```

Releasing before creating prevents resource leaks when the worker fires again before the previous audio finishes (e.g., two prayers close together).

---

## 10 Configurable Audio Clips

The API response includes an `audio_id` field per reminder. The audio manager maps this integer to a raw resource file at runtime:

```kotlin
// Generic pattern
fun resolveAudioResource(context: Context, audioId: Int): Int? {
    val resourceName = "prayer_sound_$audioId"
    val resId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
    return if (resId != 0) resId else null
}
```

This allows the server to change which sound plays without an app update.
