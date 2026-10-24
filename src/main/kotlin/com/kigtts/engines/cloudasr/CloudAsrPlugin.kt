package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*

class CloudAsrPlugin : KigPlugin {
    override val descriptor = PluginDescriptor("engine.cloud-asr", "云端语音识别", "使用云端服务识别语音", version = "0.1.1", license = "MIT")
    private lateinit var settings: CloudAsrSettings
    private lateinit var engine: CloudAsrEngine
    override val asr: AsrEngine get() = engine
    override suspend fun attach(environment: PluginEnvironment) { settings = CloudAsrSettings(environment.storage); engine = CloudAsrEngine(settings) }
    override val contributions = PluginContributions(settingsEntries = listOf(
        SettingsEntry("cloud-asr", "云端语音识别", "settings", PluginIcon.MIC, "audio", "音频", "管理云端识别账户", 30)))
    override fun page(pageId: String, context: PluginContext) = asrSettingsPage(settings, pageId)

    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome = try {
        val p = actionId.split('/')
        fun id() = asrDecode(requireNotNull(p.getOrNull(1)))
        fun account() = settings.all().firstOrNull { it.id == id() } ?: error("此账户已移除")
        when (p[0]) {
            "provider" -> { account(); PluginOutcome.OpenPluginPage(descriptor.id, actionId) }
            "add" -> if (p.size == 1) PluginOutcome.Menu("选择服务商", CloudAsrProvider.entries.map { PageAction("add/${it.key}", it.label, PluginIcon.MIC) })
                else { val next = settings.add(CloudAsrProvider.of(p[1])); PluginOutcome.OpenPluginPage(descriptor.id, "provider/${asrEncode(next.id)}") }
            "toggle" -> { settings.toggle(id()); PluginOutcome.RefreshUi }
            "select" -> { settings.select(id()); PluginOutcome.RefreshUi }
            "remove" -> { val c = account(); PluginOutcome.Confirm("移除此账户？", c.name, PageAction("remove-confirm/${asrEncode(c.id)}", "移除", PluginIcon.CLOSE, symbol = "delete")) }
            "remove-confirm" -> { settings.remove(id()); PluginOutcome.RefreshUi }
            "edit" -> {
                val c = account(); val field = asrFields(c).firstOrNull { it.key == p.getOrNull(2) } ?: error("此设置项已失效")
                PluginOutcome.TextInput(field.label, "save/${asrEncode(c.id)}/${field.key}", field.value, secret = field.secret)
            }
            "save" -> {
                require(context.inputs.containsKey("value")) { "请重新打开设置项并保存" }
                settings.update(id(), requireNotNull(p.getOrNull(2)), context.inputs.getValue("value").trim()); PluginOutcome.RefreshUi
            }
            else -> PluginOutcome.Message("此操作已失效，请重新打开页面")
        }
    } catch (error: IllegalArgumentException) { PluginOutcome.Message(error.message ?: "请检查账户设置") }
      catch (error: IllegalStateException) { PluginOutcome.Message(error.message ?: "此操作已失效，请重新打开页面") }
}
