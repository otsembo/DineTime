package com.dinetime.data.couchbase

import com.couchbase.lite.BasicAuthenticator
import com.couchbase.lite.CollectionConfiguration
import com.couchbase.lite.Replicator
import com.couchbase.lite.ReplicatorActivityLevel
import com.couchbase.lite.ReplicatorConfiguration
import com.couchbase.lite.ReplicatorType
import com.couchbase.lite.URLEndpoint
import com.dinetime.core.AppSettings
import com.dinetime.core.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.net.URI

data class SyncState(
    val enabled: Boolean = false,
    val readsActivity: String = "STOPPED",
    val writesActivity: String = "STOPPED",
    val error: String? = null,
) {
    val connected get() = enabled && (readsActivity != "OFFLINE" && readsActivity != "STOPPED") &&
        (writesActivity != "OFFLINE" && writesActivity != "STOPPED")
}

/**
 * Runs two continuous replicators over WebSockets: writes (push+pull so the server
 * acknowledgements come back) and reads (pull only; temp docs are also filtered from push).
 */
class ReplicatorManager(
    private val db: CouchbaseProvider,
    private val settings: SettingsStore,
    scope: CoroutineScope,
) {
    private var readsRepl: Replicator? = null
    private var writesRepl: Replicator? = null
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    init {
        scope.launch {
            settings.settings
                .distinctUntilChangedBy {
                    listOf(it.syncEnabled, it.publicWsUrl, it.userName, it.userPassword)
                }
                .collect { restart(it) }
        }
    }

    @Synchronized
    private fun restart(s: AppSettings) {
        stopInternal()
        if (!s.syncEnabled) {
            _state.value = SyncState(enabled = false)
            return
        }
        val auth = BasicAuthenticator(s.userName, s.userPassword.toCharArray())
        val endpoint = URLEndpoint(URI(s.publicWsUrl))

        val reads = buildReplicator(
            endpoint, auth, ReplicatorType.PULL,
            CollectionConfiguration(db.meals).setPushFilter { doc, _ -> !doc.getBoolean("isTemp") },
        )
        val writes = buildReplicator(endpoint, auth, ReplicatorType.PUSH_AND_PULL, CollectionConfiguration(db.commands))

        reads.addChangeListener { c ->
            _state.value = _state.value.copy(
                enabled = true,
                readsActivity = c.status.activityLevel.name,
                error = c.status.error?.message,
            )
        }
        writes.addChangeListener { c ->
            _state.value = _state.value.copy(
                enabled = true,
                writesActivity = c.status.activityLevel.name,
                error = c.status.error?.message,
            )
        }
        readsRepl = reads
        writesRepl = writes
        _state.value = SyncState(enabled = true, readsActivity = ReplicatorActivityLevel.CONNECTING.name, writesActivity = ReplicatorActivityLevel.CONNECTING.name)
        reads.start()
        writes.start()
    }

    private fun buildReplicator(
        endpoint: URLEndpoint,
        auth: BasicAuthenticator,
        type: ReplicatorType,
        collectionConfig: CollectionConfiguration,
    ): Replicator {
        val cfg = ReplicatorConfiguration(listOf(collectionConfig), endpoint)
            .setType(type)
            .setContinuous(true)
            .setAuthenticator(auth)
        return Replicator(cfg)
    }

    private fun stopInternal() {
        readsRepl?.stop()
        writesRepl?.stop()
        readsRepl = null
        writesRepl = null
    }
}
