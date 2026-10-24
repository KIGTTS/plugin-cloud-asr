package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

internal class CloudAsrEngine(private val settings: CloudAsrSettings) : AsrEngine {
    override val info: AsrInfo get() = AsrInfo(inputFormat = AudioFormat(), batch = true, streaming = StreamingKind.SEGMENTED,
        availability = if (settings.current()?.configured == true) EngineAvailability.READY else EngineAvailability.NEEDS_RESOURCES,
        role = AsrRole.PROVIDER, maxBatchSeconds = 120)
    override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult {
        val service = settings.current() ?: throw EngineException(EngineError.NOT_CONFIGURED, "请先配置并启用一个云端识别服务")
        if (!service.configured) throw EngineException(EngineError.NOT_CONFIGURED, "云端识别服务缺少 API Key")
        val body = JSONObject().put("audio", Base64.getEncoder().encodeToString(audio.bytes))
            .put("format", "pcm").put("sampleRate", audio.sampleRate).put("channels", audio.channels)
            .put("language", config.language).put("model", service.model).put("appId", service.appId)
        val response = CloudAsrHttp.post(service, body)
        return RecognitionResult(parseText(response).take(config.maxDisplayCharacters))
    }
    override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> = flow {
        emit(RecognitionEvent.Started)
        val bytes = audio.toList().flatMap { it.bytes.asIterable() }.map { it.toByte() }.toByteArray()
        val sample = PcmAudio(bytes, 16_000, 1)
        emit(RecognitionEvent.Final(recognize(sample, config)))
    }.flowOn(Dispatchers.IO)
    private fun parseText(value: String): String {
        val root = runCatching { JSONObject(value) }.getOrNull() ?: return value.trim()
        for (key in listOf("text", "transcript", "result", "sentence")) {
            val candidate = root.opt(key)
            when (candidate) {
                is String -> if (candidate.isNotBlank()) return candidate
                is JSONArray -> return (0 until candidate.length()).joinToString("") { candidate.optString(it) }
                is JSONObject -> parseText(candidate.toString()).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        root.optJSONObject("data")?.let { return parseText(it.toString()) }
        return ""
    }
}

internal object CloudAsrHttp {
    fun post(config: CloudAsrConfig, body: JSONObject): String {
        val connection = (URL(config.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 30_000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-KIGTTS-Provider", config.provider.key)
            if (config.apiKey.isNotBlank()) setRequestProperty("X-Api-Key", config.apiKey)
            if (config.apiSecret.isNotBlank()) setRequestProperty("X-Api-Secret", config.apiSecret)
            if (config.appId.isNotBlank()) setRequestProperty("X-App-Id", config.appId)
        }
        connection.outputStream.use { OutputStreamWriter(it, Charsets.UTF_8).use { writer -> writer.write(body.toString()) } }
        val code = connection.responseCode
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = input?.let { BufferedInputStream(it).use { stream -> stream.readBytes().toString(Charsets.UTF_8) } }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw EngineException(if (code == 408) EngineError.TIMEOUT else EngineError.INFERENCE, "云端识别请求失败（HTTP $code）")
        return response
    }
}
