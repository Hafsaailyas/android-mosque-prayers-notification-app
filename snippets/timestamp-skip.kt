// Generic timestamp-based 24-hour window pattern
// Not production code — for portfolio demonstration only

/**
 * Manages a time-bounded preference using a stored timestamp rather than a boolean flag.
 *
 * A boolean flag fails when:
 *  - The app process is killed and the Activity relaunches with CLEAR_TASK
 *  - The flag is consumed (cleared) on first read, losing state across process kills
 *  - There is no way to determine *when* the flag was set (no expiry)
 *
 * A timestamp solves all three: it persists, is non-destructively readable,
 * and carries its own expiry information.
 */
class TimestampWindowManager(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_PERMISSION_SKIP = "permission_skip_start"
        const val WINDOW_24H = 24 * 60 * 60 * 1000L
    }

    /**
     * Record the current moment as the start of the window.
     * Call when the user taps "Skip for Now".
     */
    fun activate(key: String) {
        prefs.edit()
            .putLong(key, System.currentTimeMillis())
            .apply()
    }

    /**
     * Returns true if we are currently within [windowMs] of the activation time.
     * Safe to call multiple times — does not mutate state.
     */
    fun isActive(key: String, windowMs: Long = WINDOW_24H): Boolean {
        val start = prefs.getLong(key, 0L)
        if (start == 0L) return false
        return (System.currentTimeMillis() - start) < windowMs
    }

    /**
     * Explicitly clear the window (e.g., user granted the permission).
     */
    fun clear(key: String) {
        prefs.edit().remove(key).apply()
    }
}

// ─── Usage example ──────────────────────────────────────────────────────────

fun shouldShowPermissionOnboarding(context: Context): Boolean {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val windowManager = TimestampWindowManager(prefs)

    // If permissions are already granted, never show the screen
    if (PermissionHelper.getPermissionStatus(context).allGranted()) return false

    // If within 24-hour skip window, don't show
    if (windowManager.isActive(TimestampWindowManager.KEY_PERMISSION_SKIP)) return false

    return true
}

// In the onboarding Composable / Activity:
fun onSkipClicked(context: Context) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    TimestampWindowManager(prefs).activate(TimestampWindowManager.KEY_PERMISSION_SKIP)
    // Navigate to main app
}

fun onPermissionsGranted(context: Context) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    TimestampWindowManager(prefs).clear(TimestampWindowManager.KEY_PERMISSION_SKIP)
    // Navigate to main app
}
