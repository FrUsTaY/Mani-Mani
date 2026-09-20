package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.ui.util.CurrencyHelper

object PushNotificationHelper {

    const val CHANNEL_ID = "bank_transaction_reminders"
    const val CHANNEL_NAME = "Банковские операции"
    const val CHANNEL_DESC = "Уведомления-напоминания о новых расходах и доходах по банковским пушам"

    const val PLANNED_CHANNEL_ID = "planned_payment_reminders"
    const val PLANNED_CHANNEL_NAME = "Запланированные платежи"
    const val PLANNED_CHANNEL_DESC = "Уведомления-напоминания о запланированных платежах и доходах"

    const val EVENING_SUMMARY_CHANNEL_ID = "evening_summary_channel"
    const val EVENING_SUMMARY_CHANNEL_NAME = "Вечерняя сводка"
    const val EVENING_SUMMARY_CHANNEL_DESC = "Ежедневный сводный отчет о расходах и неразобранных операциях"

    private var notificationIdCounter = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            
            val bankChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val plannedChannel = NotificationChannel(PLANNED_CHANNEL_ID, PLANNED_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = PLANNED_CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val eveningChannel = NotificationChannel(EVENING_SUMMARY_CHANNEL_ID, EVENING_SUMMARY_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = EVENING_SUMMARY_CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(bankChannel)
            notificationManager?.createNotificationChannel(plannedChannel)
            notificationManager?.createNotificationChannel(eveningChannel)
        }
    }

    fun sendBankTransactionReminder(
        context: Context,
        bankName: String,
        amount: Double,
        currency: String = "RUB",
        merchant: String = "",
        type: String = "EXPENSE",
        notificationId: Int? = null
    ) {
        val prefs = UserFinancePreferences(context)
        if (!prefs.isPushNotificationsEnabled()) {
            return
        }

        createNotificationChannel(context)

        val notifId = notificationId ?: notificationIdCounter++

        val formattedAmount = CurrencyHelper.formatAmount(amount, currency)
        val title = when (type) {
            "EXPENSE" -> "💳 Покупка: $formattedAmount"
            "INCOME" -> "💰 Поступление: +$formattedAmount"
            else -> "🔁 Перевод: $formattedAmount"
        }

        val text = if (merchant.isNotBlank()) {
            "$bankName: $merchant. Нажмите, чтобы подтвердить операцию в приложении."
        } else {
            "$bankName: зафиксирована операция на $formattedAmount. Нажмите для записи."
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification_mono)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission not yet granted
        }
    }

    fun sendBatchBankTransactionReminder(
        context: Context,
        bankName: String,
        count: Int,
        totalAmount: Double,
        currency: String = "RUB",
        notificationId: Int = 8888
    ) {
        val prefs = UserFinancePreferences(context)
        if (!prefs.isPushNotificationsEnabled()) {
            return
        }

        createNotificationChannel(context)

        val formattedAmount = CurrencyHelper.formatAmount(totalAmount, currency)
        val title = "📥 Зафиксировано операций: $count"
        val text = "$bankName: получено $count операций на сумму $formattedAmount. Нажмите для подтверждения."

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification_mono)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission not yet granted
        }
    }

    fun sendTestReminder(context: Context) {
        val prefs = UserFinancePreferences(context)
        if (!prefs.isPushNotificationsEnabled()) {
            return
        }

        createNotificationChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            9999,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification_mono)
            .setContentTitle("🔔 Мани-мани: Пуш-уведомления активны")
            .setContentText("При покупках и переводах в банках вам будут приходить напоминания добавить операцию.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Пуш-уведомления успешно работают! Теперь, когда банк пришлёт уведомление о трате или доходе, Мани-мани напомнит зафиксировать её в вашем бюджете."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(9999, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission not yet granted
        }
    }

    fun sendPlannedPaymentReminder(
        context: Context,
        title: String,
        amount: Double,
        type: String = "EXPENSE",
        plannedId: Long = 0L
    ) {
        val prefs = UserFinancePreferences(context)
        if (!prefs.isPushNotificationsEnabled()) {
            return
        }

        createNotificationChannel(context)

        val notifId = (20000 + (plannedId % 10000)).toInt()

        val formattedAmount = CurrencyHelper.formatAmount(amount, "RUB")
        val notifTitle = if (type == "INCOME") "💰 Ожидаемый доход: +$formattedAmount" else "🔔 Напоминание о платеже: $formattedAmount"
        val text = "Запланировано: $title. Нажмите, чтобы открыть приложение и отметить исполнение."

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, PLANNED_CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification_mono)
            .setContentTitle(notifTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notifId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission not yet granted
        }
    }

    fun sendEveningSummaryNotification(
        context: Context,
        text: String,
        notificationId: Int = 2001
    ) {
        val prefs = UserFinancePreferences(context)
        if (!prefs.isEveningSummaryEnabled()) {
            return
        }

        createNotificationChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, EVENING_SUMMARY_CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_notification_mono)
            .setContentTitle("🌙 Вечерняя сводка")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission not yet granted
        }
    }
}
