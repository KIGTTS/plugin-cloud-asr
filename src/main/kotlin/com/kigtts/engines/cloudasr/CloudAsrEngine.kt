package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream

internal class CloudAsrEngine(private val settings: CloudAsrSettings,
    private val protocols: CloudAsrProtocols = CloudAsrProtocols()) : AsrEngine {
    override val info: AsrInfo get() = AsrInfo(inputFormat = AudioFormat(), batch = true,
        streaming = StreamingKind.SEGMENTED,
        availability = if (settings.current()?.configured == true) EngineAvailability.READY else EngineAvailability.NEEDS_RESOURCES,
        role = AsrRole.PROVIDER, maxBatchSeconds = 60)

    override suspend fun recognize(audio: PcmAudio, config: RecognitionConfig): RecognitionResult {
        val service = settings.current() ?: throw EngineException(EngineError.NOT_CONFIGURED, "请先选择已配置的云端识别服务")
        SpeechPcm.validate(audio)
        return RecognitionResult(protocols.recognize(service, audio, service.language.ifBlank { config.language }).take(config.maxDisplayCharacters))
    }

    override fun recognizeStream(audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> = flow {
        emit(RecognitionEvent.Started)
        // A bounded segment is flushed while recording, rather than retaining an entire stream in memory.
        val pending = ByteArrayOutputStream(SEGMENT_BYTES)
        suspend fun flush() {
            if (pending.size() == 0) return
            val sample = PcmAudio(pending.toByteArray()); pending.reset()
            emit(RecognitionEvent.Final(recognize(sample, config)))
        }
        audio.collect { chunk ->
            if (chunk.sampleRate != 16_000 || chunk.channels != 1) throw EngineException(EngineError.UNSUPPORTED_FORMAT, "云端识别需要 16 kHz 单声道 PCM 音频")
            var offset = 0
            while (offset < chunk.bytes.size) {
                val count = minOf(SEGMENT_BYTES - pending.size(), chunk.bytes.size - offset)
                pending.write(chunk.bytes, offset, count); offset += count
                if (pending.size() == SEGMENT_BYTES) flush()
            }
        }
        flush()
    }.flowOn(Dispatchers.IO)

    override suspend fun close() = protocols.close()
    companion object { private const val SEGMENT_BYTES = 16_000 * 2 * 15 }
}