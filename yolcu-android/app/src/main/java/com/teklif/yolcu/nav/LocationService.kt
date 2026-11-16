package com.teklif.yolcu.nav

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.teklif.yolcu.MainActivity
import com.teklif.yolcu.R
import com.teklif.yolcu.geo.Geo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Ön planda çalışan konum servisi: GPS'i saniyede bir okur, [Nav]'a verir, Türkçe sesli
 * yönlendirmeyi okur ve bildirimde kalan yolu gösterir.
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class LocationService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeech = ArrayDeque<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notifyJob: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { Nav.onLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Nav.init(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("tr", "TR")
                while (pendingSpeech.isNotEmpty()) speak(pendingSpeech.removeFirst())
            }
        }
        Nav.speaker = { text -> speak(text) }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Nav.stopNavigation()
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(buildNotification(null))
        startLocation()
        if (notifyJob == null) {
            notifyJob = scope.launch {
                Nav.state.sample(10_000).collect { s ->
                    if (s.navigating) {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(s))
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private var locationStarted = false

    private fun startLocation() {
        if (locationStarted) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(700L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            locationStarted = true
        } catch (e: SecurityException) {
            // İzin verilmemiş; ekran izin isteyince servis yeniden başlatılır.
        }
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            pendingSpeech.addLast(text)
            if (pendingSpeech.size > 3) pendingSpeech.removeFirst()
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "yolcu-${System.nanoTime()}")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Navigasyon", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Yol tarifi ve konum takibi"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(s: NavSnapshot?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val p = s?.progress
        val title = when {
            s?.navigating == true && p?.nextManeuver != null ->
                "${Geo.formatDistance(p.distanceToManeuverM)} · ${p.nextManeuver.instruction}"
            s?.navigating == true -> "Navigasyon sürüyor"
            else -> "Yolcu"
        }
        val text = when {
            s?.navigating == true && p != null -> buildString {
                append("Kalan ${Geo.formatDistance(p.remainingM)} · ${Geo.formatDuration(p.remainingS)}")
                p.nextBorder?.let { append(" · ${it.toName} sınırı ${Geo.formatDistance(p.distanceToBorderM)}") }
            }
            else -> "Konum takibi açık"
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_nav)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (s?.navigating == true) addAction(0, "Durdur", stop) }
            .build()
    }

    override fun onDestroy() {
        notifyJob?.cancel()
        if (locationStarted) fused.removeLocationUpdates(callback)
        Nav.speaker = null
        Nav.saveTrip()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.teklif.yolcu.START"
        const val ACTION_STOP = "com.teklif.yolcu.STOP"
        private const val CHANNEL = "nav"
        private const val NOTIFICATION_ID = 1
    }
}
