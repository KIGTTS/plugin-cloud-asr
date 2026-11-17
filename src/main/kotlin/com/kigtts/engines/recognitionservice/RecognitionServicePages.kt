package com.kigtts.engines.recognitionservice

import com.kigtts.plugin.api.*

internal fun recognitionServicePage(id: String, settings: RecognitionServiceSettings, broker: RecognitionBroker,
    context: PluginContext, serviceId: String, copy: (String, String) -> String = { _, fallback -> fallback }): PluginPage? {
    val all = settings.ordered(broker.providers()); val selected = settings.current(all)
    if (id == "manager") return PluginPage(id, copy("page.title", "语音识别服务"), buildList {
        if (context.recognitionServiceId != serviceId) add(PageBlock.Actions(listOf(PageAction("activate", copy("action.activate", "用于语音识别"), PluginIcon.MIC))))
        add(PageBlock.Group(copy("group.captions", "聆听字幕"), listOf(PageBlock.Choice(copy("captions.position", "显示位置"), listOf(
            PageAction("output/PANEL", copy("captions.panel", "主界面"), PluginIcon.TEXT), PageAction("output/FLOATING", copy("captions.floating", "浮窗"), PluginIcon.TEXT),
            PageAction("output/BOTH", copy("captions.both", "两处"), PluginIcon.TEXT)), "output/${settings.captionOutput.name}", PluginIcon.HEARING))))
        add(PageBlock.Group(copy("group.input", "语音输入"), listOf(
            PageBlock.Toggle(copy("input.quick", "快捷语音输入"), settings.voiceInput, "voice-input", copy("input.quick.summary", "长按快捷文本开始或停止"), PluginIcon.MIC),
            PageBlock.Toggle(copy("input.continuous", "连续输入"), settings.continuousInput, "continuous-input", copy("input.continuous.summary", "逐句发送，再次长按停止"), PluginIcon.HEARING),
        )))
    }, management = ManagementList("recognition-engines", all.map { provider ->
        ManagementItem(provider.pluginId, engineName(provider.pluginId, provider.name, copy),
            (if (!provider.enabled) copy("engine.disabled", "已停用") else if (provider.info.availability == EngineAvailability.READY) recognitionKind(provider.info.streaming, copy) else copy("engine.add_pack", "请添加识别包")),
            PluginIcon.MIC, selected = provider.pluginId == selected?.pluginId, primaryAction = "engine/${provider.pluginId}",
            actions = listOf(PageAction("select/${provider.pluginId}", copy("engine.use", "选用"), PluginIcon.MIC)),
            toggle = ManagementToggle(provider.enabled, "toggle/${provider.pluginId}"))
    }, "reorder", copy("engine.empty", "添加识别插件后即可使用")))
    if (id.startsWith("engine/")) {
        val provider = all.firstOrNull { it.pluginId == id.substringAfter('/') } ?: return null
        val items = listOf(
            ManagementItem("enabled", copy("engine.enable", "启用"), icon = PluginIcon.MIC, group = copy("group.recognition", "识别"),
                toggle = ManagementToggle(provider.enabled, "toggle/${provider.pluginId}")),
            ManagementItem("select", copy("engine.for_recognition", "用于识别"), recognitionKind(provider.info.streaming, copy), PluginIcon.MIC, group = copy("group.recognition", "识别"),
                selected = provider.pluginId == selected?.pluginId, primaryAction = "select/${provider.pluginId}")) +
            if (provider.enabled) provider.settingsPages.map { page ->
                ManagementItem("settings/${page.id}", page.title, icon = page.icon, group = copy("group.settings", "设置"),
                    primaryAction = "settings/${provider.pluginId}/${page.id}")
            } else emptyList()
        return PluginPage(id, provider.name, emptyList(), ManagementList("recognition-settings/${provider.pluginId}", items))
    }
    return null
}

private fun engineName(id: String, fallback: String, copy: (String, String) -> String): String = when (id) {
    "engine.streaming-asr" -> copy("engine.streaming", "流式语音识别")
    "engine.cloud-asr" -> copy("engine.cloud", "云端语音识别")
    "engine.sensevoice" -> copy("engine.sensevoice", "SenseVoice 语音识别")
    else -> fallback
}
internal fun recognitionKind(kind: StreamingKind, copy: (String, String) -> String = { _, fallback -> fallback }) = when (kind) {
    StreamingKind.INCREMENTAL -> copy("engine.incremental", "边说边识别")
    StreamingKind.SEGMENTED -> copy("engine.segmented", "分段识别")
    StreamingKind.NONE -> copy("engine.batch", "录音识别")
}
