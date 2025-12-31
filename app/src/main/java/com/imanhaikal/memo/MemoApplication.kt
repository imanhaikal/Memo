package com.imanhaikal.memo

import android.app.Application
import com.imanhaikal.memo.data.AppDatabase
import com.imanhaikal.memo.data.BudgetPreferences
import com.imanhaikal.memo.data.TransactionDao

class MemoApplication : Application() {

    // AppContainer instance used by the rest of classes to obtain dependencies
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

interface AppContainer {
    val transactionDao: TransactionDao
    val budgetPreferences: BudgetPreferences
}

class DefaultAppContainer(private val context: Application) : AppContainer {
    override val transactionDao: TransactionDao by lazy {
        AppDatabase.getDatabase(context).transactionDao()
    }

    override val budgetPreferences: BudgetPreferences by lazy {
        BudgetPreferences(context)
    }
}