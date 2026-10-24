# 云端语音识别

在 KIGTTS 中使用腾讯云、阿里云、Azure、百度云或讯飞识别语音。添加账户、填写凭据，再选用需要的账户即可；每家服务商均可添加多个账户。

支持短语音与持续录音的分段识别，输入为 16 kHz 单声道 PCM。阿里云与百度云的访问 Token 会自动获取和续期。具体接口、字段及限制见 [协议说明](PROTOCOLS.md)。

本轮已通过协议请求与页面回归，真实服务的识别效果仍需使用有效账户验证。

## 构建

需要 JDK17、Android SDK36、PowerShell7。克隆时获取固定版本的 plugin-sdk 子模块。

```powershell
./gradlew.bat testDebugUnitTest assembleDebug collectPluginDependencies
./Package.ps1 -SigningKey /path/to/publisher-private.der -AndroidSdk /path/to/android-sdk
```

许可：MIT，见 [LICENSE](LICENSE)。第三方许可见 [NOTICE.md](NOTICE.md)。