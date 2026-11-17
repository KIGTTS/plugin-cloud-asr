package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*

internal data class AsrField(val key: String, val label: String, val value: String, val secret: Boolean = false)

internal fun asrFields(c: CloudAsrConfig, copy: (String, String) -> String = { _, fallback -> fallback }): List<AsrField> = buildList {
    add(AsrField("name", copy("account.name", "账户名称"), c.name)); add(AsrField("endpoint", copy("account.endpoint", "服务地址"), c.endpoint))
    add(AsrField("apiKey", when (c.provider) {
        CloudAsrProvider.TENCENT -> "SecretId"; CloudAsrProvider.ALIYUN -> "AccessKey ID"
        CloudAsrProvider.AZURE -> "Speech Key"; else -> "API Key"
    }, c.apiKey, true))
    if (c.provider !in setOf(CloudAsrProvider.AZURE, CloudAsrProvider.DEEPSEEK)) add(AsrField("apiSecret", when (c.provider) {
        CloudAsrProvider.TENCENT -> "SecretKey"; CloudAsrProvider.ALIYUN -> "AccessKey Secret"; else -> "Secret Key"
    }, c.apiSecret, true))
    if (c.provider in listOf(CloudAsrProvider.ALIYUN, CloudAsrProvider.IFlytek)) add(AsrField("appId", if (c.provider == CloudAsrProvider.ALIYUN) "AppKey" else "App ID", c.appId))
    if (c.provider != CloudAsrProvider.DEEPSEEK) add(AsrField("language", copy("field.language", "语言代码"), c.language))
    if (c.provider !in listOf(CloudAsrProvider.AZURE, CloudAsrProvider.ALIYUN, CloudAsrProvider.DEEPSEEK)) add(AsrField("model", when (c.provider) {
        CloudAsrProvider.TENCENT -> copy("field.tencent", "识别模型（留空使用通用模型）")
        CloudAsrProvider.BAIDU -> copy("field.baidu", "模型 ID（留空使用普通话）")
        CloudAsrProvider.IFlytek -> copy("field.iflytek", "业务领域（留空使用 iat）")
        else -> copy("field.note", "模型备注（可选）")
    }, c.model))
}

internal fun asrSettingsPage(settings: CloudAsrSettings, pageId: String, copy: (String, String) -> String = { _, fallback -> fallback }): PluginPage? {
    if (pageId == "settings") return PluginPage(pageId, copy("plugin.title", "云端语音识别"), listOf(
        PageBlock.Group(copy("account.group", "服务账户"), listOf(PageBlock.Actions(listOf(PageAction("add", copy("account.add", "添加账户"), PluginIcon.ADD)))))
    ), header = ManagementItem("cloud-asr-header", copy("plugin.title", "云端语音识别"), copy("plugin.description", "选择云端服务识别语音"), PluginIcon.MIC, symbol = "cloud"),
        management = ManagementList("cloud-asr-services", settings.all().map { c -> ManagementItem(
            id = c.id, title = c.name, subtitle = if (!c.configured) copy("account.pending", "待配置") else if (c.provider == CloudAsrProvider.DEEPSEEK) copy("account.auto_lang", "自动识别语言") else c.language, icon = PluginIcon.MIC,
            group = copy("account.added", "已添加的账户"), primaryAction = "provider/${asrEncode(c.id)}", symbol = "cloud", badges = listOf(c.provider.label),
            selected = settings.current()?.id == c.id,
            actions = listOf(PageAction("remove/${asrEncode(c.id)}", copy("account.remove", "移除"), PluginIcon.CLOSE, symbol = "delete")),
            toggle = ManagementToggle(c.enabled, "toggle/${asrEncode(c.id)}"),
            selection = ManagementSelection("select/${asrEncode(c.id)}", enabled = c.enabled && c.configured)
        ) }, emptyText = copy("account.empty", "添加一个云端服务账户")))
    if (!pageId.startsWith("provider/")) return null
    val id = runCatching { asrDecode(pageId.substringAfter('/')) }.getOrNull()
    val c = settings.all().firstOrNull { it.id == id } ?: return PluginPage(pageId, copy("account.group", "服务账户"), listOf(PageBlock.Notice(copy("account.removed", "此账户已移除"))))
    fun input(field: AsrField) = PageBlock.Input(field.label, field.value, "edit/${asrEncode(c.id)}/${field.key}", secret = field.secret)
    val fields = asrFields(c, copy)
    val (connection, recognition) = fields.partition { it.key !in listOf("language", "model") }
    return PluginPage(pageId, c.name, listOf(
        PageBlock.Group(copy("account.connections", "连接信息"), connection.map(::input)),
        PageBlock.Group(copy("account.options", "识别选项"), if (recognition.isEmpty()) listOf(PageBlock.Text(copy("account.auto_lang", "自动识别语言"))) else recognition.map(::input)),
        PageBlock.Group(copy("account.single", "账户"), listOf(PageBlock.Toggle(copy("account.enable", "启用账户"), c.enabled, "toggle/${asrEncode(c.id)}", icon = PluginIcon.MIC),
            PageBlock.Actions(listOf(PageAction("select/${asrEncode(c.id)}", copy("account.use", "使用此账户"), PluginIcon.STAR, symbol = "check")))))
    ), header = ManagementItem("cloud-asr-account", c.name, c.provider.label, PluginIcon.MIC, symbol = "cloud"))
}

internal fun asrEncode(value: String) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
internal fun asrDecode(value: String) = String(java.util.Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
