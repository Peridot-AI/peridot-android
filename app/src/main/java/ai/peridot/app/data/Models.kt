package ai.peridot.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A chat message as the API stores it. The assistant's role is "model". */
@Serializable
data class ApiMessage(val role: String, val content: String)

@Serializable
data class User(
    val id: Long,
    val email: String,
    val name: String? = null,
    val role: String = "user",
    val avatar: String? = null,
    val plan: String? = null,
)

@Serializable
data class AuthResponse(val token: String, val user: User)

/** Public sign-in config; Google ID tokens must be requested for [googleClientId]. */
@Serializable
data class AuthConfig(val googleClientId: String = "")

@Serializable
data class Limits(
    val plan: String = "free",
    val unlimited: Boolean = false,
    val monthlyPercent: Int = 0,
    val dailyUsed: Int = 0,
    val dailyLimit: Int = 0,
)

@Serializable
data class MeResponse(val user: User, val limits: Limits? = null)

@Serializable
data class Model(
    val id: String,
    val name: String,
    val badge: String? = null,
    val desc: String = "",
    val locked: Boolean = false,
)

@Serializable
data class ModelsResponse(val models: List<Model> = emptyList())

@Serializable
data class Conversation(
    val id: Long,
    val title: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ConversationsResponse(val conversations: List<Conversation> = emptyList())

@Serializable
data class ConversationResponse(val conversation: Conversation, val messages: List<ApiMessage> = emptyList())

@Serializable
data class CreatedConversation(val conversation: Conversation)

@Serializable
data class ErrorBody(val error: String? = null, val code: String? = null)

@Serializable
data class StreamChunk(val chunk: String? = null, val error: String? = null)

/** Reasoning effort; the server caps it to what the plan allows. */
enum class Effort(val apiValue: String) { Low("Low"), Medium("Medium"), High("High") }

/** An error from the API, with the server's message (already user-facing) and code. */
class ApiException(message: String, val status: Int, val code: String? = null) : Exception(message)
