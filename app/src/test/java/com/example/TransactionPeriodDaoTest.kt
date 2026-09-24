package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.AccountEntity
import com.example.data.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TransactionPeriodDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPeriodSummaryCountsAllOperationsAndSumsOnlyExpenses() = runBlocking {
        val accId = db.accountDao().insertAccount(
            AccountEntity(name = "Test Card", type = "DEBIT", balance = 10000.0)
        )
        val acc2Id = db.accountDao().insertAccount(
            AccountEntity(name = "Savings", type = "DEPOSIT", balance = 5000.0)
        )

        val t1 = 1000L
        val t2 = 2000L
        val t3 = 3000L
        val t4 = 4000L
        val tOutside = 9000L

        // Inside period (1000..5000):
        // 1. Expense: 1500
        db.transactionDao().insertTransaction(
            TransactionEntity(type = "EXPENSE", amount = 1500.0, accountId = accId, timestamp = t1)
        )
        // 2. Income: 5000
        db.transactionDao().insertTransaction(
            TransactionEntity(type = "INCOME", amount = 5000.0, accountId = accId, timestamp = t2)
        )
        // 3. Transfer between own accounts: 3000 (must NOT be counted as expense)
        db.transactionDao().insertTransaction(
            TransactionEntity(type = "TRANSFER", amount = 3000.0, accountId = accId, toAccountId = acc2Id, timestamp = t3)
        )
        // 4. Another Expense: 450
        db.transactionDao().insertTransaction(
            TransactionEntity(type = "EXPENSE", amount = 450.0, accountId = accId, timestamp = t4)
        )

        // Outside period:
        db.transactionDao().insertTransaction(
            TransactionEntity(type = "EXPENSE", amount = 9999.0, accountId = accId, timestamp = tOutside)
        )

        val summary = db.transactionDao().getPeriodSummary(1000L, 5000L).first()

        // Total count should be 4 operations (expense, income, transfer, expense)
        assertEquals(4, summary.totalCount)
        // Total expense should be 1500 + 450 = 1950.0 (income and transfer excluded!)
        assertEquals(1950.0, summary.totalExpense, 0.001)

        val allSummary = db.transactionDao().getAllTimeSummary().first()
        // Total count all time should be 5
        assertEquals(5, allSummary.totalCount)
        // Total expense all time should be 1500 + 450 + 9999 = 11949.0
        assertEquals(11949.0, allSummary.totalExpense, 0.001)

        // Test paged list
        val pagedList = db.transactionDao().getTransactionsBetweenPaged(1000L, 5000L, limit = 2).first()
        assertEquals(2, pagedList.size)
        // First item should be newest timestamp (4000L)
        assertEquals(t4, pagedList[0].timestamp)
        assertEquals(t3, pagedList[1].timestamp)
    }
}
