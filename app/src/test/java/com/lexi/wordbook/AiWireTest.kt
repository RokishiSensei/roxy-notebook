package com.lexi.wordbook

import android.app.Application
import com.lexi.wordbook.ai.AiApiFormat
import com.lexi.wordbook.ai.AiConfig
import com.lexi.wordbook.ai.AiWire
import com.lexi.wordbook.ai.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wire-level rules for the AI client.
 *
 * Two reported bugs are pinned here:
 * 1. "获取在线模型" returned the wrong entries / failed when the address field held a full
 *    `…/v1/chat/completions` URL (the `/models` path was appended to it verbatim), and the
 *    human-readable `name` field was collected as if it were a model id.
 * 2. Using a model then failed with HTTP 400: reasoning models reject `temperature`, o-series /
 *    gpt-5 renamed `max_tokens`, and consecutive same-role messages are refused.
 */
// org.json is part of the Android SDK and is only stubbed in plain JVM tests, so this suite
// runs under Robolectric to get the real implementation (same setup as ImportFormatTest).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiWireTest {

    private val openAi = AiConfig(
        apiFormat = AiApiFormat.OPENAI,
        baseUrl = "https://api.deepseek.com/v1/chat/completions",
        model = "deepseek-chat"
    )

    // ---------------------------------------------------------------- endpoints

    @Test
    fun `a full chat endpoint is reduced before models is appended`() {
        // This is the reported bug: …/v1/chat/completions/models returned 404.
        assertEquals(
            "https://api.deepseek.com/v1/models",
            AiWire.modelsEndpoint(AiApiFormat.OPENAI, "https://api.deepseek.com/v1/chat/completions")
        )
        assertEquals(
            "https://api.openai.com/v1/models",
            AiWire.modelsEndpoint(AiApiFormat.OPENAI, "https://api.openai.com/v1/")
        )
        assertEquals(
            "https://api.anthropic.com/v1/models",
            AiWire.modelsEndpoint(AiApiFormat.ANTHROPIC, "https://api.anthropic.com/v1/messages")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
            AiWire.modelsEndpoint(
                AiApiFormat.GEMINI,
                "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
            )
        )
    }

    @Test
    fun `model paths already inside the address are preserved`() {
        assertEquals(
            "https://gw.example.com/openai/v1/models",
            AiWire.modelsEndpoint(AiApiFormat.OPENAI, "https://gw.example.com/openai/v1")
        )
        assertEquals(
            "https://gw.example.com/openai/v1/chat/completions",
            AiWire.chatEndpoint(AiApiFormat.OPENAI, "https://gw.example.com/openai/v1", "gpt-4o")
        )
    }

    @Test
    fun `chat endpoint rebuilds the right path for every format`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiWire.chatEndpoint(AiApiFormat.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat")
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiWire.chatEndpoint(
                AiApiFormat.OPENAI,
                "https://api.deepseek.com/v1/chat/completions/",
                "deepseek-chat"
            )
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            AiWire.chatEndpoint(AiApiFormat.ANTHROPIC, "https://api.anthropic.com", "claude-3-5-haiku-latest")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
            AiWire.chatEndpoint(
                AiApiFormat.GEMINI,
                "https://generativelanguage.googleapis.com",
                "gemini-2.0-flash"
            )
        )
        // A pasted generateContent URL keeps its version but takes the configured model.
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent",
            AiWire.chatEndpoint(
                AiApiFormat.GEMINI,
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-old:generateContent",
                "gemini-2.5-pro"
            )
        )
    }

    // ---------------------------------------------------------------- model list

    @Test
    fun `model ids are read from id and not from the display name`() {
        val body = """
            {"object":"list","data":[
              {"id":"gpt-4o-mini","object":"model","name":"GPT-4o Mini"},
              {"id":"o3-mini","object":"model","name":"o3 Mini (reasoning)"}
            ]}
        """.trimIndent()
        assertEquals(listOf("gpt-4o-mini", "o3-mini"), AiWire.parseModelIds(body))
    }

    @Test
    fun `gemini names are unwrapped and bare model fields are accepted`() {
        val gemini = """
            {"models":[{"name":"models/gemini-2.0-flash"},{"name":"models/gemini-1.5-pro"}]}
        """.trimIndent()
        assertEquals(listOf("gemini-1.5-pro", "gemini-2.0-flash"), AiWire.parseModelIds(gemini))

        val gateway = """{"data":[{"model":"qwen-max"}]}"""
        assertEquals(listOf("qwen-max"), AiWire.parseModelIds(gateway))

        val nested = """{"result":{"data":[{"id":"glm-4"}]}}"""
        assertEquals(listOf("glm-4"), AiWire.parseModelIds(nested))
    }

    @Test
    fun `non chat models are filtered out of the picker`() {
        assertFalse(AiWire.isChatModel("text-embedding-3-small"))
        assertFalse(AiWire.isChatModel("whisper-1"))
        assertFalse(AiWire.isChatModel("tts-1-hd"))
        assertFalse(AiWire.isChatModel("dall-e-3"))
        assertFalse(AiWire.isChatModel("omni-moderation-latest"))
        assertFalse(AiWire.isChatModel("gpt-4o-realtime-preview"))
        assertFalse(AiWire.isChatModel("bge-large-zh"))

        assertTrue(AiWire.isChatModel("gpt-4o"))
        assertTrue(AiWire.isChatModel("deepseek-chat"))
        assertTrue(AiWire.isChatModel("gemini-2.0-flash"))
        assertTrue(AiWire.isChatModel("claude-3-5-haiku-latest"))
        assertTrue(AiWire.isChatModel("qwen2.5-72b-instruct"))
    }

    // ---------------------------------------------------------------- payload

    @Test
    fun `reasoning models are not sent a temperature`() {
        val config = openAi.copy(model = "o3-mini", temperature = 0.7f)
        val payload = JSONObject(AiWire.payload(AiApiFormat.OPENAI, config, listOf(user("hi"))))
        assertFalse(payload.has("temperature"))
        // o-series wants max_completion_tokens.
        assertTrue(payload.has("max_completion_tokens"))
        assertFalse(payload.has("max_tokens"))

        val deepseek = JSONObject(
            AiWire.payload(
                AiApiFormat.OPENAI,
                openAi.copy(model = "deepseek-reasoner"),
                listOf(user("hi"))
            )
        )
        assertFalse(deepseek.has("temperature"))
        // Not an o-series name, so the classic field is kept.
        assertTrue(deepseek.has("max_tokens"))
    }

    @Test
    fun `regular models keep temperature and max_tokens`() {
        val payload = JSONObject(AiWire.payload(AiApiFormat.OPENAI, openAi, listOf(user("hi"))))
        assertEquals("deepseek-chat", payload.getString("model"))
        assertEquals(0.3, payload.getDouble("temperature"), 0.0001)
        assertEquals(openAi.maxTokens, payload.getInt("max_tokens"))
        assertFalse(payload.getBoolean("stream"))
    }

    @Test
    fun `consecutive same role messages are merged`() {
        val messages = listOf(
            ChatMessage(ChatMessage.SYSTEM, "你是英语老师"),
            user("第一句"),
            user("第二句"),
            ChatMessage(ChatMessage.ASSISTANT, "回答"),
            ChatMessage(ChatMessage.ASSISTANT, "补充")
        )
        val (system, conversation) = AiWire.normalize(messages)
        assertEquals("你是英语老师", system)
        // The system prompt is returned separately, so only user + assistant remain.
        assertEquals(2, conversation.size)
        assertEquals(ChatMessage.USER, conversation[0].role)
        assertEquals("第一句\n\n第二句", conversation[0].content)
        assertEquals(ChatMessage.ASSISTANT, conversation[1].role)
        assertEquals("回答\n\n补充", conversation[1].content)
    }

    @Test
    fun `blank messages are dropped so the array is never empty`() {
        val (_, conversation) = AiWire.normalize(listOf(user("   "), user(""), user("真问题")))
        assertEquals(1, conversation.size)
        assertEquals("真问题", conversation.single().content)
    }

    @Test
    fun `later attempts fold the system prompt into the first user turn`() {
        val messages = listOf(ChatMessage(ChatMessage.SYSTEM, "规则"), user("问题"))
        // Attempt 0 keeps a real system message…
        val first = JSONObject(AiWire.payload(AiApiFormat.OPENAI, openAi, messages, attempt = 0))
        val firstRoles = first.getJSONArray("messages").let { array ->
            (0 until array.length()).map { array.getJSONObject(it).getString("role") }
        }
        assertEquals(listOf("system", "user"), firstRoles)

        // …attempt 2 folds it in, for providers that reject the system role.
        val folded = JSONObject(AiWire.payload(AiApiFormat.OPENAI, openAi, messages, attempt = 2))
        val foldedArray = folded.getJSONArray("messages")
        assertEquals(1, foldedArray.length())
        assertEquals("user", foldedArray.getJSONObject(0).getString("role"))
        assertTrue(foldedArray.getJSONObject(0).getString("content").contains("规则"))

        // Gemini uses systemInstruction instead.
        val gemini = JSONObject(
            AiWire.payload(
                AiApiFormat.GEMINI,
                openAi.copy(apiFormat = AiApiFormat.GEMINI, model = "gemini-2.0-flash"),
                messages,
                attempt = 2
            )
        )
        assertFalse(gemini.has("systemInstruction"))
        assertEquals("user", gemini.getJSONArray("contents").getJSONObject(0).getString("role"))
    }

    @Test
    fun `gemini payload maps assistant to model and always sets output tokens`() {
        val config = openAi.copy(
            apiFormat = AiApiFormat.GEMINI,
            model = "gemini-2.0-flash",
            maxTokens = 0
        )
        val payload = JSONObject(
            AiWire.payload(
                AiApiFormat.GEMINI,
                config,
                listOf(ChatMessage(ChatMessage.SYSTEM, "sys"), user("q"), assistant("a"))
            )
        )
        val contents = payload.getJSONArray("contents")
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals(AiConfig.DEFAULT_MAX_TOKENS, payload.getJSONObject("generationConfig").getInt("maxOutputTokens"))
        // temperature is inside generationConfig for Gemini
        assertTrue(payload.getJSONObject("generationConfig").has("temperature"))
    }

    @Test
    fun `anthropic payload never leaks a system message into messages`() {
        val config = openAi.copy(apiFormat = AiApiFormat.ANTHROPIC, model = "claude-3-5-haiku-latest")
        val payload = JSONObject(
            AiWire.payload(
                AiApiFormat.ANTHROPIC,
                config,
                listOf(ChatMessage(ChatMessage.SYSTEM, "sys"), user("q"))
            )
        )
        assertEquals("sys", payload.getString("system"))
        val messages = payload.getJSONArray("messages")
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
    }

    // ---------------------------------------------------------------- retry + errors

    @Test
    fun `only parameter shaped errors are retried`() {
        assertTrue(
            AiWire.isRetryableParameterError(
                400,
                "Unsupported value: 'temperature' does not support 0.3 with this model"
            )
        )
        assertTrue(
            AiWire.isRetryableParameterError(
                400,
                "Unrecognized request argument supplied: max_tokens. Use max_completion_tokens."
            )
        )
        assertTrue(AiWire.isRetryableParameterError(400, "systemInstruction is not supported"))
        // A plain bad-model error is not a parameter problem.
        assertFalse(AiWire.isRetryableParameterError(400, "The model `gpt-9` does not exist"))
        assertFalse(AiWire.isRetryableParameterError(401, "Invalid API key"))
        assertFalse(AiWire.isRetryableParameterError(429, "rate limited"))
    }

    @Test
    fun `display names are recognised as invalid model ids`() {
        assertTrue(AiWire.looksLikeInvalidModel(""))
        assertTrue(AiWire.looksLikeInvalidModel("GPT-4o Mini"))
        assertTrue(AiWire.looksLikeInvalidModel("https://api.openai.com/v1"))
        assertTrue(AiWire.looksLikeInvalidModel("gemini-2.0-flash:"))
        assertFalse(AiWire.looksLikeInvalidModel("gpt-4o-mini"))
        assertFalse(AiWire.looksLikeInvalidModel("deepseek-chat"))
        assertFalse(AiWire.looksLikeInvalidModel("meta-llama/Llama-3.3-70B-Instruct"))
    }

    // ---------------------------------------------------------------- response parsing

    @Test
    fun `text and errors are read from every provider shape`() {
        assertEquals(
            "你好",
            AiWire.extractText("""{"choices":[{"message":{"content":"你好"}}]}""")
        )
        assertEquals(
            "hi",
            AiWire.extractText("""{"choices":[{"text":"hi"}]}""")
        )
        assertEquals(
            "claude 回答",
            AiWire.extractText("""{"content":[{"type":"text","text":"claude 回答"}]}""")
        )
        assertEquals(
            "gemini 回答",
            AiWire.extractText("""{"candidates":[{"content":{"parts":[{"text":"gemini 回答"}]}}]}""")
        )
        assertEquals("", AiWire.extractText("""{"candidates":[]}"""))
        assertEquals("", AiWire.extractText("not json"))

        assertEquals(
            "Invalid model",
            AiWire.extractError("""{"error":{"message":"Invalid model","type":"invalid_request_error"}}""")
        )
        assertEquals("bad", AiWire.extractError("""{"message":"bad"}"""))
    }

    @Test
    fun `model list result exposes how many entries were hidden`() {
        val body = """
            {"data":[
              {"id":"gpt-4o"},{"id":"text-embedding-3-small"},{"id":"whisper-1"}
            ]}
        """.trimIndent()
        val all = AiWire.parseModelIds(body)
        val chat = all.filter(AiWire::isChatModel)
        assertEquals(3, all.size)
        assertEquals(listOf("gpt-4o"), chat)
        assertEquals(2, all.size - chat.size)
    }

    private fun user(text: String) = ChatMessage(ChatMessage.USER, text)
    private fun assistant(text: String) = ChatMessage(ChatMessage.ASSISTANT, text)
}
