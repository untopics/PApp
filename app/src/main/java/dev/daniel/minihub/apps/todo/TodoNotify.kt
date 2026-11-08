package dev.daniel.minihub.apps.todo

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

/**
 * Reminders, same scheme as Traditions: one inexact daily alarm around [CHECK_TIME] posts every open
 * entry whose reminder date is today. Each entry is posted at most once per reminder date, so a
 * reminder set for today after the check (e.g. while adding it) is posted right away on the next check.
 */
object TodoNotify {
    private const val CHANNEL = "todo"
    private const val ACTION_CHECK = "dev.daniel.minihub.todo.CHECK"
    private val CHECK_TIME: LocalTime = LocalTime.of(8, 0)
    private const val PREFS = "todo_notify"

    fun schedule(context: Context) {
        val ctx = context.applicationContext
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(CHECK_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, checkIntent(ctx))
        // After today's check time: catch up (also posts a reminder just set for today).
        if (!now.toLocalTime().isBefore(CHECK_TIME)) check(ctx)
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun check(context: Context) {
        val ctx = context.applicationContext
        if (!hasPermission(ctx)) return // try again on the next check, once allowed
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        val posted = if (prefs.getString("day", null) == today.toString()) {
            prefs.getStringSet("posted", emptySet())!!.toMutableSet()
        } else mutableSetOf()

        val state = TodoData.load(JsonStore(ctx, TodoApp.id))
        val due = state.entries.remindersOn(today).filter { it.id !in posted }
        if (due.isNotEmpty()) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Todo reminders", NotificationManager.IMPORTANCE_DEFAULT))
            for (e in due) {
                val list = state.lists.firstOrNull { it.id == e.listId }?.name.orEmpty()
                val text = listOfNotNull(e.due?.let { dueText(it, today) }, list.takeIf { it.isNotEmpty() }).joinToString(" · ")
                val n = Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_launcher_fg)
                    .setContentTitle("${TodoApp.icon} ${e.title}")
                    .setContentText(text)
                    .setContentIntent(openIntent(ctx))
                    .setAutoCancel(true)
                    .build()
                nm.notify(notificationId(e.id), n)
                posted += e.id
            }
        }
        prefs.edit().putString("day", today.toString()).putStringSet("posted", posted).apply()
    }

    /** Removes a posted reminder (entry done or deleted). */
    fun cancel(context: Context, entryId: String) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(entryId))
    }

    private fun notificationId(entryId: String) = "todo:$entryId".hashCode()

    private fun checkIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 11,
        Intent(ctx, TodoReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 12,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_MINIAPP, TodoApp.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun onBroadcast(ctx: Context, action: String?) {
        when (action) {
            ACTION_CHECK -> check(ctx)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> schedule(ctx)
        }
    }
}

class TodoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TodoNotify.onBroadcast(context, intent.action)
    }
}
