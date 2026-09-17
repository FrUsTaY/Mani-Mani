package com.example.service

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.entity.PlannedTransactionEntity
import java.util.concurrent.TimeUnit

object PlannedPaymentScheduler {
    fun scheduleReminder(context: Context, plannedTransaction: PlannedTransactionEntity) {
        val delay = plannedTransaction.plannedDate - System.currentTimeMillis()
        if (delay <= 0) return

        val workTag = "planned_reminder_${plannedTransaction.id}"
        val data = workDataOf(
            "title" to plannedTransaction.note,
            "amount" to plannedTransaction.amount,
            "type" to plannedTransaction.type,
            "plannedId" to plannedTransaction.id
        )

        val workRequest = OneTimeWorkRequestBuilder<PlannedPaymentReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(workTag)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workTag,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelReminder(context: Context, plannedId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("planned_reminder_$plannedId")
    }
}
