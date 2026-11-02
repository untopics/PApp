package dev.daniel.minihub.apps.traditions

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.daniel.minihub.MainActivity
import dev.daniel.minihub.R
import dev.daniel.minihub.core.JsonStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reminders: one inexact daily alarm around [CHECK_TIME]. Each check posts every reminder whose fire
 * date is today, once per day. No exact-alarm permission needed; re-armed after reboot / app update.
 */
object TraditionsNotify {
    private const val CHANNEL = "traditions"
    private const val ACTION_CHECK = "dev.daniel.minihub.traditions.CHECK"
    private val CHECK_TIME: LocalTime = LocalTime.of(8, 0)
    private const val PREFS = "traditions_notify"

    fun schedule(context: Context) {
        val ctx = context.applicationContext
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(CHECK_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, checkIntent(ctx))
        // Installed, rebooted or opened after today's check time: catch up on today's reminders.
        if (!now.toLocalTime().isBefore(CHECK_TIME)) check(ctx)
    }

    fun check(context: Context) {
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        if (prefs.getString("lastCheck", null) == today.toString()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return // try again on the next check, once allowed

        val planner = TraditionsData.load(JsonStore(ctx, TraditionsApp.id)).planner()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Traditions & birthdays", NotificationManager.IMPORTANCE_DEFAULT))
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        for (r in planner.remindersOn(today)) {
            val o = r.occurrence
            val title = if (r.daysBefore == 0) "${o.kind.icon} ${o.name} · today"
            else "${o.kind.icon} ${o.name} · ${o.date.format(fmt)} (in ${r.daysBefore} days)"
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_fg)
                .setContentTitle(title)
                .setContentText(r.text)
                .setStyle(Notification.BigTextStyle().bigText(r.text))
                .setContentIntent(openIntent(ctx))
                .setAutoCancel(true)
                .build()
            nm.notify(r.key.hashCode(), n)
        }
        prefs.edit().putString("lastCheck", today.toString()).apply()
    }

    private fun checkIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1,
        Intent(ctx, TraditionsReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 2,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_MINIAPP, TraditionsApp.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun onBroadcast(ctx: Context, action: String?) {
        when (action) {
            ACTION_CHECK -> check(ctx)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> schedule(ctx)
        }
    }
}

class TraditionsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TraditionsNotify.onBroadcast(context, intent.action)
    }
}
