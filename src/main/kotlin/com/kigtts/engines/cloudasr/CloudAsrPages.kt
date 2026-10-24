package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.*

internal data class AsrField(val key: String, val label: String, val value: String, val secret: Boolean = false)

internal fun asrFields(c: CloudAsrConfig): List<AsrField> = buildList {
    add(AsrField("name", "账户名称", c.name)); add(AsrField("endpoint", "服务地址", c.endpoint))
    add(AsrField("apiKey", when (c.provider) {
        CloudAsrProvider.TENCENT -> "SecretId"; CloudAsrProvider.ALIYUN -> "AccessKey ID"
        CloudAsrProvider.AZURE -> "Speech Key"; else -> "API Key"
    }, c.apiKey, true))
    if (c.provider != CloudAsrProvider.AZURE) add(AsrField("apiSecret", when (c.provider) {
        CloudAsrProvider.TENCENT -> "SecretKey"; CloudAsrProvider.ALIYUN -> "AccessKey Secret"; else -> "Secret Key"
    }, c.apiSecret, true))
    if (c.provider in listOf(CloudAsrProvider.ALIYUN, CloudAsrProvider.IFlytek)) add(AsrField("appId", if (c.provider == CloudAsrProvider.ALIYUN) "AppKey" else "App ID", c.appId))
    add(AsrField("language", "语言代码", c.language))
    if (c.provider !in listOf(CloudAsrProvider.AZURE, CloudAsrProvider.ALIYUN)) add(AsrField("model", when (c.provider) {
        CloudAsrProvider.TENCENT -> "识别模型（留空使用通用模型）"
        CloudAsrProvider.BAIDU -> "模型 ID（留空使用普通话）"
        CloudAsrProvider.IFlytek -> "业务领域（留空使用 iat）"
        else -> "模型备注（可选）"
    }, c.model))
}

internal fun asrSettingsPage(settings: CloudAsrSettings, pageId: String): PluginPage? {
    if (pageId == "settings") return PluginPage(pageId, "云端语音识别", listOf(
        PageBlock.Group("服务账户", listOf(PageBlock.Actions(listOf(PageAction("add", "添加账户", PluginIcon.ADD)))))
    ), header = ManagementItem("cloud-asr-header", "云端语音识别", "选择云端服务识别语音", PluginIcon.MIC, symbol = "cloud"),
        management = ManagementList("cloud-asr-services", settings.all().map { c -> ManagementItem(
            id = c.id, title = c.name, subtitle = if (c.configured) c.language else "待配置", icon = PluginIcon.MIC,
            group = "已添加的账户", primaryAction = "provider/${asrEncode(c.id)}", symbol = "cloud", badges = listOf(c.provider.label),
            selected = settings.current()?.id == c.id,
            actions = listOf(PageAction("remove/${asrEncode(c.id)}", "移除", PluginIcon.CLOSE, symbol = "delete")),
            toggle = ManagementToggle(c.enabled, "toggle/${asrEncode(c.id)}"),
            selection = ManagementSelection("select/${asrEncode(c.id)}", enabled = c.enabled && c.configured)
        ) }, emptyText = "添加一个云端服务账户"))
    if (!pageId.startsWith("provider/")) return null
    val id = runCatching { asrDecode(pageId.substringAfter('/')) }.getOrNull()
    val c = settings.all().firstOrNull { it.id == id } ?: return PluginPage(pageId, "服务账户", listOf(PageBlock.Notice("此账户已移除")))
    fun input(field: AsrField) = PageBlock.Input(field.label, field.value, "edit/${asrEncode(c.id)}/${field.key}", secret = field.secret)
    val fields = asrFields(c)
    val (connection, recognition) = fields.partition { it.key !in listOf("language", "model") }
    return PluginPage(pageId, c.name, listOf(
        PageBlock.Group("连接信息", connection.map(::input)),
        PageBlock.Group("识别选项", recognition.map(::input)),
        PageBlock.Group("账户", listOf(PageBlock.Toggle("启用账户", c.enabled, "toggle/${asrEncode(c.id)}", icon = PluginIcon.MIC),
            PageBlock.Actions(listOf(PageAction("select/${asrEncode(c.id)}", "使用此账户", PluginIcon.STAR, symbol = "check")))))
    ), header = ManagementItem("cloud-asr-account", c.name, c.provider.label, PluginIcon.MIC, symbol = "cloud"))
}

internal fun asrEncode(value: String) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
internal fun asrDecode(value: String) = String(java.util.Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
