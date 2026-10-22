package ai.peridot.app.ui

import ai.peridot.app.R
import ai.peridot.app.data.ApiException
import ai.peridot.app.data.ApiMessage
import ai.peridot.app.data.Conversation
import ai.peridot.app.data.Effort
import ai.peridot.app.data.Limits
import ai.peridot.app.data.Model
import ai.peridot.app.data.PeridotApi
import ai.peridot.app.data.Settings
import ai.peridot.app.data.User
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Role { User, Model }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
    val error: String? = null,
)

data class ChatState(
    val user: User? = null,
    val limits: Limits? = null,
    val messages: List<ChatMessage> = emptyList(),
    val streaming: Boolean = false,
    val models: List<Model> = emptyList(),
    val model: String? = null,
    val effort: Effort = Effort.Medium,
    val conversations: List<Conversation> = emptyList(),
    val conversationId: Long? = null,
    val loadingConversation: Boolean = false,
    /** A one-off message for the snackbar. */
    val notice: String? = null,
) {
    val signedIn get() = user != null
    val currentModel get() = models.firstOrNull { it.id == model }
}

data class AuthState(val busy: Boolean = false, val error: String? = null)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val api = PeridotApi(tokenProvider = { token })
    private var token: String? = settings.token

    private val _state = MutableStateFlow(ChatState(effort = settings.effort))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _auth = MutableStateFlow(AuthState())
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private var streamJob: Job? = null
    private var nextId = 1L

    /**
     * Every chat on screen gets a serial number; work that finishes after the user moved to
     * another chat (a save, the end of a stream) checks it and leaves the new chat alone.
     */
    private var chatSerial = 0
    private val savedAs = mutableMapOf<Int, Long>()
    private val saveLock = Mutex()

    init {
        if (token != null) {
            // Show the account straight away from what we know; /auth/me fills in the rest
            _state.update { it.copy(user = User(id = 0, email = "")) }
            refreshAccount()
            loadConversations()
        }
        loadModels()
    }

    private fun string(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    // ── Account ──

    fun signIn(email: String, password: String, onDone: () -> Unit) =
        authenticate(onDone) { api.login(email.trim(), password) }

    /**
     * Signs in with Google. [idToken] shows the system's Google account picker for the given
     * OAuth client and returns the ID token, or null when the user closed it.
     */
    fun signInWithGoogle(idToken: suspend (serverClientId: String) -> String?, onDone: () -> Unit) =
        authenticate(onDone) {
            val clientId = api.authConfig().googleClientId
            if (clientId.isBlank()) throw ApiException(string(R.string.google_unavailable), 503)
            idToken(clientId)?.let { api.googleLogin(it) }
        }

    /** Runs a sign-in; [call] returns null when the user backed out, which is not an error. */
    private fun authenticate(onDone: () -> Unit, call: suspend () -> ai.peridot.app.data.AuthResponse?) {
        if (_auth.value.busy) return
        _auth.value = AuthState(busy = true)
        viewModelScope.launch {
            try {
                val result = call()
                if (result == null) {
                    _auth.value = AuthState()
                    return@launch
                }
                token = result.token
                settings.token = result.token
                _state.update { it.copy(user = result.user) }
                _auth.value = AuthState()
                onDone()
                refreshAccount()
                loadModels()
                loadConversations()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _auth.value = AuthState(error = describe(e))
            }
        }
    }

    fun clearAuthError() {
        _auth.update { it.copy(error = null) }
    }

    fun signOut(expired: Boolean = false) {
        token = null
        settings.token = null
        startOver()
        _state.update {
            it.copy(
                user = null,
                limits = null,
                conversations = emptyList(),
                notice = if (expired) string(R.string.session_expired) else null,
            )
        }
        loadModels()
    }

    fun refreshAccount() {
        if (token == null) return
        viewModelScope.launch {
            try {
                val me = api.me()
                _state.update { it.copy(user = me.user, limits = me.limits) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!expiredSession(e) && _state.value.user?.email.isNullOrEmpty()) {
                    // Offline at start-up: stay signed in, the next request will tell
                    _state.update { it.copy(notice = describe(e)) }
                }
            }
        }
    }

    // ── Models ──

    fun loadModels() {
        viewModelScope.launch {
            try {
                val models = api.models()
                _state.update { s ->
                    val saved = settings.model
                    val usable = models.filterNot { it.locked }
                    val pick = usable.firstOrNull { it.id == saved }?.id ?: usable.firstOrNull()?.id ?: s.model
                    s.copy(models = models, model = pick)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The picker stays empty; the server then answers with its default model
            }
        }
    }

    fun selectModel(model: Model) {
        if (model.locked) return
        settings.model = model.id
        _state.update { it.copy(model = model.id) }
    }

    fun selectEffort(effort: Effort) {
        settings.effort = effort
        _state.update { it.copy(effort = effort) }
    }

    // ── Chat history ──

    fun loadConversations() {
        if (token == null) return
        viewModelScope.launch {
            try {
                val list = api.conversations()
                _state.update { it.copy(conversations = list) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                expiredSession(e)
            }
        }
    }

    fun newChat() = startOver()

    private fun startOver() {
        streamJob?.cancel()
        streamJob = null
        chatSerial++
        _state.update { it.copy(messages = emptyList(), conversationId = null, streaming = false, loadingConversation = false) }
    }

    fun openConversation(conversation: Conversation) {
        if (conversation.id == _state.value.conversationId && !_state.value.loadingConversation) return
        startOver()
        val serial = chatSerial
        savedAs[serial] = conversation.id
        _state.update { it.copy(conversationId = conversation.id, loadingConversation = true) }
        viewModelScope.launch {
            try {
                val loaded = api.conversation(conversation.id)
                if (serial != chatSerial) return@launch
                val messages = loaded.messages.map {
                    ChatMessage(nextId++, if (it.role == "user") Role.User else Role.Model, it.content)
                }
                _state.update { it.copy(messages = messages, loadingConversation = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (serial != chatSerial) return@launch
                _state.update { it.copy(loadingConversation = false) }
                if (!expiredSession(e)) _state.update { it.copy(notice = describe(e)) }
            }
        }
    }

    fun renameConversation(conversation: Conversation, title: String) {
        val clean = title.trim().take(80)
        if (clean.isEmpty() || clean == conversation.title) return
        _state.update { s -> s.copy(conversations = s.conversations.map { if (it.id == conversation.id) it.copy(title = clean) else it }) }
        viewModelScope.launch {
            try {
                api.renameConversation(conversation.id, clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!expiredSession(e)) _state.update { it.copy(notice = describe(e)) }
                loadConversations()
            }
        }
    }

    fun deleteConversation(conversation: Conversation) {
        if (conversation.id == _state.value.conversationId) startOver()
        _state.update { s -> s.copy(conversations = s.conversations.filterNot { it.id == conversation.id }) }
        viewModelScope.launch {
            try {
                api.deleteConversation(conversation.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!expiredSession(e)) _state.update { it.copy(notice = describe(e)) }
                loadConversations()
            }
        }
    }

    // ── Sending ──

    fun send(text: String): Boolean {
        val question = text.trim()
        val s = _state.value
        if (question.isEmpty() || s.streaming || s.loadingConversation) return false
        val history = s.messages.filter { it.error == null && it.text.isNotBlank() }
        stream(history + ChatMessage(nextId++, Role.User, question), question)
        return true
    }

    /** Asks again after a failed answer (the failed one is dropped). */
    fun retry() {
        val s = _state.value
        if (s.streaming) return
        val last = s.messages.lastOrNull() ?: return
        if (last.role != Role.Model || last.error == null) return
        val history = s.messages.dropLast(1).filter { it.error == null && it.text.isNotBlank() }
        val question = history.lastOrNull { it.role == Role.User }?.text ?: return
        stream(history, question)
    }

    fun stop() {
        streamJob?.cancel()
    }

    private fun stream(history: List<ChatMessage>, question: String) {
        val serial = chatSerial
        val answerId = nextId++
        _state.update {
            it.copy(messages = history + ChatMessage(answerId, Role.Model, "", streaming = true), streaming = true)
        }
        val apiHistory = history.map { ApiMessage(if (it.role == Role.User) "user" else "model", it.text) }
        val s = _state.value
        streamJob = viewModelScope.launch {
            val answer = StringBuilder()
            var failure: String? = null
            try {
                api.stream(apiHistory, s.model, s.effort).collect { chunk ->
                    answer.append(chunk)
                    val text = answer.toString()
                    updateMessage(answerId) { it.copy(text = text) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = if (expiredSession(e)) null else describe(e)
            } finally {
                updateMessage(answerId) { it.copy(streaming = false, error = failure) }
                if (serial == chatSerial) _state.update { it.copy(streaming = false) }
                val text = answer.toString()
                // Like the CLI, an answer stopped half-way is kept and saved; a failed one is not,
                // so "Try again" doesn't leave the question in the history twice
                if (text.isNotBlank() && failure == null && token != null) {
                    viewModelScope.launch { save(serial, question, text) }
                }
                if (token != null) refreshAccount()
            }
        }
    }

    private fun updateMessage(id: Long, change: (ChatMessage) -> ChatMessage) {
        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == id) change(it) else it }) }
    }

    /** Saves one exchange to the account's history, creating the conversation on the first one. */
    private suspend fun save(serial: Int, question: String, answer: String) = saveLock.withLock {
        try {
            val id = savedAs[serial] ?: api.createConversation(question.replace(Regex("\\s+"), " ").take(60)).id.also { created ->
                savedAs[serial] = created
                if (serial == chatSerial) _state.update { it.copy(conversationId = created) }
            }
            api.addMessages(id, listOf(ApiMessage("user", question), ApiMessage("model", answer)))
            loadConversations()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // History is a convenience: a failed save never gets in the way of the chat
        }
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    // ── Errors ──

    /** Signs out when the server says the token is no longer valid. Returns true if it did. */
    private fun expiredSession(e: Exception): Boolean {
        if (e is ApiException && e.status == 401 && token != null) {
            signOut(expired = true)
            return true
        }
        return false
    }

    private fun describe(e: Exception): String = when (e) {
        is ApiException -> e.message ?: string(R.string.error_generic)
        is IOException -> string(R.string.error_network)
        else -> string(R.string.error_generic)
    }
}
