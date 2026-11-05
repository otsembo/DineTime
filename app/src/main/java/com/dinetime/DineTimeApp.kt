package com.dinetime

import android.app.Application
import com.dinetime.core.SettingsStore
import com.dinetime.data.couchbase.CouchbaseProvider
import com.dinetime.data.couchbase.ReplicatorManager
import com.dinetime.data.reads.MealRepository
import com.dinetime.fakeserver.FakeServer
import com.dinetime.reminders.ReminderScheduler
import com.dinetime.sync.Reconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val couchbase = CouchbaseProvider(app)
    val repository = MealRepository(couchbase, settings)
    val replicators = ReplicatorManager(couchbase, settings, scope)
    val reconciler = Reconciler(repository, couchbase, settings, scope)
    val fakeServer = FakeServer(settings, scope)
    val reminders = ReminderScheduler(app, repository, scope)
}

class DineTimeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
