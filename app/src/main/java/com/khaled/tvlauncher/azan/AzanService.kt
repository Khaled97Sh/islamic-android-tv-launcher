package com.khaled.tvlauncher.azan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.khaled.tvlauncher.R
import com.khaled.tvlauncher.ui.theme.UI_V2

/**
 * Plays the adhan when a prayer time arrives and the TV is on.
 *
 * Requesting transient-exclusive audio focus makes YouTube (and most players) pause themselves;
 * they resume when we abandon focus. A reminder is drawn over whatever is on screen.
 */
class AzanService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var overlay: android.view.View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayer = intent?.getStringExtra(AzanReceiver.EXTRA_PRAYER) ?: "الصلاة"
        startForeground(NOTIFICATION_ID, buildNotification(prayer))

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            // TV is off / in standby: nothing to interrupt.
            stopSelf()
            return START_NOT_STICKY
        }

        stopAll()
        runCatching { showOverlay(prayer) }.onFailure { android.util.Log.e("TvLauncher", "azan overlay failed", it) }
        playAdhan()
        return START_NOT_STICKY
    }

    private fun playAdhan() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .build()
            .also { am.requestAudioFocus(it) }

        val mp = MediaPlayer.create(this, nextAdhanRes(), attrs, am.generateAudioSessionId())
        if (mp == null) {
            // The recording could not be opened: keep the reminder card up for a while, then finish quietly.
            android.util.Log.e("TvLauncher", "adhan MediaPlayer could not be created")
            abandonFocus()
            handler.postDelayed({ finish() }, REMINDER_AFTER_ADHAN_MS)
            return
        }
        player = mp
        mp.setOnErrorListener { _, what, extra ->
            android.util.Log.e("TvLauncher", "adhan playback error $what/$extra")
            abandonFocus()
            handler.postDelayed({ finish() }, REMINDER_AFTER_ADHAN_MS)
            true
        }
        mp.setOnCompletionListener {
            abandonFocus()
            // Keep the reminder up a little longer after the adhan ends, then finish.
            handler.postDelayed({ finish() }, REMINDER_AFTER_ADHAN_MS)
        }
        mp.start()
    }

    /** Alternates between the bundled adhan recordings, remembering which one played last. */
    private fun nextAdhanRes(): Int {
        val prefs = getSharedPreferences("azan", Context.MODE_PRIVATE)
        val i = (prefs.getInt("last", -1) + 1) % ADHANS.size
        prefs.edit().putInt("last", i).apply()
        return ADHANS[i]
    }

    private fun showOverlay(prayer: String) {
        if (!Settings.canDrawOverlays(this)) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        fun label(text: String, sp: Float, color: Int, bold: Boolean) = TextView(this).apply {
            this.text = text
            setTextColor(color)
            textSize = sp
            if (UI_V2 && !bold) androidx.core.content.res.ResourcesCompat.getFont(this@AzanService, R.font.plex_arabic_regular)?.let { typeface = it }
            gravity = Gravity.CENTER
            if (bold) typeface = if (UI_V2) (androidx.core.content.res.ResourcesCompat.getFont(this@AzanService, R.font.plex_arabic_medium) ?: Typeface.DEFAULT_BOLD)
                else Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(dp(5f).toFloat(), 0f, dp(1.5f).toFloat(), 0xCC000000.toInt())
        }

        val screen = resources.displayMetrics
        val topPx = dp(28f)
        // Liquid glass: same layers as the home-screen cards (see AzanGlassCard).
        val card = AzanGlassCard(this, screen.widthPixels, screen.heightPixels, topPx, dp(30f).toFloat(), dp(1.2f).toFloat()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            setPadding(dp(44f), dp(22f), dp(44f), dp(22f))
            addView(label("حان وقت صلاة $prayer", 28f, Color.WHITE, bold = true))
            addView(label("قُمْ إلى الصلاة", 17f, if (UI_V2) 0xFFFFE8A8.toInt() else 0xFFF2D58B.toInt(), bold = false).apply { setPadding(0, dp(6f), 0, 0) })
            // Focusable so the remote's OK / Back reaches us and closes the reminder (and the adhan).
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setOnClickListener { finish() }
            setOnKeyListener { _, keyCode, event ->
                val dismissKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BACK
                if (dismissKey && event.action == KeyEvent.ACTION_DOWN) finish()
                dismissKey
            }
            alpha = 0f
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurBehindRadius = dp(28f) // frosted blur where the compositor supports it
        }
        wm.addView(card, lp)
        overlay = card
        card.requestFocus()
        card.animate().alpha(1f).setDuration(260).start()
    }

    private fun abandonFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        player?.release(); player = null
        abandonFocus()
        overlay?.let { runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) } }
        overlay = null
    }

    private fun finish() {
        stopAll()
        stopSelf()
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    private fun buildNotification(prayer: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.azan_channel), NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_azan)
            .setContentTitle(getString(R.string.azan_notification))
            .setContentText(prayer)
            .build()
    }

    private companion object {
        val ADHANS = intArrayOf(R.raw.first, R.raw.second)
        const val CHANNEL_ID = "azan"
        const val NOTIFICATION_ID = 77
        const val REMINDER_AFTER_ADHAN_MS = 90_000L
    }
}
