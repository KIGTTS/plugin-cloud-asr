package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

internal class CloudAsrProtocols(private val transport: SpeechTransport = OkSpeechTransport(),
    private val clock: () -> Instant = Instant::now) {
    private val tokens = SpeechTokens(transport, clock)
    suspend fun recognize(config: CloudAsrConfig, audio: PcmAudio, language: String): String {
        SpeechPcm.validate(audio)
        if (!config.configured) throw EngineException(EngineError.NOT_CONFIGURED, "请先填写完整的云端识别配置")
        return when (config.provider) {
            CloudAsrProvider.TENCENT -> tencent(config, audio, language)
            CloudAsrProvider.ALIYUN -> aliyun(config, audio)
            CloudAsrProvider.AZURE -> azure(config, audio, language)
            CloudAsrProvider.BAIDU -> baidu(config, audio, language)
            CloudAsrProvider.IFlytek -> xfyun(config, audio, language)
        }
    }
    private suspend fun tencent(c: CloudAsrConfig, audio: PcmAudio, language: String): String {
        requireSecret(c)
        val model = c.model.ifBlank { "16k_" + when { language.startsWith("en") -> "en"; language.startsWith("ja") -> "ja"; language.startsWith("yue") -> "yue"; else -> "zh" } }
        val body = JSONObject().put("EngSerViceType", model).put("SourceType", 1).put("VoiceFormat", "pcm")
            .put("Data", Base64.getEncoder().encodeToString(audio.bytes)).put("DataLen", audio.bytes.size).toString().toByteArray()
        val headers = SpeechSigning.tencent(c.endpoint, "asr", "SentenceRecognition", "2019-06-14", c.apiKey, c.apiSecret, body, clock())
        val result = speechJson(transport.http(SpeechHttpRequest(c.endpoint, body, headers = headers)), secrets(c)).optJSONObject("Response")
            ?: throw EngineException(EngineError.INFERENCE, "腾讯云未返回识别结果")
        result.optJSONObject("Error")?.let { throw speechFailure(it.optString("Code"), it.optString("Message"), secrets(c)) }
        if (!result.has("Result")) throw EngineException(EngineError.INFERENCE, "腾讯云未返回识别文本")
        return result.optString("Result")
    }
    private suspend fun aliyun(c: CloudAsrConfig, audio: PcmAudio): String {
        requireApp(c)
        val token = tokens.aliyun(c.apiKey, c.apiSecret, c.endpoint)
        val url = c.endpoint + (if ('?' in c.endpoint) "&" else "?") + SpeechSigning.query(mapOf("appkey" to c.appId,
            "format" to "pcm", "sample_rate" to "16000", "enable_punctuation_prediction" to "true", "enable_inverse_text_normalization" to "true"))
        val result = speechJson(transport.http(SpeechHttpRequest(url, audio.bytes, "application/octet-stream", mapOf("X-NLS-Token" to token))), secrets(c) + token)
        if (result.optInt("status") != 20000000) throw speechFailure(result.optString("status"), result.optString("message"), secrets(c) + token)
        return result.optString("result")
    }
    private suspend fun azure(c: CloudAsrConfig, audio: PcmAudio, language: String): String {
        val url = c.endpoint + (if ('?' in c.endpoint) "&" else "?") + SpeechSigning.query(mapOf("language" to language, "format" to "simple"))
        val result = speechJson(transport.http(SpeechHttpRequest(url, SpeechPcm.wav(audio), "audio/wav; codecs=audio/pcm; samplerate=16000",
            mapOf("Ocp-Apim-Subscription-Key" to c.apiKey, "Accept" to "application/json"))), secrets(c))
        return when (result.optString("RecognitionStatus")) {
            "Success" -> result.optString("DisplayText")
            "NoMatch", "InitialSilenceTimeout", "BabbleTimeout" -> ""
            else -> throw speechFailure(result.optString("RecognitionStatus"), result.optString("ErrorDetails"), secrets(c))
        }
    }
    private suspend fun baidu(c: CloudAsrConfig, audio: PcmAudio, language: String): String {
        val token = tokens.baidu(c.apiKey, c.apiSecret)
        val model = c.model.toIntOrNull() ?: if (language.startsWith("en")) 1737 else 1537
        val body = JSONObject().put("format", "pcm").put("rate", 16000).put("channel", 1).put("token", token)
            .put("cuid", "kigtts-${c.id.take(48)}").put("dev_pid", model).put("speech", Base64.getEncoder().encodeToString(audio.bytes)).put("len", audio.bytes.size)
        val result = speechJson(transport.http(SpeechHttpRequest(c.endpoint, body.toString().toByteArray())), secrets(c) + token)
        if (result.optInt("err_no", -1) != 0) throw speechFailure(result.optString("err_no"), result.optString("err_msg"), secrets(c) + token)
        val values = result.optJSONArray("result") ?: return ""
        return (0 until values.length()).joinToString("") { values.optString(it) }
    }
    private suspend fun xfyun(c: CloudAsrConfig, audio: PcmAudio, language: String): String {
        requireApp(c); requireSecret(c)
        val results = sortedMapOf<Int, String>()
        transport.socket(SpeechSigning.xfyun(c.endpoint, c.apiKey, c.apiSecret, clock()), XfyunAsrFrames.create(c, audio, language)) { text ->
            val root = runCatching { JSONObject(text) }.getOrElse { throw EngineException(EngineError.INFERENCE, "讯飞返回了无效结果") }
            if (root.optInt("code", -1) != 0) throw speechFailure(root.optString("code"), root.optString("message"), secrets(c))
            val data = root.optJSONObject("data")
            data?.optJSONObject("result")?.let { result ->
                if (result.optString("pgs") == "rpl") result.optJSONArray("rg")?.let { range ->
                    val start = range.optInt(0); val end = range.optInt(1)
                    results.keys.filter { it in start..end }.forEach(results::remove)
                }
                val words = result.optJSONArray("ws")
                results[result.optInt("sn")] = if (words == null) "" else (0 until words.length()).joinToString("") {
                    words.optJSONObject(it)?.optJSONArray("cw")?.optJSONObject(0)?.optString("w").orEmpty()
                }
            }
            data?.optInt("status") == 2
        }
        return results.values.joinToString("")
    }
    private fun requireApp(c: CloudAsrConfig) { if (c.appId.isBlank()) throw EngineException(EngineError.NOT_CONFIGURED, "请填写服务的 App ID 或 AppKey") }
    private fun requireSecret(c: CloudAsrConfig) { if (c.apiSecret.isBlank()) throw EngineException(EngineError.NOT_CONFIGURED, "请填写服务密钥 Secret") }
    private fun secrets(c: CloudAsrConfig) = listOf(c.apiKey, c.apiSecret, c.appId)
    suspend fun close() { transport.close(); tokens.clear() }
}

internal object XfyunAsrFrames {
    fun create(c: CloudAsrConfig, audio: PcmAudio, language: String): List<SpeechFrame> {
        val result = mutableListOf<SpeechFrame>()
        for (offset in audio.bytes.indices step 1280) {
            val data = JSONObject().put("status", if (offset == 0) 0 else 1).put("format", "audio/L16;rate=16000")
                .put("encoding", "raw").put("audio", Base64.getEncoder().encodeToString(audio.bytes.copyOfRange(offset, minOf(offset + 1280, audio.bytes.size))))
            val root = JSONObject().put("data", data)
            if (offset == 0) root.put("common", JSONObject().put("app_id", c.appId)).put("business", JSONObject()
                .put("language", if (language.startsWith("en")) "en_us" else "zh_cn").put("domain", c.model.ifBlank { "iat" }).put("accent", "mandarin"))
            result += SpeechFrame(root.toString(), 40)
        }
        result += SpeechFrame(JSONObject().put("data", JSONObject().put("status", 2)).toString())
        return result
    }
}
