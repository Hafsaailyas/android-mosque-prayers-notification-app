// Generic SDK-version conditional permission checking pattern
// Not production code — for portfolio demonstration only

object PermissionHelper {

    /** Check if full-screen notification alerts are permitted */
    fun canShowFullScreenAlert(context: Context): Boolean {
        return when {
            // Android 14+ (API 34): explicit USE_FULL_SCREEN_INTENT grant required
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.canUseFullScreenIntent()
            }
            // Android 6–13 (API 23–33): SYSTEM_ALERT_WINDOW required
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                Settings.canDrawOverlays(context)
            }
            // Below Android 6: permission granted at install time
            else -> true
        }
    }

    /** Open the correct system settings screen for the current Android version */
    fun openFullScreenPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
        } else {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        }.apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** Check battery optimization exemption (critical for OEM devices) */
    fun isExemptFromBatteryOptimization(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(PowerManager::class.java)
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    /** Aggregate status of all permissions in a single call */
    fun getPermissionStatus(context: Context): PermissionStatus {
        return PermissionStatus(
            hasFullScreenIntent = canShowFullScreenAlert(context),
            canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Settings.canDrawOverlays(context) else true,
            ignoringBatteryOptimization = isExemptFromBatteryOptimization(context)
        )
    }
}

data class PermissionStatus(
    val hasFullScreenIntent: Boolean,
    val canDrawOverlays: Boolean,
    val ignoringBatteryOptimization: Boolean
) {
    fun allGranted() = hasFullScreenIntent && canDrawOverlays && ignoringBatteryOptimization
}
