// Generic dual audio stream configuration pattern
// Not production code — for portfolio demonstration only

// ─── STANDARD NOTIFICATIONS ─────────────────────────────────────────────────
// Uses USAGE_NOTIFICATION_EVENT — respects device silent/vibrate mode

object StandardAudioManager {

    fun play(context: Context, audioResId: Int, onComplete: () -> Unit) {
        val audioManager = context.getSystemService(AudioManager::class.java)

        // Respect silent mode — check before playing
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            onComplete()
            return
        }

        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT) // respects silent
                    .build()
            )
            val afd = context.resources.openRawResourceFd(audioResId)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            prepare()
            setOnCompletionListener { release(); onComplete() }
            start()
        }
    }
}

// ─── FULL-SCREEN / ALARM ALERTS ─────────────────────────────────────────────
// Uses USAGE_ALARM — bypasses silent mode, loops until dismissed

object AlarmAudioManager {

    private var mediaPlayer: MediaPlayer? = null

    fun play(context: Context, audioResId: Int) {
        stopAndRelease()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM) // bypasses silent mode
                    .build()
            )
            val afd = context.resources.openRawResourceFd(audioResId)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            prepare()
            isLooping = true  // Loop until user dismisses
            start()
        }
    }

    fun stop() = stopAndRelease()

    private fun stopAndRelease() {
        mediaPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }
}

// ─── AUDIO FOCUS (Android 8+) ───────────────────────────────────────────────

fun requestAlarmAudioFocus(context: Context): Int {
    val audioManager = context.getSystemService(AudioManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioManager.requestAudioFocus(
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                .build()
        )
    } else {
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_ALARM,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
    }
}
