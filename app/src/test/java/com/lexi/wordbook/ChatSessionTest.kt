package com.lexi.wordbook

import com.lexi.wordbook.ai.ChatMessage
import com.lexi.wordbook.ai.ChatSession
import com.lexi.wordbook.ai.StoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the multi-conversation chat model. */
class ChatSessionTest {

    private fun user(text: String) = StoredMessage(role = ChatMessage.USER, content = text)
    private fun ai(text: String) = StoredMessage(role = ChatMessage.ASSISTANT, content = text)

    @Test
    fun `blank titles fall back to a placeholder`() {
        assertEquals("新对话", ChatSession(title = "").displayTitle)
        assertEquals("雅思口语", ChatSession(title = "雅思口语").displayTitle)
    }

    @Test
    fun `turn count only counts user messages`() {
        val session = ChatSession(
            messages = listOf(user("a"), ai("b"), user("c"), ai("d"))
        )
        assertEquals(2, session.userTurnCount)
    }

    @Test
    fun `preview uses the newest non-empty reply`() {
        val session = ChatSession(
            messages = listOf(user("第一句"), ai("很长的回答\n换行也要压平"))
        )
        assertEquals("很长的回答 换行也要压平", session.preview)

        val withPending = ChatSession(
            messages = listOf(user("问题"), ai("答案"), StoredMessage(role = ChatMessage.ASSISTANT, content = "", pending = true))
        )
        assertEquals("答案", withPending.preview)
    }

    @Test
    fun `preview is truncated`() {
        val long = "x".repeat(200)
        val session = ChatSession(messages = listOf(ai(long)))
        assertEquals(60, session.preview.length)
    }

    @Test
    fun `preview is empty for a fresh session`() {
        assertTrue(ChatSession().preview.isEmpty())
        assertEquals(0, ChatSession().userTurnCount)
    }

    @Test
    fun `messages default to user role and unique ids`() {
        val a = StoredMessage()
        val b = StoredMessage()
        assertEquals(ChatMessage.USER, a.role)
        assertFalse(a.id == b.id)
        assertTrue(a.createdAt > 0)
    }
}
