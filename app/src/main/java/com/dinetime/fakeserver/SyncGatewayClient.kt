package com.dinetime.fakeserver

import com.dinetime.core.AppSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SgHttpException(val code: Int, message: String) : Exception(message)

data class ChangesResult(val docs: List<JsonObject>, val lastSeq: String)

/** Minimal Sync Gateway admin REST client, used the way a real external server would. */
class SyncGatewayClient(private val settingsProvider: () -> AppSettings) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val media = "application/json".toMediaType()

    private fun keyspace(s: AppSettings, collection: String) = "${s.sgDatabase}.dinetime.$collection"

    private fun request(collection: String, path: String, build: (Request.Builder) -> Request.Builder, query: Map<String, String> = emptyMap()): Request {
        val s = settingsProvider()
        val url = "${s.adminHttpUrl}/${keyspace(s, collection)}/$path".toHttpUrl().newBuilder()
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        return build(Request.Builder().url(url).header("Authorization", Credentials.basic(s.adminUser, s.adminPassword))).build()
    }

    private fun execute(req: Request): String? = http.newCall(req).execute().use { r ->
        if (r.code == 404) return null
        val body = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw SgHttpException(r.code, "SG ${r.code}: $body")
        body
    }

    fun changes(collection: String, since: String): ChangesResult {
        val body = execute(
            request(collection, "_changes", { it.get() }, mapOf("since" to since, "include_docs" to "true", "feed" to "normal")),
        ) ?: return ChangesResult(emptyList(), since)
        val root = json.parseToJsonElement(body).jsonObject
        val docs = (root["results"] as? JsonArray).orEmpty().mapNotNull { row ->
            val o = row.jsonObject
            if (o["deleted"]?.jsonPrimitive?.content == "true") null else o["doc"] as? JsonObject
        }
        return ChangesResult(docs, root["last_seq"]?.jsonPrimitive?.content ?: since)
    }

    fun get(collection: String, id: String): JsonObject? =
        execute(request(collection, id, { it.get() }))?.let { json.parseToJsonElement(it).jsonObject }

    /** Creates or updates a document, resolving the current revision itself. */
    fun upsert(collection: String, id: String, doc: JsonObject) {
        repeat(3) {
            val rev = get(collection, id)?.get("_rev")?.jsonPrimitive?.content
            val body = JsonObject(doc.filterKeys { !it.startsWith("_") } + (if (rev != null) mapOf("_rev" to JsonPrimitive(rev)) else emptyMap()))
            try {
                execute(request(collection, id, { it.put(body.toString().toRequestBody(media)) }))
                return
            } catch (e: SgHttpException) {
                if (e.code != 409) throw e
            }
        }
        throw SgHttpException(409, "Could not update $id after retries")
    }

    /** Writes only if the stored revision still equals [expectedRev]; false when someone else changed it. */
    fun putIfRev(collection: String, id: String, doc: JsonObject, expectedRev: String): Boolean {
        val body = JsonObject(doc.filterKeys { !it.startsWith("_") } + ("_rev" to JsonPrimitive(expectedRev)))
        return try {
            execute(request(collection, id, { it.put(body.toString().toRequestBody(media)) }))
            true
        } catch (e: SgHttpException) {
            if (e.code == 409) false else throw e
        }
    }

    fun delete(collection: String, id: String) {
        val rev = get(collection, id)?.get("_rev")?.jsonPrimitive?.content ?: return
        execute(request(collection, id, { it.delete() }, mapOf("rev" to rev)))
    }
}
