package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*
import java.util.UUID

internal suspend fun deepSeekAsr(transport: SpeechTransport, account: CloudAsrConfig, audio: PcmAudio): String {
    val boundary = "kigtts-${UUID.randomUUID()}"
    val body = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n" +
        "Content-Type: audio/wav\r\n\r\n"
    val request = SpeechHttpRequest(account.endpoint,
        body.toByteArray() + SpeechPcm.wav(audio) + "\r\n--$boundary--\r\n".toByteArray(),
        "multipart/form-data; boundary=$boundary",
        mapOf("Authorization" to "Bearer ${account.apiKey}", "Accept" to "application/json"))
    val response = speechJson(transport.http(request), listOf(account.apiKey))
    return response.opt("text") as? String ?: throw EngineException(EngineError.INFERENCE, "DeepSeek 未返回识别文本")
}
