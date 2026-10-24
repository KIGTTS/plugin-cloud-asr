# 云端语音识别

为 KIGTTS 提供可选的云端语音识别接口。支持腾讯云、阿里云、Arzue、百度云和讯飞；每个服务商可以添加多个接口配置，只有用户主动添加并启用的配置才会参与识别，远端模型不会自动填充到列表。

API 密钥仅保存在插件自己的本地数据目录中，接口地址必须使用 HTTPS。插件不会在没有配置时发起网络请求。

## 构建

需要 JDK17、Android SDK36、PowerShell7。克隆时获取固定版本的 plugin-sdk 子模块。

```powershell
./gradlew.bat testDebugUnitTest assembleDebug
./Package.ps1 -SigningKey /path/to/publisher-private.der -AndroidSdk /path/to/android-sdk
```

许可：MIT，见 [LICENSE](LICENSE)。第三方许可见 [NOTICE.md](NOTICE.md)。
