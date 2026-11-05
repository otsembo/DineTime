package com.dinetime.data.couchbase

import android.content.Context
import com.couchbase.lite.Collection
import com.couchbase.lite.CouchbaseLite
import com.couchbase.lite.DataSource
import com.couchbase.lite.Database
import com.couchbase.lite.DatabaseConfiguration
import com.couchbase.lite.Meta
import com.couchbase.lite.MutableDocument
import com.couchbase.lite.QueryBuilder
import com.couchbase.lite.SelectResult
import com.dinetime.domain.CommandDoc
import com.dinetime.domain.DocJson
import com.dinetime.domain.MealDoc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val SCOPE = "dinetime"
const val MEALS = "meals"
const val MEAL_COMMANDS = "meal_commands"

/** Owns the two CQRS databases: reads (queries/UI) and writes (commands). */
class CouchbaseProvider(context: Context) {
    val readsDb: Database
    val writesDb: Database
    val meals: Collection
    val commands: Collection

    init {
        CouchbaseLite.init(context.applicationContext)
        val cfg = DatabaseConfiguration()
        readsDb = Database("dinetime_reads", cfg)
        writesDb = Database("dinetime_writes", cfg)
        meals = readsDb.createCollection(MEALS, SCOPE)
        commands = writesDb.createCollection(MEAL_COMMANDS, SCOPE)
    }
}

/** Emits all documents of the collection (decoded) whenever any of them changes. */
fun <T> Collection.observeAll(decode: (String) -> T?): Flow<List<T>> = callbackFlow {
    val query = QueryBuilder
        .select(SelectResult.expression(Meta.id), SelectResult.expression(Meta.sequence))
        .from(DataSource.collection(this@observeAll))
    val token = query.addChangeListener { change ->
        val rows = change.results?.allResults() ?: return@addChangeListener
        val docs = rows.mapNotNull { row ->
            val id = row.getString(0) ?: return@mapNotNull null
            getDocument(id)?.toJSON()?.let(decode)
        }
        trySend(docs)
    }
    awaitClose { token.remove() }
}

fun Collection.saveJson(id: String, json: String) {
    save(MutableDocument(id, json))
}

fun Collection.saveMeal(meal: MealDoc) = saveJson(meal.id, DocJson.encodeToString(MealDoc.serializer(), meal))

fun Collection.saveCommand(cmd: CommandDoc) =
    saveJson(cmd.id, DocJson.encodeToString(CommandDoc.serializer(), cmd))

fun Collection.getMeal(id: String): MealDoc? =
    getDocument(id)?.toJSON()?.let { runCatching { DocJson.decodeFromString(MealDoc.serializer(), it) }.getOrNull() }

fun Collection.getCommand(id: String): CommandDoc? =
    getDocument(id)?.toJSON()?.let { runCatching { DocJson.decodeFromString(CommandDoc.serializer(), it) }.getOrNull() }

fun Collection.deleteById(id: String) {
    getDocument(id)?.let { delete(it) }
}
