# Conflict Resolution: Dual Notification Audio

## The Problem

The system schedules two independent types of notifications — standard reminders and full-screen alerts — both of which can be triggered for the same prayer at overlapping times.

When both fire simultaneously:
- The full-screen alert plays alarm audio (looping, bypasses silent mode)
- The standard reminder also attempts to play notification audio
- Result: two audio streams playing on top of each other, jarring and unprofessional

---

## Why This Happens

Both notification types are scheduled independently from the same API response. The API returns:

- A `standard_reminders` list — e.g., "remind 10 minutes before Fajr"
- A `full_screen_alerts` list — e.g., "full-screen alert 0 minutes before Fajr iqamah"

Because iqamah times may equal or closely follow prayer times, the two systems can share the same clock minute.

---

## Solution: Set-Based Conflict Detection

### Algorithm

```kotlin
// Generic pattern — not production code
fun scheduleAllReminders(
    fullScreenAlerts: List<Alert>,
    standardReminders: List<Reminder>
) {
    // Phase 1: Collect all minute-slots occupied by full-screen alerts
    val occupiedSlots: Set<String> = fullScreenAlerts
        .map { formatToHourMinute(it.triggerTimeMillis) }
        .toSet()

    // Phase 2: Schedule standard reminders, suppressing audio on conflicts
    standardReminders.forEach { reminder ->
        val slotKey = formatToHourMinute(reminder.triggerTimeMillis)
        val isConflict = slotKey in occupiedSlots

        scheduleStandardReminder(
            reminder = reminder,
            playAudio = !isConflict  // ← Audio disabled when conflict detected
        )
    }

    // Phase 3: Full-screen alerts always play alarm audio
    fullScreenAlerts.forEach { alert ->
        scheduleFullScreenAlert(alert)
    }
}

fun formatToHourMinute(timeMillis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
```

### Why HH:mm Granularity?

Exact millisecond equality is unreliable here because:

1. Full-screen alert trigger times are calculated from prayer time milliseconds minus an offset
2. Standard reminder trigger times may come from a differently-formatted time string parsed independently
3. Sub-second differences between two representations of "the same minute" would cause the set lookup to miss

Rounding to `HH:mm` matches human perception ("these fire at the same time") and eliminates false negatives.

---

## What the Standard Worker Does With the Flag

The `playAudio` boolean is passed as WorkManager input data and read inside the worker:

```kotlin
// Generic pattern
override fun doWork(): Result {
    val playAudio = inputData.getBoolean("playAudio", true)

    showNotification()

    if (playAudio) {
        AudioAlertManager.playPrayerAlert(applicationContext, prayerName = prayerName)
    }
    // If playAudio = false, notification still shows — just silently

    return Result.success()
}
```

The notification itself is always shown. Only the audio is suppressed. This preserves the reminder in the notification shade even when audio is off.

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| Full-screen fires, standard doesn't | Standard was past-due and skipped in validation |
| Standard fires, full-screen doesn't | Full-screen was past-due; standard plays audio normally |
| Both fire at same minute | Standard shows silently; full-screen plays alarm audio |
| Same prayer, different reminder IDs | Each gets its own work name — no collision |
| Two prayers in same minute (rare) | Both full-screen alerts play; standard reminders for that minute all suppressed |

---

## Result

- No overlapping audio
- Full-screen alarm audio takes priority (louder, more urgent)
- Standard notification remains visible in the shade
- Zero user-visible glitches at prayer times with back-to-back reminders
