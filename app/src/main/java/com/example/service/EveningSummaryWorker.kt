package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import kotlinx.coroutines.flow.firstOrNull

class EveningSummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            Log.d("EveningSummaryWorker", "Executing evening summary worker")

            val prefs = UserFinancePreferences(applicationContext)
            if (!prefs.isEveningSummaryEnabled()) {
                Log.d("EveningSummaryWorker", "Evening summary disabled in preferences. Skipping.")
                return Result.success()
            }

            // Verify current time: WorkManager may wake up during morning or deep night due to Doze mode / maintenance window
            val scheduledTime = prefs.getEveningSummaryTime()
            val timeParts = scheduledTime.split(":")
            val targetHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 21
            val targetMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

            val nowCal = java.util.Calendar.getInstance()
            val currentHour = nowCal.get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = nowCal.get(java.util.Calendar.MINUTE)

            val currentMinutesOfDay = currentHour * 60 + currentMinute
            val targetMinutesOfDay = targetHour * 60 + targetMinute
            val diffMinutes = kotlin.math.abs(currentMinutesOfDay - targetMinutesOfDay)
            val cyclicDiffMinutes = kotlin.math.min(diffMinutes, 1440 - diffMinutes)

            // Allow window of ±90 minutes around target evening time. Skip if running at unexpected hours (e.g. morning 09:00 or night)
            if (cyclicDiffMinutes > 90) {
                Log.w(
                    "EveningSummaryWorker",
                    "Skipping summary notification: executed at %02d:%02d, outside scheduled window (%s)".format(currentHour, currentMinute, scheduledTime)
                )
                return Result.success()
            }

            val db = AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
            
            // Get today's start and end time
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis
            
            // Get transactions for today
            val transactions = db.transactionDao().getAllTransactions().firstOrNull() ?: emptyList()
            val todayExpenses = transactions.filter { 
                it.type == "EXPENSE" && it.timestamp >= startOfDay && !it.excludeFromStats
            }.sumOf { it.amount }
            
            val pendingCount = db.pendingNotificationDao().getUnprocessedNotifications().firstOrNull()?.size ?: 0
            
            var text = ""
            if (todayExpenses > 0) {
                text += "Расходы за сегодня: ${com.example.ui.util.CurrencyHelper.formatAmount(todayExpenses, "RUB")}. "
            } else {
                text += "Сегодня вы не совершали трат. "
            }
            
            if (pendingCount > 0) {
                text += "У вас $pendingCount неразобранных операций, давайте запишем их!"
            } else {
                text += "Все операции разобраны, отличная работа!"
            }
            
            PushNotificationHelper.createNotificationChannel(applicationContext)
            val launchIntent = android.content.Intent(applicationContext, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                applicationContext,
                2001,
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val builder = androidx.core.app.NotificationCompat.Builder(applicationContext, PushNotificationHelper.CHANNEL_ID)
                .setSmallIcon(com.example.R.drawable.ic_notification_mono)
                .setContentTitle("🌙 Вечерняя сводка")
                .setContentText(text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                
            try {
                val notificationManager = androidx.core.app.NotificationManagerCompat.from(applicationContext)
                notificationManager.notify(2001, builder.build())
            } catch (e: SecurityException) {
                // permission not granted
            }
            
            return Result.success()
        } catch (e: Exception) {
            Log.e("EveningSummaryWorker", "Error in worker", e)
            return Result.failure()
        }
    }
}
