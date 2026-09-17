package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PlannedPaymentReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "Запланированная операция"
        val amount = inputData.getDouble("amount", 0.0)
        val type = inputData.getString("type") ?: "EXPENSE"
        val plannedId = inputData.getLong("plannedId", 0L)

        PushNotificationHelper.sendPlannedPaymentReminder(
            context = applicationContext,
            title = title,
            amount = amount,
            type = type,
            plannedId = plannedId
        )

        return Result.success()
    }
}
