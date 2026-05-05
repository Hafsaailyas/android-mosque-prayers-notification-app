# System Architecture

## Overview

This notification system is the entire native Android layer of a WebView-based app. The WebView handles all UI and remote content; the native layer is responsible exclusively for scheduling, delivering, and managing notifications — including background sync, audio, and permission management.

---

## Component Breakdown

### Component 1: Background Sync Layer

Responsible for periodic API polling to keep prayer schedules current.

- Uses `PeriodicWorkRequestBuilder` (WorkManager) for battery-efficient scheduling
- Polling interval is server-controlled via a `sync_after` field in the API response (minimum enforced client-side at 5 minutes)
- Uses `ExistingPeriodicWorkPolicy.KEEP` to prevent duplicate workers from competing code paths
- A `BroadcastReceiver` listening to `BOOT_COMPLETED` re-enqueues the periodic worker after device restart
- A separate `DailyRescheduleWorker` fires every 24 hours to re-schedule all prayer notifications for the new calendar day

### Component 2: Reminder Orchestration Layer

Bridges the API response and the WorkManager scheduling system.

- Parses the server JSON into typed data structures
- Validates prayer times — rejects any trigger time already in the past
- Detects conflicts between full-screen and standard notification trigger times (see conflict resolution doc)
- Enqueues `OneTimeWorkRequest` tasks for each individual reminder
- Compares incoming prayer times against cached values to avoid redundant rescheduling

### Component 3: Notification Delivery Layer

Two separate Worker classes handle the two notification types:

**Standard Notification Worker**
- Builds and displays a standard Android notification via `NotificationCompat`
- Receives an `playAudio` boolean flag — set to `false` when a conflict is detected
- Validates that the scheduled time hasn't passed before firing

**Full-Screen Alert Worker**
- Launches a full-screen `Activity` with `FLAG_SHOW_WHEN_LOCKED` and `FLAG_TURN_SCREEN_ON`
- Passes `prayer_time_millis` to the activity for post-hoc staleness validation
- Supports configurable auto-close timer
- Uses `USE_FULL_SCREEN_INTENT` notification category

### Component 4: Audio Management Layer

Two separate managers because the two notification types require different audio stream contracts.

**Standard Audio Manager (`USAGE_NOTIFICATION_EVENT`)**
- Respects device silent/vibrate mode
- Requests `AUDIOFOCUS_GAIN_TRANSIENT`
- Plays once and releases MediaPlayer on completion

**Full-Screen Audio Manager (`USAGE_ALARM`)**
- Bypasses silent mode — plays even when device is muted
- Loops continuously until explicitly stopped (user dismisses the alert)
- Targets `STREAM_ALARM` for audio focus requests on older APIs

### Component 5: Permission Management Layer

Handles the fragmented permission landscape across Android 11–15.

- SDK-version conditional checks for each permission type
- Opens the correct system Settings screen per Android version
- `PermissionStatus` data class aggregates all permission states in one check
- Onboarding screen (Jetpack Compose) with a "Skip for Now" option backed by a 24-hour timestamp window

---

## Data Flow

```
┌──────────────────┐
│   API Response   │
│  (JSON payload)  │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│         Sync Worker (CoroutineWorker)    │
│  1. Validate auth token                  │
│  2. HTTP GET with Bearer token           │
│  3. Process urgent broadcast messages    │
│  4. Save response to SharedPreferences   │
│  5. Save sync_after interval             │
│  6. Trigger reminder orchestration       │
└────────────────────┬─────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────┐
│        Reminder Orchestration            │
│  1. Parse JSON → typed structures        │
│  2. Calculate trigger times              │
│  3. Build conflict set (HH:mm slots)     │
│  4. Enqueue standard reminders           │
│     (audio suppressed if conflicting)    │
│  5. Enqueue full-screen alerts           │
└──────────┬─────────────────┬─────────────┘
           │                 │
           ▼                 ▼
┌──────────────────┐ ┌───────────────────────┐
│ Standard Worker  │ │ Full-Screen Worker     │
│ NotificationCompat│ │ Activity + FSI intent │
│ NOTIFICATION     │ │ ALARM stream audio     │
│ stream audio     │ │ Lock screen overlay    │
└──────────────────┘ └───────────────────────┘
```

---

## State Persistence

All scheduling state is stored in `SharedPreferences`:

| Key | Type | Purpose |
|-----|------|---------|
| `namazi_response` | String | Cached API JSON for offline rescheduling |
| `sync_after` | Int | Server-controlled polling interval (minutes) |
| `logged_in` | Boolean | Auth gate for all workers |
| `token` | String | Bearer token for API calls |
| `skip_window_start` | Long | Timestamp for 24-hour permission skip window |
| `last_prayer_times` | String | Cached prayer times for change detection |

---

## Recovery Mechanisms

| Event | Recovery Strategy |
|-------|------------------|
| Device reboot | `BOOT_COMPLETED` receiver re-enqueues periodic sync |
| Midnight / new day | `DailyRescheduleWorker` re-schedules from cached JSON |
| Network failure | WorkManager `Result.retry()` with exponential backoff |
| Stale notification | Timestamp validation in Worker + Activity |
| Auth failure | Workers return `Result.success()` (skip silently, don't crash) |
