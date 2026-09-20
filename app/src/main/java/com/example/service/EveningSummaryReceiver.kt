package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar

class EveningSummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = UserFinancePreferences(context)
        if (!prefs.isEveningSummaryEnabled()) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context, this)
                val now = System.currentTimeMillis()
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = cal.timeInMillis

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

                PushNotificationHelper.sendEveningSummaryNotification(context, text)

                // Reschedule for next day
                EveningSummaryScheduler.schedule(context, prefs.getEveningSummaryTime(), forceUpdate = true)
            } catch (e: Exception) {
                Log.e("EveningSummaryReceiver", "Error in EveningSummaryReceiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
