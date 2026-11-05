package com.dinetime.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dinetime.DineTimeApp
import com.dinetime.MainActivity
import com.dinetime.R
import com.dinetime.data.reads.MealRepository
import com.dinetime.domain.MealItem
import com.dinetime.domain.MealStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

const val REMINDER_CHANNEL = "meal_reminders"
const val EXTRA_TITLE = "title"
const val EXTRA_MEAL_ID = "mealId"
const val EXTRA_AT = "at"

fun reminderTimeOf(item: MealItem): Long? =
    item.meal.reminderLeadMinutes?.let { item.meal.scheduledAt - it * 60_000L }

/**
 * Keeps one alarm per meal that has a reminder lead time. It reads from the reads DB, so
 * temp docs (offline/pending meals) are scheduled immediately and re-keyed once the real doc replicates.
 */
class ReminderScheduler(
    private val context: Context,
    repo: MealRepository,
    scope: CoroutineScope,
) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences("reminder_state", Context.MODE_PRIVATE)

    init {
        createChannel()
        scope.launch {
            repo.observeItems()
                .map { items ->
                    items.filter { it.status != MealStatus.ERROR && !it.deleting }
                        .mapNotNull { i -> reminderTimeOf(i)?.takeIf { it > System.currentTimeMillis() }?.let { i to it } }
                }
                .distinctUntilChanged()
                .collect { sync(it) }
        }
    }

    private fun sync(wanted: List<Pair<MealItem, Long>>) {
        val old = prefs.getStringSet("ids", emptySet()).orEmpty()
        val newIds = wanted.map { it.first.id }.toSet()
        (old - newIds).forEach { cancel(it) }
        wanted.forEach { (item, at) -> schedule(item.id, item.meal.title, item.meal.scheduledAt, at) }
        prefs.edit().putStringSet("ids", newIds).apply()
    }

    private fun intentFor(id: String, title: String = "", scheduledAt: Long = 0): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.dinetime.REMINDER.$id")
            .putExtra(EXTRA_MEAL_ID, id).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_AT, scheduledAt)
        return PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun schedule(id: String, title: String, scheduledAt: Long, at: Long) {
        val pi = intentFor(id, title, scheduledAt)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(id: String) = alarms.cancel(intentFor(id))

    private fun createChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL, "Meal reminders", NotificationManager.IMPORTANCE_HIGH),
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Your meal" }
        val at = intent.getLongExtra(EXTRA_AT, 0)
        val mins = ((at - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(if (mins > 0) "Starts in $mins min" else "It's meal time")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(intent.getStringExtra(EXTRA_MEAL_ID).hashCode(), n)
    }
}

/** Launching the process on boot is enough: [DineTimeApp] re-creates the scheduler from the reads DB. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as? DineTimeApp)?.container
    }
}
