package dev.daniel.minihub.apps.lawn

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.daniel.minihub.MainActivity
import dev.daniel.minihub.R
import dev.daniel.minihub.core.JsonStore
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Fetch + evaluate + notify. Shared by the background job and the app itself. Blocking. */
object LawnCheck {
    private const val CHANNEL = "lawn"
    private const val JOB_PERIODIC = 4201
    private const val JOB_NOW = 4202

    /** Returns null on success, or a short error message if the fetch failed. */
    fun run(context: Context): String? {
        val store = JsonStore(context, LawnStore.APP_ID)
        val place = LawnStore.load(store).place
        val now = Instant.now()
        val fetched = runCatching { OpenMeteo.fetch(place, now) }
        var notes = emptyList<Note>()
        LawnStore.update(store) { old ->
            val today = LocalDate.now()
            val merged = old.copy(days = LawnStore.mergeForecast(old.days, fetched.getOrDefault(emptyList()), today))
            notes = if (canNotify(context)) LawnRules.pending(merged, today, now) else emptyList()
            merged.copy(notified = merged.notified + notes.associate { it.key to today })
        }
        notes.forEach { post(context, it) }
        return fetched.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(context: Context, note: Note) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lawn", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_MINIAPP, LawnStore.APP_ID)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lawn)
            .setContentTitle(note.title)
            .setContentText(note.text)
            .setStyle(Notification.BigTextStyle().bigText(note.text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(note.key.hashCode(), n)
    }

    /**
     * On hub start: one fetch as soon as there is network, plus a periodic check every ~6 h
     * while any notifications are enabled.
     */
    fun schedule(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        val component = ComponentName(context, LawnJobService::class.java)
        js.schedule(
            JobInfo.Builder(JOB_NOW, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
        )
        updatePeriodic(context)
    }

    fun updatePeriodic(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        val wanted = LawnStore.load(JsonStore(context, LawnStore.APP_ID)).notify.isNotEmpty()
        val existing = js.getPendingJob(JOB_PERIODIC)
        if (!wanted) {
            if (existing != null) js.cancel(JOB_PERIODIC)
            return
        }
        if (existing != null) return
        js.schedule(
            JobInfo.Builder(JOB_PERIODIC, ComponentName(context, LawnJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6 * 60 * 60 * 1000L, 60 * 60 * 1000L)
                .setPersisted(true)
                .build()
        )
    }
}

class LawnJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            runCatching { LawnCheck.run(applicationContext) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
