package ai.peridot.app.data

import ai.peridot.app.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The Peridot REST API (the same one the website, desktop app and CLI use). */
class PeridotApi(
    private val tokenProvider: () -> String?,
    private val baseUrl: String = BuildConfig.API_URL.trimEnd('/'),
) {
    val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Answers stream for a long time; a long read timeout only matters between chunks
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    // ── Auth ──
    suspend fun login(email: String, password: String): AuthResponse =
        post("/auth/login", buildJsonObject { put("email", email); put("password", password) })

    /** Signs in with a Google ID token (issued for the website's OAuth client, see [authConfig]). */
    suspend fun googleLogin(idToken: String): AuthResponse =
        post("/auth/google", buildJsonObject { put("idToken", idToken) })

    suspend fun authConfig(): AuthConfig = get("/auth/config")

    suspend fun me(): MeResponse = get("/auth/me")

    // ── Models ──
    suspend fun models(): List<Model> = get<ModelsResponse>("/models").models

    // ── Chat history (signed in only) ──
    suspend fun conversations(): List<Conversation> = get<ConversationsResponse>("/conversations").conversations

    suspend fun conversation(id: Long): ConversationResponse = get("/conversations/$id")

    suspend fun createConversation(title: String): Conversation =
        post<CreatedConversation>("/conversations", buildJsonObject { put("title", title) }).conversation

    suspend fun addMessages(id: Long, messages: List<ApiMessage>) {
        send<JsonObject>("POST", "/conversations/$id/messages", buildJsonObject {
            putJsonArray("messages") { messages.forEach { add(json.encodeToJsonElement(ApiMessage.serializer(), it)) } }
        })
    }

    suspend fun renameConversation(id: Long, title: String) {
        send<JsonObject>("PATCH", "/conversations/$id", buildJsonObject { put("title", title) })
    }

    suspend fun deleteConversation(id: Long) {
        send<JsonObject>("DELETE", "/conversations/$id", null)
    }

    // ── Sharing (a public, read-only copy of a chat) ──
    suspend fun shareStatus(id: Long): ShareInfo? = get<ShareResponse>("/conversations/$id/share").share

    suspend fun share(id: Long): ShareInfo =
        send<ShareResponse>("POST", "/conversations/$id/share", null).share ?: throw ApiException("Could not share this chat", 500)

    suspend fun stopSharing(id: Long) {
        send<JsonObject>("DELETE", "/conversations/$id/share", null)
    }

    // ── Invites ──
    suspend fun referrals(): ReferralStats = get("/referrals")

    // ── Plans: the payment itself happens on Stripe's page in the browser ──
    suspend fun checkoutConfig(): CheckoutConfig = get("/checkout/config")

    suspend fun checkoutOffer(): CheckoutOffer = get("/checkout/offer")

    /** Stripe's checkout page for the plan; the consents were given in the app. */
    suspend fun checkoutSession(plan: String, lang: String): String =
        post<CheckoutSession>("/checkout/session", buildJsonObject {
            put("plan", plan)
            put("lang", lang)
            put("consent", buildJsonObject { put("terms", true); put("immediateStart", true) })
        }).url

    // ── Streaming chat ──
    /**
     * Streams one answer as text chunks. Cancelling the collector closes the connection, which
     * also stops the model on the server.
     */
    fun stream(messages: List<ApiMessage>, model: String?, effort: Effort): Flow<String> = flow {
        val body = buildJsonObject {
            putJsonArray("messages") { messages.forEach { add(json.encodeToJsonElement(ApiMessage.serializer(), it)) } }
            if (model != null) put("model", model)
            put("effort", effort.apiValue)
        }
        val call = client.newCall(request("POST", "/chat/stream", body).header("Accept", "text/event-stream").build())
        val stop = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw errorFrom(response)
                val source = response.body?.source() ?: throw ApiException("Empty response", response.code)
                val parser = SseParser()
                while (true) {
                    val line = source.readUtf8Line()
                    val event = if (line == null) parser.finish() else parser.feed(line)
                    if (event != null) {
                        when (event.event) {
                            "error" -> throw ApiException(
                                cleanError(runCatching { json.decodeFromString<StreamChunk>(event.data).error }.getOrNull())
                                    ?: "Streaming error",
                                response.code,
                            )
                            "done" -> return@use
                            "message" -> json.decodeFromString<StreamChunk>(event.data).chunk?.let { emit(it) }
                        }
                    }
                    if (line == null) break
                }
            }
        } finally {
            stop.dispose()
        }
    }.flowOn(Dispatchers.IO)

    // ── Plumbing ──
    private fun request(method: String, path: String, body: JsonObject?): Request.Builder {
        val builder = Request.Builder().url(baseUrl + path)
        tokenProvider()?.let { builder.header("Authorization", "Bearer $it") }
        val requestBody = body?.let { json.encodeToString(it).toRequestBody(JSON) }
        return builder.method(method, requestBody ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(JSON))
    }

    private suspend inline fun <reified T> get(path: String): T = send("GET", path, null)

    private suspend inline fun <reified T> post(path: String, body: JsonObject): T = send("POST", path, body)

    private suspend inline fun <reified T> send(method: String, path: String, body: JsonObject?): T {
        val text = execute(request(method, path, body).build())
        return json.decodeFromString<T>(text.ifBlank { "{}" })
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) cont.resume(it.body?.string().orEmpty())
                    else cont.resumeWithException(errorFrom(it))
                }
            }
        })
    }

    private fun errorFrom(response: Response): ApiException {
        val body = runCatching { json.decodeFromString<ErrorBody>(response.body?.string().orEmpty()) }.getOrNull()
        return ApiException(cleanError(body?.error) ?: "Request failed (${response.code})", response.code, body?.code)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** Provider errors sometimes arrive as raw JSON ({"error":{"message":…}}); keep just the message. */
internal fun cleanError(message: String?): String? {
    if (message.isNullOrBlank()) return null
    val start = message.indexOf('{')
    if (start == -1) return message
    return runCatching {
        val obj = Json.parseToJsonElement(message.substring(start)) as JsonObject
        val inner = obj["error"]
        val text = when (inner) {
            is JsonObject -> (inner["message"] as? JsonPrimitive)?.content
            is JsonPrimitive -> inner.content
            else -> null
        } ?: (obj["message"] as? JsonPrimitive)?.content
        text ?: message
    }.getOrDefault(message)
}
