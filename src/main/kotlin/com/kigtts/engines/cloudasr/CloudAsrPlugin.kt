package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*

class CloudAsrPlugin : KigPlugin {
    override val descriptor = PluginDescriptor("engine.cloud-asr", "云端语音识别", "使用云端服务识别语音", version = "0.2.2", license = "MIT")
    override val localization = PluginLocalization("zh-CN", listOf("zh-CN", "en", "ko", "ja"))
    private var strings = PluginStrings(emptyMap())

    private fun labels(locale: String): (String, String) -> String =
        { key, fallback -> strings.text(key, locale, fallback = fallback) }

    private lateinit var settings: CloudAsrSettings
    private lateinit var engine: CloudAsrEngine
    override val asr: AsrEngine get() = engine
    override suspend fun attach(environment: PluginEnvironment) { strings = PluginStrings.fromAssets(environment.assets); settings = CloudAsrSettings(environment.storage); engine = CloudAsrEngine(settings, copy = { key, fallback -> strings.text(key, environment.locale, fallback = fallback) }) }
    override val contributions get() = contributions(PluginContext("", ""))
    override fun contributions(context: PluginContext): PluginContributions {
        val copy = labels(context.locale)
        return PluginContributions(settingsEntries = listOf(
        SettingsEntry("cloud-asr", copy("plugin.title", "云端语音识别"), "settings", PluginIcon.MIC, "audio", copy("action.audio", "音频"), copy("plugin.summary", "管理云端识别账户"), 30)))
    }
    override fun page(pageId: String, context: PluginContext) = asrSettingsPage(settings, pageId, labels(context.locale))

    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
        val copy = labels(context.locale)
        return try {
            val p = actionId.split('/')
            fun id() = asrDecode(requireNotNull(p.getOrNull(1)))
            fun account() = settings.all().firstOrNull { it.id == id() } ?: throw LocalizedSettingIssue(copy("account.removed", "此账户已移除"))
            when (p[0]) {
                "provider" -> { account(); PluginOutcome.OpenPluginPage(descriptor.id, actionId) }
                "add" -> if (p.size == 1) PluginOutcome.Menu(copy("action.choose_provider", "选择服务商"), CloudAsrProvider.entries.map { PageAction("add/${it.key}", it.displayName(copy), PluginIcon.MIC) })
                    else { val next = settings.add(CloudAsrProvider.of(p[1])); PluginOutcome.OpenPluginPage(descriptor.id, "provider/${asrEncode(next.id)}") }
                "toggle" -> { settings.toggle(id()); PluginOutcome.RefreshUi }
                "select" -> { settings.select(id()); PluginOutcome.RefreshUi }
                "remove" -> { val c = account(); PluginOutcome.Confirm(copy("action.remove_account", "移除此账户？"), c.name, PageAction("remove-confirm/${asrEncode(c.id)}", copy("account.remove", "移除"), PluginIcon.CLOSE, symbol = "delete"), cancelLabel = copy("feedback.cancel", "取消")) }
                "remove-confirm" -> { settings.remove(id()); PluginOutcome.RefreshUi }
                "edit" -> {
                    val c = account(); val field = asrFields(c, copy).firstOrNull { it.key == p.getOrNull(2) } ?: throw LocalizedSettingIssue(copy("action.settings_gone", "此设置项已失效"))
                    PluginOutcome.TextInput(field.label, "save/${asrEncode(c.id)}/${field.key}", field.value, secret = field.secret)
                }
                "save" -> {
                    require(context.inputs.containsKey("value")) { throw LocalizedSettingIssue(copy("action.input_retry", "请重新打开设置项并保存")) }
                    settings.update(id(), requireNotNull(p.getOrNull(2)), context.inputs.getValue("value").trim()); PluginOutcome.RefreshUi
                }
                else -> PluginOutcome.Message(copy("action.result_gone", "此操作已失效，请重新打开页面"))
            }
        } catch (error: CloudSettingIssue) { PluginOutcome.Message(copy(error.key, requireNotNull(error.message))) }
        catch (error: LocalizedSettingIssue) { PluginOutcome.Message(requireNotNull(error.message)) }
        catch (_: IllegalArgumentException) { PluginOutcome.Message(copy("action.account_check", "请检查账户设置")) }
          catch (_: IllegalStateException) { PluginOutcome.Message(copy("action.result_gone", "此操作已失效，请重新打开页面")) }
    }
}
