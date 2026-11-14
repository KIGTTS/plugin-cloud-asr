# 云端语音识别

在 KIGTTS 中使用腾讯云、阿里云、Azure、百度云、讯飞或 DeepSeek 识别语音。添加账户、填写凭据，再选用需要的账户即可；每家服务商均可添加多个账户。DeepSeek 只需 API Key，自动识别语言。

插件只处理一段完整录音，输入为 16 kHz 单声道 PCM。请搭配语音识别服务 0.8.3 或以上使用，由服务切分连续录音并逐句上屏。阿里云与百度云的访问 Token 会自动获取和续期。DeepSeek 使用 [官方 ASR 接口](https://api-docs.deepseek.com/zh-cn/api/asr/)，将 PCM 封成 WAV 通过表单上传。其它接口见 [协议说明](PROTOCOLS.md)。

本轮已通过协议请求与页面回归，真实服务的识别效果仍需使用有效账户验证。

## 构建

需要 JDK17、Android SDK36、PowerShell7。克隆时获取固定版本的 plugin-sdk 子模块。

```powershell
./gradlew.bat testDebugUnitTest assembleDebug collectPluginDependencies
./Package.ps1 -SigningKey /path/to/publisher-private.der -AndroidSdk /path/to/android-sdk
```

许可：MIT，见 [LICENSE](LICENSE)。第三方许可见 [NOTICE.md](NOTICE.md)。
