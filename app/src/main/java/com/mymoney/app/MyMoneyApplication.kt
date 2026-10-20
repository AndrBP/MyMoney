package com.mymoney.app

import android.app.Application
import com.mymoney.app.data.repository.CategoryRepository
import com.mymoney.app.ui.notifications.ReminderChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MyMoneyApplication : Application() {

    @Inject lateinit var categoryRepository: CategoryRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Seed default categories on first run (rule 5.4.15). Idempotent.
        appScope.launch { categoryRepository.ensureDefaults() }
        ReminderChannels.ensure(this)
    }
}
