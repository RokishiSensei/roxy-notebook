package com.lexi.wordbook.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal, dependency-free client for OpenAI-compatible / Anthropic / Gemini chat endpoints.
 * Built on HttpURLConnection so the app ships no extra networking library.
 *
 * Wire rules (endpoint derivation, payload shape, model-list parsing, 400 retries) live in
 * [AiWire] so they are unit tested rather than guessed at.
 */
class AiClient(private val config: AiConfig) {

    suspend fun chat(messages: List<ChatMessage>): AiResponse = withContext(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) {
            return@withContext AiResponse("", "还没有配置 API 地址，请到「设置 → AI 接口」填写。")
        }
        if (config.apiFormat != AiApiFormat.OPENAI && config.apiKey.isBlank()) {
            return@withContext AiResponse("", "该接口需要 API Key，请到设置里填写。")
        }
        runCatching { execute(messages) }.getOrElse { error ->
            AiResponse("", friendlyError(error))
        }
    }

    /**
     * Sends the request and retries without optional fields when a provider rejects them.
     *
     * Reasoning models (o1/o3/gpt-5/deepseek-reasoner/…) refuse `temperature`, Gemini sometimes
     * refuses `systemInstruction`, and a few gateways dislike `max_tokens`. Instead of failing
     * with a bare HTTP 400 the client walks down a small list of relaxed payloads.
     */
    private fun execute(messages: List<ChatMessage>): AiResponse {
        var attempt = 0
        var lastError: AiResponse? = null

        while (attempt <= MAX_ATTEMPTS) {
            val response = sendOnce(messages, attempt)
            if (response.error == null) return response
            lastError = response

            val retryable = AiWire.isRetryableParameterError(
                response.httpCode,
                response.error.orEmpty()
            )
            if (!retryable || attempt == MAX_ATTEMPTS) return response
            attempt++
        }
        return lastError ?: AiResponse("", "请求失败")
    }

    private fun sendOnce(messages: List<ChatMessage>, attempt: Int): AiResponse {
        val endpoint = AiWire.chatEndpoint(config.apiFormat, config.baseUrl, config.model)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.timeoutSeconds * 1000
            readTimeout = config.timeoutSeconds * 1000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            applyAuth(this)
        }

        val payload = AiWire.payload(config.apiFormat, config, messages, attempt)
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()

        if (code !in 200..299) {
            val detail = AiWire.extractError(body).ifBlank { body.take(400) }
            return AiResponse("", failureMessage(code, detail), code)
        }

        val text = AiWire.extractText(body)
        return if (text.isBlank()) {
            AiResponse("", emptyResponseMessage(body), code)
        } else {
            AiResponse(text.trim(), null, code)
        }
    }

    /** Turns a raw provider error into something the user can act on. */
    private fun failureMessage(code: Int, detail: String): String {
        val hint = when {
            code == 401 || code == 403 ->
                "API Key 无效或没有该模型的权限，请检查 Key。"

            code == 404 ->
                "接口地址或模型名不存在。地址可以只填到 /v1，App 会自动补全路径；" +
                    "模型名建议用「获取在线模型」里的那一项。"

            code == 429 ->
                "请求过于频繁或额度用尽，稍后再试。"

            code == 400 || code == 422 -> buildString {
                append("请求被接口拒绝（参数与模型不匹配）。")
                if (AiWire.looksLikeInvalidModel(config.model)) {
                    append("当前模型名「${config.model}」看起来不是有效的模型 ID，")
                    append("请点「获取在线模型」从列表里选一个。")
                } else {
                    append("已自动尝试去掉 temperature / max_tokens 等可选参数；")
                    append("若仍失败，多半是模型名不被该接口接受，请用「获取在线模型」重新选择。")
                }
            }

            code >= 500 -> "接口服务端错误（HTTP $code），稍后再试。"

            else -> "接口返回 HTTP $code。"
        }
        return if (detail.isBlank()) hint else "$hint\n\n原始信息：$detail"
    }

    /** A 2xx body with nothing usable in it: usually a wrong model or a non-chat endpoint. */
    private fun emptyResponseMessage(body: String): String {
        val trimmed = body.trim()
        return when {
            trimmed.isEmpty() -> "接口返回了空响应，请检查模型名与接口地址。"
            trimmed.startsWith("[") -> "接口返回的不是对话结果，请确认地址指向的是对话接口。"
            else -> "接口没有返回可识别的文本，请检查模型名或接口地址。\n\n原始信息：${trimmed.take(300)}"
        }
    }

    private fun applyAuth(connection: HttpURLConnection) {
        val key = config.apiKey.trim()
        when (config.apiFormat) {
            AiApiFormat.OPENAI -> if (key.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer $key")
            }

            AiApiFormat.ANTHROPIC -> {
                if (key.isNotEmpty()) connection.setRequestProperty("x-api-key", key)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            }

            AiApiFormat.GEMINI -> if (key.isNotEmpty()) {
                connection.setRequestProperty("x-goog-api-key", key)
            }
        }
    }

    // ------------------------------------------------------------------ model list

    /**
     * Asks the endpoint which models it offers.
     *
     * - OpenAI 兼容: GET {root}/v1/models   -> { "data": [ { "id": "..." } ] }
     * - Anthropic:   GET {root}/v1/models
     * - Gemini:      GET {root}/v1beta/models -> { "models": [ { "name": "models/..." } ] }
     *
     * A full chat endpoint in the address field (`…/v1/chat/completions`) is handled: the suffix
     * is stripped before `/models` is appended, which used to produce a 404 and the misleading
     * "该接口不支持列出模型" message. Non-chat models (embeddings, TTS, image, moderation) are
     * filtered out so picking one cannot lead to an HTTP 400 later.
     */
    suspend fun listModels(): ModelListResult = withContext(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) {
            return@withContext ModelListResult(error = "请先填写 API 地址")
        }
        runCatching { fetchModels() }.getOrElse { error ->
            ModelListResult(error = friendlyError(error))
        }
    }

    private fun fetchModels(): ModelListResult {
        val connection = (URL(AiWire.modelsEndpoint(config.apiFormat, config.baseUrl))
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = config.timeoutSeconds * 1000
            readTimeout = config.timeoutSeconds * 1000
            setRequestProperty("Accept", "application/json")
            applyAuth(this)
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()

        if (code !in 200..299) {
            val detail = AiWire.extractError(body).ifBlank { body.take(200) }
            return ModelListResult(
                error = when (code) {
                    401, 403 -> "API Key 无效或没有列出模型的权限，请手动填写模型名称。"
                    404 -> "没找到模型列表接口（HTTP 404）：地址填到 /v1 即可，" +
                        "App 会自动补全 /models。" +
                        if (detail.isBlank()) "" else "\n\n原始信息：$detail"
                    else -> "该接口不支持列出模型（HTTP $code）" +
                        if (detail.isBlank()) "，请手动填写模型名称。" else "：$detail"
                }
            )
        }

        val all = AiWire.parseModelIds(body)
        if (all.isEmpty()) {
            return ModelListResult(error = "接口没有返回模型列表，请手动填写模型名称。")
        }
        val chatModels = all.filter(AiWire::isChatModel)
        return ModelListResult(
            models = chatModels.ifEmpty { all },
            // Let the UI explain why the list may be shorter than the provider's dashboard.
            hiddenCount = all.size - chatModels.size
        )
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("Unable to resolve host") || raw.contains("UnknownHost") ->
                "无法解析接口域名，请检查网络或地址是否正确。"
            raw.contains("timed out", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) ->
                "请求超时（${config.timeoutSeconds}s），可以在设置里调大超时时间。"
            raw.contains("Cleartext", ignoreCase = true) ->
                "该地址是 http:// 明文请求，Android 默认禁止，请改用 https。"
            raw.contains("SSL", ignoreCase = true) || raw.contains("certificate", ignoreCase = true) ->
                "TLS 握手失败：$raw"
            raw.isBlank() -> "请求失败：${error::class.java.simpleName}"
            else -> "请求失败：$raw"
        }
    }

    companion object {
        /** Attempt 0 is the full payload; 1 and 2 progressively drop optional fields. */
        private const val MAX_ATTEMPTS = 2
    }
}
