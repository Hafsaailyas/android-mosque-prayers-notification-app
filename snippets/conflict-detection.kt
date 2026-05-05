// Generic set-based conflict detection pattern for dual notification scheduling
// Not production code — for portfolio demonstration only

fun scheduleAllRemindersWithConflictDetection(
    context: Context,
    fullScreenAlerts: List<FullScreenAlert>,
    standardReminders: List<StandardReminder>
) {
    // Phase 1: Build a set of HH:mm slots occupied by full-screen alerts
    val occupiedSlots: Set<String> = fullScreenAlerts
        .map { toHourMinute(it.triggerTimeMillis) }
        .toSet()

    // Phase 2: Schedule standard reminders — suppress audio if slot is taken
    standardReminders.forEach { reminder ->
        val slot = toHourMinute(reminder.triggerTimeMillis)
        val hasConflict = slot in occupiedSlots

        scheduleStandardReminder(
            context = context,
            reminder = reminder,
            playAudio = !hasConflict  // Notification still shows; only audio is suppressed
        )
    }

    // Phase 3: Full-screen alerts always play alarm audio
    fullScreenAlerts.forEach { alert ->
        scheduleFullScreenAlert(context, alert)
    }
}

/**
 * Format millis to HH:mm for minute-level granularity comparison.
 *
 * Using millisecond equality is brittle here: trigger times for the two
 * notification types are derived from different sources (one from a
 * pre-calculated millis value, one from a parsed time string), so they
 * may differ by a few hundred milliseconds even when representing the
 * same clock minute. Rounding to minutes eliminates false misses.
 */
private fun toHourMinute(timeMillis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
    return "%02d:%02d".format(
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE)
    )
}

private fun scheduleStandardReminder(
    context: Context,
    reminder: StandardReminder,
    playAudio: Boolean
) {
    val delay = reminder.triggerTimeMillis - System.currentTimeMillis()
    if (delay <= 0) return  // Already past — skip

    val inputData = Data.Builder()
        .putBoolean("playAudio", playAudio)
        // ... other fields
        .build()

    val workRequest = OneTimeWorkRequestBuilder<StandardNotificationWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(inputData)
        .setConstraints(Constraints.NONE)
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "standard_${reminder.id}_${reminder.triggerTimeMillis}",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}
