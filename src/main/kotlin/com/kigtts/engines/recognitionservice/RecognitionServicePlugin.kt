package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.*

class RecognitionServicePlugin : KigPlugin {
    override val descriptor = PluginDescriptor("service.speech-recognition", "语音识别服务", "聆听字幕、语音输入与识别包", version = "0.8.0")
    override val localization = PluginLocalization("zh-CN", listOf("zh-CN", "zh-CN-x-casual", "en", "ko", "ja"))
    private var strings = PluginStrings(emptyMap())
    private lateinit var settings: RecognitionServiceSettings
    private lateinit var broker: RecognitionBroker
    private lateinit var facade: ServiceRecognition
    override val asr: AsrEngine get() = facade
    override suspend fun attach(environment: PluginEnvironment) {
        strings = PluginStrings.fromAssets(environment.assets)
        settings = RecognitionServiceSettings(environment.storage)
        broker = requireNotNull(environment.recognitionBroker) { "宿主未授予语音识别服务能力" }
        facade = ServiceRecognition(settings, broker)
    }
    private fun labels(locale: String): (String, String) -> String = { key, fallback -> strings.text(key, locale, fallback = fallback) }
    override val contributions get() = contributions(PluginContext("", ""))
    override fun contributions(context: PluginContext): PluginContributions {
        val copy = labels(context.locale)
        val selected = context.recognitionServiceId == null || context.recognitionServiceId == descriptor.id
        val entry = SettingsEntry("manager", copy("entry.title", "语音识别"), "manager", PluginIcon.MIC,
            cardId = "audio", cardTitle = copy("action.audio", "音频"), summary = copy("entry.summary", "聆听字幕与语音输入"))
        return PluginContributions(settingsEntries = listOf(entry), automationActions = listOf("listen", "close", "input", "continuous"), actions = if (selected) listOf(
            UiAction("listen", if (captionSession(context)) copy("action.close_listen", "关闭聆听") else copy("action.start_listen", "开始聆听"), UiSlot.EDITOR_TOP_START,
                PluginIcon.HEARING, buttonStyle = UiButtonStyle.ICON_TEXT),
            UiAction("close", copy("action.close_listen", "关闭聆听"), UiSlot.LISTENING_BOTTOM_END, PluginIcon.CLOSE),
            UiAction("continuous", if (context.continuousRecognition) copy("action.stop_continuous", "停止连续输入") else copy("action.continuous", "连续语音输入"),
                UiSlot.EDITOR_MENU, PluginIcon.MIC, UiPresentation.MENU_ITEM),
        ) else emptyList(), gestures = if (selected && settings.voiceInput)
            listOf(GestureAction("input", if (context.continuousRecognition) copy("action.stop_continuous", "停止连续输入") else copy("action.voice_input", "语音输入"), PluginGesture.QUICK_TEXT_LONG_PRESS))
            else emptyList())
    }
    override fun page(pageId: String, context: PluginContext) = recognitionServicePage(pageId, settings, broker, context, descriptor.id, labels(context.locale))
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
        val parts = actionId.split('/'); val id = parts.getOrNull(1)
        fun provider() = broker.providers().first { it.pluginId == id }
        return when (parts[0]) {
            "listen" -> if (captionSession(context)) PluginOutcome.EndRecognition() else start(RecognitionPurpose.CAPTIONS)
            "close" -> PluginOutcome.EndRecognition()
            "input" -> if (!settings.voiceInput) PluginOutcome.RefreshUi
                else if (context.voiceInputActive || context.continuousRecognition) PluginOutcome.EndRecognition()
                else start(RecognitionPurpose.VOICE_INPUT, settings.continuousInput)
            "continuous" -> if (context.continuousRecognition) PluginOutcome.EndRecognition()
                else start(RecognitionPurpose.VOICE_INPUT, continuous = true)
            "voice-input" -> { settings.voiceInput = !settings.voiceInput; settings.save(); PluginOutcome.RefreshUi }
            "continuous-input" -> { settings.continuousInput = !settings.continuousInput; settings.save(); PluginOutcome.RefreshUi }
            "output" -> { settings.captionOutput = CaptionOutput.valueOf(requireNotNull(id)); settings.save(); PluginOutcome.RefreshUi }
            "activate" -> PluginOutcome.SelectRecognitionService(descriptor.id)
            "engine" -> PluginOutcome.OpenPage(actionId)
            "select" -> {
                val selected = provider(); settings.provider = selected.pluginId; settings.save()
                if (!selected.enabled) PluginOutcome.SetPluginEnabled(selected.pluginId, true) else PluginOutcome.RecognitionRoutingChanged
            }
            "toggle" -> provider().let { PluginOutcome.SetPluginEnabled(it.pluginId, !it.enabled) }
            "settings" -> {
                val selected = provider(); check(selected.enabled) { "请先启用引擎" }
                val page = parts.drop(2).joinToString("/"); require(selected.settingsPages.any { it.id == page })
                PluginOutcome.OpenPluginPage(selected.pluginId, page)
            }
            "reorder" -> { settings.reorder(PluginJson.decodeFromString(requireNotNull(context.inputs["order"])), broker.providers()); PluginOutcome.RefreshUi }
            else -> PluginOutcome.Message("未知识别服务操作")
        }
    }
    private fun captionSession(context: PluginContext) = context.listeningPanelVisible ||
        (context.listening && !context.voiceInputActive && !context.continuousRecognition)
    private fun start(purpose: RecognitionPurpose, continuous: Boolean = false): PluginOutcome {
        val info = facade.info
        if (info.availability != EngineAvailability.READY) return PluginOutcome.Message("请先选用并配置一个识别包")
        if ((continuous || purpose == RecognitionPurpose.CAPTIONS) && info.streaming == StreamingKind.NONE)
            return PluginOutcome.Message("当前识别包仅支持完整录音，请选用支持边说边识别的识别包")
        return PluginOutcome.StartRecognition(RecognitionSession(descriptor.id, purpose, continuous = continuous,
            output = if (purpose == RecognitionPurpose.CAPTIONS) settings.captionOutput else CaptionOutput.PANEL,
            disableAutomaticRead = continuous, readFinalized = continuous))
    }
}
