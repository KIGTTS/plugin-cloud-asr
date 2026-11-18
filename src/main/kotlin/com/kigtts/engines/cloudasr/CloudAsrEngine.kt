package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class CloudAsrEngine(private val settings: CloudAsrSettings,
    private val protocols: CloudAsrProtocols = CloudAsrProtocols(), private val copy: SpeechCopy = defaultSpeechCopy) : AsrEngine {
    override val info: AsrInfo get() {
        val account = settings.current()
        return AsrInfo(inputFormat = AudioFormat(), batch = true, streaming = StreamingKind.NONE,
            availability = if (account?.configured == true) EngineAvailability.READY else EngineAvailability.NEEDS_RESOURCES,
            role = AsrRole.PROVIDER, maxBatchSeconds = if (account?.provider == CloudAsrProvider.DEEPSEEK) 120 else 60)
    }

    override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult = try {
        val service = settings.current() ?: throw EngineException(EngineError.NOT_CONFIGURED, "请先选择已配置的云端识别服务")
        val text = protocols.recognize(service, audio, service.language.ifBlank { config.language })
        RecognitionResult(text.take(32_000).dropLastWhile { it.isHighSurrogate() })
    } catch (error: EngineException) {
        throw localizedEngineError(error, copy)
    }

    override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> = flow {
        throw EngineException(EngineError.UNSUPPORTED_FORMAT, copy("feedback.batch_only", "此服务只支持分段识别"))
    }

    override suspend fun close() = protocols.close()
}
