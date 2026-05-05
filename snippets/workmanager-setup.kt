// Generic WorkManager periodic sync pattern
// Not production code — for portfolio demonstration only

fun schedulePeriodicSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
        15, TimeUnit.MINUTES
    )
        .setConstraints(constraints)
        .build()

    // KEEP policy: if already enqueued, leave it — don't reset the timer
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "periodic_sync",
        ExistingPeriodicWorkPolicy.KEEP,
        syncRequest
    )
}

// One-time immediate sync (e.g., on app open or login)
fun triggerImmediateSync(context: Context) {
    val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .addTag("IMMEDIATE_SYNC")
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "immediate_sync",
        ExistingWorkPolicy.REPLACE,
        syncRequest
    )
}

// Daily rescheduler — re-queues all prayer notifications for the new day
fun scheduleDailyRescheduler(context: Context) {
    val dailyRequest = PeriodicWorkRequestBuilder<DailyRescheduleWorker>(
        24, TimeUnit.HOURS
    )
        .setInitialDelay(1, TimeUnit.HOURS)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "daily_reschedule",
        ExistingPeriodicWorkPolicy.KEEP,
        dailyRequest
    )
}
