package com.lexi.wordbook.ai

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** One persisted chat turn. */
@Serializable
data class StoredMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String = ChatMessage.USER,
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val error: Boolean = false,
    /** True while waiting for the model; the bubble renders a typing animation. */
    val pending: Boolean = false
)

/** One conversation. Users can keep several side by side (「雅思口语」「阅读生词」…). */
@Serializable
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<StoredMessage> = emptyList()
) {
    val displayTitle: String
        get() = title.ifBlank { "新对话" }

    /** Number of turns the user actually sent. */
    val userTurnCount: Int
        get() = messages.count { it.role == ChatMessage.USER }

    val preview: String
        get() = messages.lastOrNull { it.content.isNotBlank() && !it.pending }?.content
            ?.replace('\n', ' ')
            ?.take(60)
            .orEmpty()
}

/**
 * Keeps every AI conversation on device. Sessions are ordered newest-first for the history
 * list; [activeId] remembers which one the chat screen should show.
 */
class ChatRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lexi_chat", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _sessions = MutableStateFlow(loadSessions())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _messages = MutableStateFlow<List<StoredMessage>>(emptyList())

    /** Messages of the active session (empty when no session exists yet). */
    val messages: StateFlow<List<StoredMessage>> = _messages.asStateFlow()

    private val _activeId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE, null)?.takeIf { id -> _sessions.value.any { it.id == id } }
    )
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    init {
        publishActive()
    }

    // ------------------------------------------------------------------ loading

    /** Migrates the pre-1.2 single transcript ("messages_json_v1") into a session. */
    private fun loadSessions(): List<ChatSession> {
        val raw = prefs.getString(KEY_SESSIONS, null)
        if (raw != null) {
            return runCatching { json.decodeFromString<List<ChatSession>>(raw) }
                .getOrElse { emptyList() }
                .sortedByDescending { it.updatedAt }
        }
        val legacy = prefs.getString(KEY_LEGACY_MESSAGES, null) ?: return emptyList()
        val messages = runCatching { json.decodeFromString<List<StoredMessage>>(legacy) }
            .getOrElse { emptyList() }
        if (messages.isEmpty()) return emptyList()
        val session = ChatSession(
            title = "历史对话",
            createdAt = messages.first().createdAt,
            updatedAt = messages.last().createdAt,
            messages = messages
        )
        persistSessions(listOf(session))
        prefs.edit {
            remove(KEY_LEGACY_MESSAGES)
            putString(KEY_ACTIVE, session.id)
        }
        return listOf(session)
    }

    private fun persistSessions(list: List<ChatSession>) {
        val sorted = list.sortedByDescending { it.updatedAt }
        _sessions.value = sorted
        runCatching { prefs.edit { putString(KEY_SESSIONS, json.encodeToString(sorted)) } }
    }

    private fun mutateActive(transform: (ChatSession) -> ChatSession) {
        val id = _activeId.value ?: return
        persistSessions(
            _sessions.value.map { if (it.id == id) transform(it).copy(updatedAt = now()) else it }
        )
        publishActive()
    }

    private fun now() = System.currentTimeMillis()

    private fun publishActive() {
        val session = _sessions.value.firstOrNull { it.id == _activeId.value }
        _messages.value = session?.messages.orEmpty()
    }

    // ------------------------------------------------------------------ sessions

    /** Creates a session and makes it active. */
    fun newSession(title: String = ""): ChatSession {
        val session = ChatSession(title = title)
        persistSessions(_sessions.value + session)
        setActive(session.id)
        return session
    }

    fun setActive(id: String?) {
        _activeId.value = id
        prefs.edit { putString(KEY_ACTIVE, id) }
        publishActive()
    }

    /** Makes sure there is something to write into, creating a session on first use. */
    fun ensureActiveSession(): ChatSession {
        val existing = _sessions.value.firstOrNull { it.id == _activeId.value }
        return existing ?: newSession()
    }

    fun renameSession(id: String, title: String) {
        persistSessions(_sessions.value.map { if (it.id == id) it.copy(title = title) else it })
        if (id == _activeId.value) publishActive()
    }

    fun deleteSession(id: String) {
        val remaining = _sessions.value.filterNot { it.id == id }
        persistSessions(remaining)
        if (_activeId.value == id) setActive(remaining.firstOrNull()?.id)
    }

    fun clearActiveMessages() {
        mutateActive { it.copy(messages = emptyList(), title = "") }
    }

    fun session(id: String): ChatSession? = _sessions.value.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ messages

    fun append(role: String, content: String, error: Boolean = false): StoredMessage {
        ensureActiveSession()
        val message = StoredMessage(role = role, content = content, error = error)
        mutateActive { session ->
            val autoTitle = if (
                session.title.isBlank() && role == ChatMessage.USER && content.isNotBlank()
            ) {
                content.replace('\n', ' ').take(24)
            } else {
                session.title
            }
            val trimmed = (session.messages + message).takeLast(MAX_MESSAGES_PER_SESSION)
            session.copy(title = autoTitle, messages = trimmed)
        }
        return message
    }

    fun appendPending(role: String = ChatMessage.ASSISTANT): StoredMessage {
        ensureActiveSession()
        val message = StoredMessage(role = role, content = "", pending = true)
        mutateActive { it.copy(messages = it.messages + message) }
        return message
    }

    /** Replaces the content of an existing message (used for the pending placeholder). */
    fun update(id: String, content: String, error: Boolean = false) {
        mutateActive { session ->
            session.copy(
                messages = session.messages.map {
                    if (it.id == id) it.copy(content = content, error = error, pending = false) else it
                }
            )
        }
    }

    fun remove(id: String) {
        mutateActive { session ->
            session.copy(messages = session.messages.filterNot { it.id == id })
        }
    }

    fun clear() {
        persistSessions(emptyList())
        setActive(null)
    }

    /** Drops any bubble still waiting for a reply (e.g. after an app restart). */
    fun dropPending() {
        if (_sessions.value.none { session -> session.messages.any { it.pending } }) return
        persistSessions(
            _sessions.value.map { session ->
                session.copy(messages = session.messages.filterNot { it.pending })
            }
        )
        publishActive()
    }

    fun exportText(sessionId: String? = null): String {
        val session = sessionId?.let { session(it) }
            ?: _sessions.value.firstOrNull { it.id == _activeId.value }
            ?: return ""
        return buildString {
            if (session.displayTitle.isNotBlank()) {
                append("【${session.displayTitle}】\n\n")
            }
            session.messages.forEach { message ->
                val who = when (message.role) {
                    ChatMessage.USER -> "我"
                    ChatMessage.ASSISTANT -> "AI"
                    else -> message.role
                }
                append("$who：${message.content}\n\n")
            }
        }.trim()
    }

    /** Every conversation concatenated, for a full backup. */
    fun exportAllText(): String = _sessions.value.joinToString("\n\n========\n\n") { session ->
        exportText(session.id)
    }

    companion object {
        private const val KEY_SESSIONS = "sessions_json_v1"
        private const val KEY_ACTIVE = "active_session_id"
        private const val KEY_LEGACY_MESSAGES = "messages_json_v1"
        const val MAX_MESSAGES_PER_SESSION = 200
    }
}
