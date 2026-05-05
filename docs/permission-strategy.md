# Permission Strategy

## The Problem

Full-screen notification alerts — the kind that wake the device and display over the lock screen — require different permissions on every major Android version released between 2020 and 2024. A single permission check is insufficient; the code must branch on `Build.VERSION.SDK_INT` for each version.

---

## Android Version Permission Matrix

| Android | API Level | Permissions Required | Check Method |
|---------|-----------|----------------------|--------------|
| 15 | 35 | `USE_FULL_SCREEN_INTENT` | `NotificationManager.canUseFullScreenIntent()` |
| 14 | 34 | `USE_FULL_SCREEN_INTENT` | `NotificationManager.canUseFullScreenIntent()` |
| 13 | 33 | `SYSTEM_ALERT_WINDOW` + `POST_NOTIFICATIONS` | `Settings.canDrawOverlays()` + runtime permission |
| 12L / 12 | 32 / 31 | `SYSTEM_ALERT_WINDOW` | `Settings.canDrawOverlays()` |
| 11 | 30 | `SYSTEM_ALERT_WINDOW` | `Settings.canDrawOverlays()` |

---

## Fallback Chain

```kotlin
// Generic pattern — not production code
fun canShowFullScreenAlert(context: Context): Boolean {
    return when {
        // Android 14+ requires explicit USE_FULL_SCREEN_INTENT grant
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.canUseFullScreenIntent()
        }
        // Android 6–13 requires SYSTEM_ALERT_WINDOW (draw over other apps)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            Settings.canDrawOverlays(context)
        }
        // Below Android 6, permission granted at install time
        else -> true
    }
}
```

---

## Opening the Correct Settings Screen

Each permission type requires a different Settings `Intent` action:

```kotlin
// Generic pattern — not production code
fun openPermissionSettings(context: Context) {
    val intent = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
        else ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
    }.apply {
        data = Uri.parse("package:${context.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
```

---

## Battery Optimization

Background workers are silently killed on many OEM devices (Samsung, Xiaomi, OnePlus) unless the app is exempted from battery optimization. This is handled alongside the above permissions:

```kotlin
// Generic pattern
fun isExemptFromBatteryOptimization(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(PowerManager::class.java)
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true
}
```

---

## Permission Onboarding Flow

```
App Launch
    │
    ▼
Check: all permissions granted?
    │
    ├── YES ──▶ Normal app flow
    │
    └── NO
         │
         ▼
    Check: within 24-hour skip window?
         │
         ├── YES ──▶ Skip permission screen, normal flow
         │
         └── NO
              │
              ▼
         Show Permission Onboarding Screen (Jetpack Compose)
              │
              ├── "Grant Permission" ──▶ Open Settings for each missing permission
              │
              └── "Skip for Now" ──▶ Record timestamp, continue to app
```

---

## The 24-Hour Skip Window

See [`skip-window-implementation.md`](skip-window-implementation.md) for full details on why a boolean flag is insufficient here and how the timestamp-based window solves the process-kill problem.

---

## PermissionStatus Aggregate

Rather than calling three separate methods across the codebase, all permission states are aggregated into a single data class:

```kotlin
// Generic pattern
data class PermissionStatus(
    val hasFullScreenIntent: Boolean,
    val canDrawOverlays: Boolean,
    val ignoringBatteryOptimization: Boolean
) {
    fun allGranted() = hasFullScreenIntent && canDrawOverlays && ignoringBatteryOptimization
}
```

This makes it easy to gate features, log diagnostics, or render the correct UI state with a single check.
