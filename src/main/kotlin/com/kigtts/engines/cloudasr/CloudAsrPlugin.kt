package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*
import java.util.Base64

class CloudAsrPlugin : KigPlugin {
    override val descriptor = PluginDescriptor("engine.cloud-asr", "云端语音识别", "配置云端识别接口并按需添加服务", version = "0.1.0", license = "MIT")
    private lateinit var settings: CloudAsrSettings
    private lateinit var engine: CloudAsrEngine
    override val asr: AsrEngine get() = engine
    override suspend fun attach(environment: PluginEnvironment) { settings = CloudAsrSettings(environment.storage); engine = CloudAsrEngine(settings) }
    override val contributions = PluginContributions(settingsEntries = listOf(
        SettingsEntry("cloud-asr", "云端语音识别", "settings", PluginIcon.MIC, "audio", "音频", "按需添加云端识别接口", 30)))
    override fun page(pageId: String, context: PluginContext): PluginPage? {
        if (pageId != "settings" && !pageId.startsWith("provider/")) return null
        if (pageId.startsWith("provider/")) return providerPage(decode(pageId.substringAfter('/')))
        val header = ManagementItem("cloud-asr-header", "云端语音识别",
            "按需添加服务商，配置后再选择要使用的接口", PluginIcon.MIC, symbol = "cloud")
        val blocks = listOf(
            PageBlock.Notice("支持腾讯云、阿里云、Arzue、百度云和讯飞。服务商只提供配置模板，模型不会自动加入。"),
            PageBlock.Actions(listOf(PageAction("add", "添加识别服务", PluginIcon.ADD)))
        )
        val current = settings.current()
        val items = settings.all().map { c ->
            val configured = c.configured
            ManagementItem(
                id = c.id,
                title = c.name,
                subtitle = "${c.provider.label} · ${if (configured) "已配置" else "待配置"} · ${c.language}",
                icon = PluginIcon.MIC,
                selected = current?.id == c.id,
                group = "识别服务",
                primaryAction = "provider/${encode(c.id)}",
                actions = listOf(
                    PageAction("select/${encode(c.id)}", if (current?.id == c.id) "当前服务" else "选用", PluginIcon.STAR),
                    PageAction("remove/${encode(c.id)}", "移除", PluginIcon.CLOSE)
                ),
                toggle = ManagementToggle(c.enabled, "toggle/${encode(c.id)}"),
                symbol = "cloud",
                badges = listOf(c.provider.label),
                selection = ManagementSelection("select/${encode(c.id)}", enabled = c.enabled && configured)
            )
        }
        return PluginPage(pageId, "云端语音识别", blocks,
            management = ManagementList("cloud-asr-services", items, emptyText = "还没有添加云端识别服务"),
            header = header)
    }
    private fun providerPage(id: String): PluginPage {
        val c = settings.all().firstOrNull { it.id == id } ?: return PluginPage("provider/$id", "识别服务", listOf(PageBlock.Notice("服务不存在")))
        fun input(label: String, field: String, value: String, secret: Boolean = false) = PageBlock.Input(label, value, "save/${encode(c.id)}/$field", secret = secret, icon = PluginIcon.SETTINGS)
        val header = ManagementItem("cloud-asr-provider", c.name,
            "${c.provider.label} · ${if (c.configured) "已配置" else "待配置"}", PluginIcon.MIC, symbol = "cloud")
        return PluginPage("provider/$id", c.name, listOf(
            PageBlock.Group("接口设置", listOf(input("名称", "name", c.name), input("接口地址", "endpoint", c.endpoint),
                input("API Key", "apiKey", c.apiKey, true), input("API Secret", "apiSecret", c.apiSecret, true),
                input("应用 ID", "appId", c.appId), input("模型或接口版本", "model", c.model), input("语言", "language", c.language))),
            PageBlock.Notice("接口地址必须使用 HTTPS；密钥只保存在本机插件数据中。"),
            PageBlock.Actions(listOf(PageAction("test/${encode(c.id)}", "测试接口", PluginIcon.HEARING)))
        ), header = header)
    }
    override suspend fun execute(actionId: String, context: PluginContext): PluginOutcome {
        val p = actionId.split('/')
        return when (p[0]) {
            "add" -> if (p.size == 1) PluginOutcome.Menu("选择服务商", CloudAsrProvider.entries.map { PageAction("add/${it.key}", it.label, PluginIcon.MIC) })
                else { settings.add(CloudAsrProvider.of(p[1])); PluginOutcome.RefreshUi }
            "toggle" -> { settings.toggle(decode(p[1])); PluginOutcome.RefreshUi }
            "select" -> { settings.select(decode(p[1])); PluginOutcome.RefreshUi }
            "remove" -> { settings.remove(decode(p[1])); PluginOutcome.RefreshUi }
            "save" -> { settings.update(decode(p[1]), p.getOrNull(2) ?: error("缺少设置项"), context.inputs["value"].orEmpty()); PluginOutcome.RefreshUi }
            "test" -> { val c = settings.all().first { it.id == decode(p[1]) }; require(c.configured) { "请先填写 API Key" }; PluginOutcome.Message("已保存 ${c.provider.label} 配置，可在识别服务中选用") }
            else -> if (p.size == 2 && CloudAsrProvider.entries.any { it.key == p[1] }) { settings.add(CloudAsrProvider.of(p[1])); PluginOutcome.RefreshUi } else PluginOutcome.Message("未知识别服务操作")
        }
    }
    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
    private fun decode(value: String) = String(Base64.getUrlDecoder().decode(value))
}
