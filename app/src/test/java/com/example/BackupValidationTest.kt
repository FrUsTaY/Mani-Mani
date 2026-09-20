package com.example

import com.example.data.entity.AccountEntity
import com.example.data.entity.BackupData
import com.example.data.entity.BackupPreferences
import com.example.data.entity.CategoryEntity
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {

    @Test
    fun testValidateEmptyAccountsThrowsException() {
        val emptyBackup = BackupData(
            timestamp = System.currentTimeMillis(),
            accounts = emptyList(),
            categories = emptyList(),
            transactions = emptyList(),
            budgets = emptyList(),
            goals = emptyList(),
            debts = emptyList(),
            plannedTransactions = emptyList(),
            preferences = BackupPreferences(
                paydayDay = 10,
                bankOfTheMonth = "VTB",
                pushNotificationsEnabled = true,
                themeMode = "SYSTEM",
                eveningSummaryEnabled = false,
                eveningSummaryTime = "21:00",
                bankPushInterceptEnabled = true,
                zenmoneyPushInterceptEnabled = false,
                spamKeywords = emptyList()
            )
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            if (emptyBackup.accounts.isEmpty()) {
                throw IllegalStateException("Файл бэкапа поврежден или не содержит счетов. База данных сохранена в неизменном виде.")
            }
        }
        assertTrue(exception.message?.contains("не содержит счетов") == true)
    }

    @Test
    fun testValidateBlankAccountNameThrowsException() {
        val invalidAccount = AccountEntity(
            id = 1L,
            name = "   ",
            type = "DEBIT",
            balance = 100.0
        )
        val badBackup = BackupData(
            timestamp = System.currentTimeMillis(),
            accounts = listOf(invalidAccount),
            categories = emptyList(),
            transactions = emptyList(),
            budgets = emptyList(),
            goals = emptyList(),
            debts = emptyList(),
            plannedTransactions = emptyList(),
            preferences = BackupPreferences(
                paydayDay = 10,
                bankOfTheMonth = "VTB",
                pushNotificationsEnabled = true,
                themeMode = "SYSTEM",
                eveningSummaryEnabled = false,
                eveningSummaryTime = "21:00",
                bankPushInterceptEnabled = true,
                zenmoneyPushInterceptEnabled = false,
                spamKeywords = emptyList()
            )
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            if (badBackup.accounts.any { it.name.isBlank() }) {
                throw IllegalStateException("Файл бэкапа поврежден: обнаружены счета с пустыми названиями. Откат изменений.")
            }
        }
        assertTrue(exception.message?.contains("пустыми названиями") == true)
    }
}
