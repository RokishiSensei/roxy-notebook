package com.lexi.wordbook.ai

/**
 * Pure request/response plumbing for the AI client: endpoint derivation, payload shape and
 * response parsing. Kept free of Android and network types so it can be unit tested directly.
 *
 * All of the 400-class failures reported by providers trace back to one of these rules, so they
 * are encoded here once instead of being guessed at the call site.
 */
internal object AiWire {

    /** Providers reject an empty `messages` array with HTTP 400. */
    private val nonChatModelMarkers = listOf(
        "embedding", "embed-", "bge-", "gte-", "text-similarity", "rerank",
        "whisper", "tts", "audio", "speech", "transcribe", "realtime",
        "dall-e", "image", "stable-diffusion", "midjourney", "flux",
        "moderation", "guard", "codex", "computer-use", "sora", "veo", "imagen"
    )

    /** Model families that changed the token parameter name or dropped `temperature`. */
    private val maxCompletionTokenPrefixes = listOf(
        "o1", "o3", "o4", "gpt-5", "gpt-6"
    )

    private val reasoningMarkers = listOf(
        "o1", "o3", "o4", "reasoner", "reasoning", "-r1", "r1-", "thinking", "qwq", "deepseek-r"
    )

    // ------------------------------------------------------------------ endpoints

    /** Strips a full chat endpoint back to its API root, so `/models` can be appended. */
    fun apiRoot(baseUrl: String): String {
        var base = baseUrl.trim().trimEnd('/')
        listOf(
            "/chat/completions", "/completions", "/messages", ":generateContent", ":streamGenerateContent"
        ).forEach { suffix ->
            if (base.endsWith(suffix)) base = base.removeSuffix(suffix)
        }
        // A mirrored path such as …/v1beta/models/gemini-2.0-flash also carries the model name.
        val modelsIndex = base.indexOf("/models/")
        if (modelsIndex >= 0) base = base.substring(0, modelsIndex)
        return base.trimEnd('/')
    }

    /**
     * Chat endpoint for a base URL.
     *
     * [apiRoot] handles the common mistake of pasting the full `…/v1/chat/completions` into the
     * address field: the suffix is stripped first, then the right path is rebuilt.
     */
    fun chatEndpoint(format: AiApiFormat, baseUrl: String, model: String): String {
        val root = apiRoot(baseUrl)
        return when (format) {
            AiApiFormat.GEMINI -> {
                val name = model.ifBlank { format.defaultModel }
                if (root.contains("/models/")) {
                    // The user pasted a full generateContent URL; just make sure the model matches.
                    val head = root.substringBefore("/models/")
                    "$head/models/$name:generateContent"
                } else {
                    val version = if (root.contains("/v1beta") || root.contains("/v1alpha")) {
                        ""
                    } else {
                        "/v1beta"
                    }
                    "$root$version/models/$name:generateContent"
                }
            }

            AiApiFormat.ANTHROPIC -> when {
                root.endsWith("/v1") -> "$root/messages"
                else -> "$root/v1/messages"
            }

            AiApiFormat.OPENAI -> when {
                root.endsWith("/v1") || root.endsWith("/v2") || root.endsWith("/openai") -> {
                    "$root/chat/completions"
                }
                // Some gateways put the version in the path already (…/openai/v1).
                root.contains("/v1") || root.contains("/v2") -> "$root/chat/completions"
                else -> "$root/v1/chat/completions"
            }
        }
    }

    /** Endpoint that lists the available models. */
    fun modelsEndpoint(format: AiApiFormat, baseUrl: String): String {
        val root = apiRoot(baseUrl)
        return when (format) {
            AiApiFormat.GEMINI -> {
                val version = if (root.contains("/v1beta") || root.contains("/v1alpha")) {
                    ""
                } else {
                    "/v1beta"
                }
                "$root$version/models?pageSize=200"
            }

            AiApiFormat.ANTHROPIC, AiApiFormat.OPENAI -> when {
                root.endsWith("/v1") || root.endsWith("/v2") -> "$root/models"
                root.contains("/v1") || root.contains("/v2") -> "$root/models"
                else -> "$root/v1/models"
            }
        }
    }

    // ------------------------------------------------------------------ model list

    /** True for models that cannot answer a chat request; they only pollute the picker. */
    fun isChatModel(id: String): Boolean {
        val lower = id.lowercase()
        if (lower.isBlank()) return false
        return nonChatModelMarkers.none { lower.contains(it) }
    }

    /**
     * Collects model ids from every response shape seen in the wild.
     *
     * Only true ids are collected: OpenAI-compatible gateways also return a human-readable
     * `name` ("GPT-4o Mini") which is **not** a valid model id and produces HTTP 400 when sent
     * back, so it is deliberately ignored.
     */
    fun parseModelIds(body: String): List<String> {
        val root = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return emptyList()
        val ids = linkedSetOf<String>()

        fun readArray(array: org.json.JSONArray?) {
            if (array == null) return
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                // "id" first; "model" is used by a few gateways; "name" only when it is the
                // sole identifier (Gemini uses "models/…" there).
                val id = item.optString("id").ifBlank { item.optString("model") }
                if (id.isNotBlank()) {
                    ids.add(id)
                } else {
                    val name = item.optString("name")
                    if (name.startsWith("models/")) ids.add(name.removePrefix("models/"))
                }
            }
        }

        readArray(root.optJSONArray("data"))
        readArray(root.optJSONArray("models"))
        root.optJSONObject("result")?.let { result ->
            readArray(result.optJSONArray("data"))
            readArray(result.optJSONArray("models"))
        }
        // Some tiny gateways answer with a bare array.
        if (ids.isEmpty()) {
            root.optJSONArray("items")?.let(::readArray)
        }
        return ids.filter { it.isNotBlank() }.distinct().sorted()
    }

    // ------------------------------------------------------------------ payload

    fun isReasoningModel(model: String): Boolean {
        val lower = model.lowercase().substringAfterLast('/')
        return reasoningMarkers.any { lower.startsWith(it) || lower.contains("-$it") || lower.contains(it) }
    }

    private fun usesMaxCompletionTokens(model: String): Boolean {
        val lower = model.lowercase().substringAfterLast('/')
        return maxCompletionTokenPrefixes.any { lower.startsWith(it) }
    }

    /**
     * Normalises a conversation for the wire:
     * - drops blank messages,
     * - merges consecutive messages with the same role (several providers answer 400 otherwise),
     * - optionally folds the system prompt into the first user turn.
     *
     * The system prompt is kept apart from the conversation so that merging it into the first
     * user turn cannot silently swallow a real user message.
     */
    fun normalize(
        messages: List<ChatMessage>,
        foldSystemIntoUser: Boolean = false
    ): Pair<String, List<ChatMessage>> {
        val system = messages.filter { it.role == ChatMessage.SYSTEM }
            .joinToString("\n\n") { it.content.trim() }
            .trim()

        val conversation = mutableListOf<ChatMessage>()
        messages.filter { it.role != ChatMessage.SYSTEM }.forEach { message ->
            val content = message.content.trim()
            if (content.isEmpty()) return@forEach
            val last = conversation.lastOrNull()
            if (last != null && last.role == message.role) {
                conversation[conversation.lastIndex] = last.copy(content = "${last.content}\n\n$content")
            } else {
                conversation += ChatMessage(message.role, content)
            }
        }

        if (foldSystemIntoUser) {
            if (system.isNotBlank()) {
                val index = conversation.indexOfFirst { it.role == ChatMessage.USER }
                if (index >= 0) {
                    conversation[index] = conversation[index].copy(
                        content = "$system\n\n${conversation[index].content}"
                    )
                } else {
                    conversation.add(0, ChatMessage(ChatMessage.USER, system))
                }
            }
            return "" to conversation
        }
        return system to conversation
    }

    /**
     * Builds the request body for [format].
     *
     * [attempt] drives the automatic retries: each step removes or renames one optional field
     * that some models reject with HTTP 400.
     */
    fun payload(
        format: AiApiFormat,
        config: AiConfig,
        messages: List<ChatMessage>,
        attempt: Int = 0
    ): String {
        val foldSystem = attempt >= 2
        val (system, conversation) = normalize(messages, foldSystemIntoUser = foldSystem)
        val model = config.model.ifBlank { format.defaultModel }
        val reasoning = isReasoningModel(model)

        return when (format) {
            AiApiFormat.OPENAI -> org.json.JSONObject().apply {
                put("model", model)
                put("stream", false)
                if (!reasoning) put("temperature", config.temperature.toDouble())
                val tokens = if (config.maxTokens > 0) config.maxTokens else 8192
                // o-series / gpt-5 renamed `max_tokens`; sending the old name is a hard 400.
                if (usesMaxCompletionTokens(model)) {
                    put("max_completion_tokens", tokens)
                } else {
                    put("max_tokens", tokens)
                }
                put(
                    "messages",
                    org.json.JSONArray().apply {
                        if (system.isNotBlank()) {
                            put(org.json.JSONObject().put("role", "system").put("content", system))
                        }
                        conversation.forEach { message ->
                            put(org.json.JSONObject().put("role", message.role).put("content", message.content))
                        }
                    }
                )
            }.toString()

            AiApiFormat.ANTHROPIC -> org.json.JSONObject().apply {
                put("model", model)
                put("max_tokens", if (config.maxTokens > 0) config.maxTokens else 8192)
                put("temperature", config.temperature.toDouble())
                if (system.isNotBlank()) put("system", system)
                put(
                    "messages",
                    org.json.JSONArray().apply {
                        conversation.forEach { message ->
                            put(org.json.JSONObject().put("role", message.role).put("content", message.content))
                        }
                    }
                )
            }.toString()

            AiApiFormat.GEMINI -> org.json.JSONObject().apply {
                if (system.isNotBlank()) {
                    put(
                        "systemInstruction",
                        org.json.JSONObject().put(
                            "parts",
                            org.json.JSONArray().put(org.json.JSONObject().put("text", system))
                        )
                    )
                }
                put(
                    "contents",
                    org.json.JSONArray().apply {
                        conversation.forEach { message ->
                            put(
                                org.json.JSONObject().put(
                                    "role",
                                    if (message.role == ChatMessage.ASSISTANT) "model" else "user"
                                ).put(
                                    "parts",
                                    org.json.JSONArray().put(org.json.JSONObject().put("text", message.content))
                                )
                            )
                        }
                    }
                )
                put(
                    "generationConfig",
                    org.json.JSONObject().apply {
                        if (!reasoning) put("temperature", config.temperature.toDouble())
                        put("maxOutputTokens", if (config.maxTokens > 0) config.maxTokens else 8192)
                    }
                )
            }.toString()
        }
    }

    /** True when a payload is worth re-sending after stripping optional fields. */
    fun isRetryableParameterError(code: Int, detail: String): Boolean {
        if (code != 400 && code != 404 && code != 422) return false
        val lower = detail.lowercase()
        return listOf(
            "temperature", "max_tokens", "max_completion_tokens", "maxoutputtokens",
            "unsupported parameter", "unsupported value", "unknown parameter",
            "invalid parameter", "system_instruction", "systeminstruction", "system message",
            "system role", "must be", "not supported", "does not support"
        ).any { lower.contains(it) }
    }

    /** A model name that is obviously not an id (display names and pasted URLs). */
    fun looksLikeInvalidModel(model: String): Boolean {
        val trimmed = model.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.startsWith("http")) return true
        if (trimmed.any { it.isWhitespace() }) return true
        if (trimmed.endsWith(":")) return true
        return false
    }

    /** Pulls the assistant text out of whichever provider answered. */
    fun extractText(body: String): String {
        val root = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return ""
        root.optJSONArray("choices")?.optJSONObject(0)?.let { choice ->
            choice.optJSONObject("message")?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it }
            choice.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }?.let { return it }
            choice.optJSONObject("delta")?.optString("content")?.takeIf { it.isNotBlank() }?.let { return it }
            choice.optString("text").takeIf { it.isNotBlank() }?.let { return it }
        }
        root.optJSONArray("content")?.let { array ->
            val joined = buildString {
                for (i in 0 until array.length()) {
                    val part = array.optJSONObject(i) ?: continue
                    if (part.optString("type", "text") == "text") append(part.optString("text"))
                }
            }
            if (joined.isNotBlank()) return joined
        }
        root.optJSONArray("candidates")?.optJSONObject(0)?.let { candidate ->
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            if (parts != null) {
                val joined = buildString {
                    for (i in 0 until parts.length()) {
                        append(parts.optJSONObject(i)?.optString("text").orEmpty())
                    }
                }
                if (joined.isNotBlank()) return joined
            }
        }
        listOf("output_text", "response", "result", "text", "answer", "message").forEach { key ->
            val value = root.optString(key)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    fun extractError(body: String): String {
        val root = runCatching { org.json.JSONObject(body) }.getOrNull() ?: return ""
        root.optJSONObject("error")?.let { error ->
            return error.optString("message")
                .ifBlank { error.optString("type") }
                .ifBlank { error.optJSONObject("metadata")?.optString("raw").orEmpty() }
        }
        root.optJSONArray("errors")?.optJSONObject(0)?.let { return it.optString("message") }
        return root.optString("message")
    }
}
