package com.kigtts.engines.cloudasr

import com.kigtts.plugin.api.PluginStorage
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

/** Providers are configuration templates. No remote voices or accounts are added automatically. */
enum class CloudAsrProvider(val key: String, val label: String, val defaultEndpoint: String) {
    TENCENT("tencent", "腾讯云", "https://asr.tencentcloudapi.com"),
    ALIYUN("aliyun", "阿里云", "https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/asr"),
    ARZUE("arzue", "Arzue", "https://api.arzue.example/v1/asr"),
    BAIDU("baidu", "百度云", "https://vop.baidu.com/server_api"),
    IFlytek("iflytek", "讯飞", "https://iat-api.xfyun.cn/v2/iat");

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } ?: ARZUE }
}

data class CloudAsrConfig(
    val id: String,
    val provider: CloudAsrProvider,
    val name: String,
    val endpoint: String,
    val apiKey: String = "",
    val apiSecret: String = "",
    val appId: String = "",
    val model: String = "",
    val language: String = "zh-CN",
    val enabled: Boolean = true,
) {
    val configured: Boolean get() = endpoint.startsWith("https://") && apiKey.isNotBlank()
    fun validate() { require(URI(endpoint).scheme == "https") { "接口地址必须使用 HTTPS" } }
}

internal class CloudAsrSettings(private val storage: PluginStorage) {
    private val file = storage.file("config/cloud-asr.json").also { storage.configuration }
    private val configs = mutableListOf<CloudAsrConfig>()
    var selectedId: String? = null

    init {
        runCatching {
            val root = JSONObject(file.readText())
            selectedId = root.optString("selected").takeIf { it.isNotBlank() }
            root.optJSONArray("configs")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val provider = CloudAsrProvider.of(item.optString("provider"))
                    val endpoint = item.optString("endpoint", provider.defaultEndpoint)
                    configs += CloudAsrConfig(item.optString("id").ifBlank { UUID.randomUUID().toString() }, provider,
                        item.optString("name", provider.label), endpoint, item.optString("apiKey"), item.optString("apiSecret"),
                        item.optString("appId"), item.optString("model"), item.optString("language", "zh-CN"), item.optBoolean("enabled", true))
                }
            }
        }
    }

    fun all(): List<CloudAsrConfig> = configs.toList()
    fun current(): CloudAsrConfig? = configs.firstOrNull { it.id == selectedId && it.enabled }
        ?: configs.firstOrNull { it.enabled && it.configured }
    fun add(provider: CloudAsrProvider): CloudAsrConfig {
        val config = CloudAsrConfig("${provider.key}-${UUID.randomUUID().toString().take(8)}", provider, provider.label,
            provider.defaultEndpoint)
        configs += config; selectedId = config.id; save(); return config
    }
    fun update(id: String, field: String, value: String): CloudAsrConfig {
        val index = configs.indexOfFirst { it.id == id }; require(index >= 0) { "找不到识别服务" }
        val old = configs[index]
        val next = when (field) {
            "name" -> old.copy(name = value)
            "endpoint" -> old.copy(endpoint = value)
            "apiKey" -> old.copy(apiKey = value)
            "apiSecret" -> old.copy(apiSecret = value)
            "appId" -> old.copy(appId = value)
            "model" -> old.copy(model = value)
            "language" -> old.copy(language = value)
            else -> error("不支持的识别服务设置")
        }
        next.validate(); configs[index] = next; save(); return next
    }
    fun toggle(id: String): CloudAsrConfig { val i = configs.indexOfFirst { it.id == id }; require(i >= 0); configs[i] = configs[i].copy(enabled = !configs[i].enabled); save(); return configs[i] }
    fun select(id: String) { require(configs.any { it.id == id }); selectedId = id; save() }
    fun remove(id: String) { configs.removeIf { it.id == id }; if (selectedId == id) selectedId = configs.firstOrNull()?.id; save() }
    fun save() {
        val array = JSONArray(); configs.forEach { c -> array.put(JSONObject().put("id", c.id).put("provider", c.provider.key)
            .put("name", c.name).put("endpoint", c.endpoint).put("apiKey", c.apiKey).put("apiSecret", c.apiSecret)
            .put("appId", c.appId).put("model", c.model).put("language", c.language).put("enabled", c.enabled)) }
        file.writeText(JSONObject().put("selected", selectedId).put("configs", array).toString(), Charsets.UTF_8)
    }
}
