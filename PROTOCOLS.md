# 云端语音识别协议

版本 0.1.1，SDK API 28。设置页中的每个账户独立保存接口、凭据和识别参数；仅当前选用且已配置的账户参与识别。

| 服务商 | 接口 | 凭据 |
| --- | --- | --- |
| 腾讯云 | SentenceRecognition，2019-06-14；TC3-HMAC-SHA256 | SecretId、SecretKey |
| 阿里云 | NLS 一句话识别 REST；CreateToken RPC 获取并缓存 Token | AccessKey ID、AccessKey Secret、AppKey |
| Azure | Speech to Text short audio REST；自动封装 PCM 为 WAV | Speech Key；地址包含区域 |
| 百度云 | server_api 短语音识别；OAuth client_credentials | API Key、Secret Key |
| 讯飞 | IAT WebSocket；HMAC-SHA256 签名、40 ms 音频帧、动态结果替换 | API Key、API Secret、App ID |

输入为 16 kHz、16 位、单声道原始 PCM；单次不超过 60 秒。持续输入按最多 15 秒分段识别，属于分段识别，不是所有厂商原生实时流式服务。阿里云海外账户需同时使用相应地区的 NLS 地址与 AppKey。可修改完整接口地址。

HTTP/WebSocket 请求支持取消并在结束后关闭资源；响应大小受限；厂商错误中的凭据会脱敏。Token 根据有效期复用。不会通过本地“配置已填写”宣称凭据联网验证成功。

本轮验证为注入传输的协议与页面回归、Android 编译和依赖收集；未提供真实凭据，尚未进行厂商实云识别验收。`arzue` 旧配置会迁移为 `azure`，占位域名会替换为真实默认地址。

## 官方文档

- [腾讯云一句话识别](https://cloud.tencent.com/document/api/1093/35646)
- [阿里云获取 Token](https://help.aliyun.com/zh/isi/getting-started/use-http-or-https-to-obtain-an-access-token)
- [Azure 短音频识别](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-speech-to-text-short)
- [百度短语音识别](https://ai.baidu.com/ai-doc/SPEECH/Jlbxdezuf)
- [讯飞语音听写](https://www.xfyun.cn/doc/asr/voicedictation/API.html)