package com.technoral.ucusbul.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.technoral.ucusbul.domain.Airlines
import com.technoral.ucusbul.domain.BookingLinks
import com.technoral.ucusbul.domain.SearchEngine
import com.technoral.ucusbul.ui.formatPrice
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Arka planda takip edilen aramaları yeniden çalıştırır; hedef fiyatın altına inildiğinde
 * ya da fiyat belirgin şekilde düştüğünde bildirim gönderir.
 */
class PriceWatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = WatchStore(applicationContext)
        val watches = store.all()
        if (watches.isEmpty()) return Result.success()

        val engine = SearchEngine(applicationContext)
        val today = LocalDate.now().toString()

        watches.forEachIndexed { index, watch ->
            if (watch.query.departureDate < today) return@forEachIndexed

            val outcome = runCatching { engine.run(watch.query) }.getOrNull() ?: return@forEachIndexed
            val best = outcome.results.minByOrNull { it.offer.price } ?: run {
                store.update(watch.id) {
                    it.copy(lastCheckedAt = System.currentTimeMillis(), lastNote = "Uygun uçuş bulunamadı")
                }
                return@forEachIndexed
            }

            val price = best.offer.price
            val previous = watch.lastBestPrice
            val target = watch.targetPrice

            val hitTarget = target != null && price <= target
            val bigDrop = previous != null && price <= previous * DROP_RATIO && abs(previous - price) > 1.0

            if (hitTarget || bigDrop) {
                val destLabel = best.destination.airport.let { "${it.city} (${it.iata})" }
                val ground = if (best.destination.isAnchor) {
                    ""
                    } else {
                    " • ${watch.query.destinationCity} şehrine ${
                        com.technoral.ucusbul.data.formatMinutes(best.destination.groundMinutes)
                    } kara yolu"
                }
                val title = if (hitTarget) {
                    "Hedef fiyat yakalandı: ${formatPrice(price, best.offer.currency)}"
                } else {
                    "Fiyat düştü: ${formatPrice(price, best.offer.currency)}"
                }
                val text = buildString {
                    append(best.offer.origin).append(" → ").append(destLabel)
                    append(" • ").append(watch.query.departureDate)
                    append(" • ").append(Airlines.label(best.offer.airlineCodes))
                    append(ground)
                    previous?.let {
                        append("\nÖnceki en iyi: ").append(formatPrice(it, best.offer.currency))
                    }
                }
                val url = best.offer.deepLink ?: BookingLinks.googleFlights(
                    best.offer.origin,
                    best.offer.destination,
                    watch.query.departureDate,
                    watch.query.returnDate
                )
                Notifications.postPriceDrop(applicationContext, 5000 + index, title, text, url)
            }

            store.update(watch.id) {
                it.copy(
                    lastBestPrice = price,
                    lastCheckedAt = System.currentTimeMillis(),
                    lastNote = "En iyi: ${formatPrice(price, best.offer.currency)} • ${best.destination.airport.iata}"
                )
            }
        }

        return Result.success()
    }

    companion object {
        private const val DROP_RATIO = 0.93
        private const val PERIODIC_NAME = "price-watch-periodic"
        private const val ONE_SHOT_NAME = "price-watch-now"

        fun schedule(context: Context, everyHours: Long = 6L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PriceWatchWorker>(everyHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }

        fun checkNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PriceWatchWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
