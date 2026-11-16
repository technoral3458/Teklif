package com.teklif.yolcu.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Bir harita karosunun kimliği. */
data class TileKey(val z: Int, val x: Int, val y: Int)

/**
 * OpenStreetMap karolarını indirir; bellekte ve diskte saklar. Diskteki karolar internet yokken
 * de kullanılır (gezilen yol geri dönüşte çevrimdışı görünür).
 */
class TileCache private constructor(context: Context) {

    private val dir = File(context.cacheDir, "tiles").apply { mkdirs() }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap.newKeySet<TileKey>()
    private val failedAt = ConcurrentHashMap<TileKey, Long>()
    private val parallel = Semaphore(4)

    private val memory = object : LruCache<TileKey, Bitmap>(MEMORY_TILES) {}

    /** Karo geldiğinde çizimi yenilemek isteyenler (telefon ekranı, araç ekranı). */
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** Bellekteki karo; yoksa arka planda yükler ve null döner. */
    fun get(key: TileKey): Bitmap? {
        memory.get(key)?.let { return it }
        schedule(key)
        return null
    }

    /** Yalnızca bellekten (yükleme başlatmaz). */
    fun peek(key: TileKey): Bitmap? = memory.get(key)

    private fun schedule(key: TileKey) {
        if (!inFlight.add(key)) return
        val lastFail = failedAt[key]
        if (lastFail != null && System.currentTimeMillis() - lastFail < RETRY_MS) {
            inFlight.remove(key)
            return
        }
        scope.launch {
            try {
                val bmp = loadFromDisk(key) ?: download(key)
                if (bmp != null) {
                    memory.put(key, bmp)
                    listeners.forEach { it() }
                } else {
                    failedAt[key] = System.currentTimeMillis()
                }
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private fun file(key: TileKey) = File(dir, "${key.z}_${key.x}_${key.y}.png")

    private fun loadFromDisk(key: TileKey): Bitmap? {
        val f = file(key)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path) ?: run { f.delete(); null }
    }

    private suspend fun download(key: TileKey): Bitmap? = parallel.withPermit {
        val request = Request.Builder()
            .url("https://tile.openstreetmap.org/${key.z}/${key.x}/${key.y}.png")
            // OSM karo kullanım politikası: uygulamayı tanıtan bir User-Agent zorunlu.
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return null
                val bytes = r.body?.bytes() ?: return null
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                runCatching { file(key).writeBytes(bytes) }
                trimDiskIfNeeded()
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    private var lastTrim = 0L

    /** Disk önbelleğini sınırda tutar (en eski dosyalar silinir). */
    private fun trimDiskIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastTrim < 60_000) return
        lastTrim = now
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total < DISK_LIMIT_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            total -= f.length()
            f.delete()
            if (total < DISK_LIMIT_BYTES * 0.8) break
        }
    }

    companion object {
        const val USER_AGENT = "Yolcu/1.0 (Android; https://github.com/technoral3458/Teklif)"
        private const val MEMORY_TILES = 160
        private const val DISK_LIMIT_BYTES = 400L * 1024 * 1024
        private const val RETRY_MS = 30_000L

        @Volatile private var instance: TileCache? = null
        fun get(context: Context): TileCache = instance ?: synchronized(this) {
            instance ?: TileCache(context.applicationContext).also { instance = it }
        }
    }
}
