# Skip Window Implementation

## The Problem

The permission onboarding screen (shown when the app lacks full-screen notification permissions) has a "Skip for Now" button. The intent: don't show the screen again for 24 hours after the user taps Skip.

### Why a Boolean Flag Fails

The naive implementation:

```kotlin
// ❌ This doesn't work reliably
prefs.edit().putBoolean("permission_skipped", true).apply()
```

**Failure scenario:**

1. User opens app, taps "Skip for Now"
2. Flag is set to `true`
3. User navigates deeper into the app
4. Android kills the process (low memory, background restriction)
5. User taps the app icon → `MainActivity` relaunches with `FLAG_ACTIVITY_CLEAR_TASK`
6. On `onCreate`, the permission check runs: reads `"permission_skipped"` → `true`
7. Works fine... until the app reads this flag somewhere with `getBoolean("permission_skipped", false)` and then clears it
8. Or: the 24 hours was supposed to expire, but the boolean has no expiry — it's permanent until cleared

The boolean approach has no time dimension. There is no way to determine whether "skipped 2 minutes ago" or "skipped 3 weeks ago" without adding a second field — at which point you've reinvented a timestamp.

---

## Solution: Timestamp-Based Window

Store the moment the skip was initiated, not a flag. On each check, compute whether the elapsed time is within the window.

```kotlin
// Generic pattern — not production code
class SkipWindowManager(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_SKIP_START = "skip_window_start"
        private const val WINDOW_MS = 24 * 60 * 60 * 1000L  // 24 hours
    }

    /** Call when user taps "Skip for Now" */
    fun markSkipped() {
        prefs.edit()
            .putLong(KEY_SKIP_START, System.currentTimeMillis())
            .apply()
    }

    /** Returns true if we're still within the 24-hour window */
    fun isWithinWindow(): Boolean {
        val start = prefs.getLong(KEY_SKIP_START, 0L)
        if (start == 0L) return false
        return (System.currentTimeMillis() - start) < WINDOW_MS
    }

    /** Call when user grants permissions — clears the window */
    fun clearWindow() {
        prefs.edit().remove(KEY_SKIP_START).apply()
    }
}
```

---

## Why This Works

| Property | Boolean Flag | Timestamp Window |
|----------|-------------|-----------------|
| Survives process kill | ✅ | ✅ |
| Survives `CLEAR_TASK` restart | ✅ | ✅ |
| Expires automatically | ❌ | ✅ (after 24h) |
| Multiple reads without mutation | ❌ (if cleared on read) | ✅ |
| Survives Activity recreation | ✅ | ✅ |
| Encodes elapsed time | ❌ | ✅ |

---

## Integration in the Permission Check

```kotlin
// Generic pattern
fun shouldShowPermissionScreen(context: Context): Boolean {
    val permissionsGranted = PermissionHelper.hasAllRequiredPermissions(context)
    if (permissionsGranted) return false

    val skipManager = SkipWindowManager(getSharedPreferences(...))
    if (skipManager.isWithinWindow()) return false

    return true  // Missing permissions AND not within skip window
}
```

---

## Sequence Diagram

```
User taps "Skip for Now"
         │
         ▼
  markSkipped()
  prefs: skip_window_start = 1700000000000L
         │
         ▼
  [Process killed by OS]
         │
         ▼
  User opens app again
         │
         ▼
  isWithinWindow()?
  now - 1700000000000L < 86400000?
         │
         ├── YES (< 24h) ──▶ Skip permission screen, show main app
         │
         └── NO  (> 24h) ──▶ Show permission screen again
```

---

## Edge Cases

| Scenario | Behavior |
|----------|----------|
| User grants permissions during window | `clearWindow()` called — screen never shown again |
| Device clock rolled back | Window extends unexpectedly (acceptable; no security risk) |
| First launch (no timestamp) | `prefs.getLong(key, 0L)` returns 0, `isWithinWindow()` returns false → show screen |
| User skips, kills app, reopens immediately | Within window → screen skipped correctly |
| 25 hours after skip | Window expired → permission screen shown again |
