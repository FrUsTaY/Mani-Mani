package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object EveningSummaryScheduler {
    private const val WORK_NAME = "EveningSummaryWork"
    private const val ALARM_REQUEST_CODE = 3001

    fun schedule(context: Context, timeString: String, forceUpdate: Boolean = false) {
        val parts = timeString.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: 21
        val minute = parts[1].toIntOrNull() ?: 0

        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.DAY_OF_YEAR, 1)
        }

        // 1. Exact AlarmManager trigger for on-time delivery in Doze mode
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, EveningSummaryReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        dueDate.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        dueDate.timeInMillis,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("EveningSummaryScheduler", "Failed to schedule exact alarm: ${e.message}")
        }

        // 2. WorkManager periodic backup
        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        val workRequest = PeriodicWorkRequestBuilder<EveningSummaryWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .build()

        val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            policy,
            workRequest
        )
    }

    fun cancel(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, EveningSummaryReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (alarmManager != null && pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            Log.w("EveningSummaryScheduler", "Failed to cancel alarm: ${e.message}")
        }

        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
