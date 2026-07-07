# 东大码 Android

[English](README.en.md)

东大码 Android 是一个使用 Kotlin / Jetpack Compose 构建的东北大学 e码通辅助客户端，面向个人学习、研究与自用便利场景。

> 本仓库是经过清理的公开 Android 客户端源码。仓库不包含后端源码、Cloudflare Worker 源码、私有部署配置、账号数据、会话 Cookie、签名密钥、APK 私有下载链接、原始协议密钥材料或原始诊断日志。

## 功能特性

- Kotlin + Jetpack Compose + Material 3
- 原生协议登录，运行时从私有辅助服务获取协议配置
- 可选长效登录，使用 Android Keystore 支持的加密偏好存储
- OkHttp / WebView Cookie 同步
- e码通页面入口与二维码捕获支持
- 校园卡 / 网费余额同步支持
- 桌面小组件支持
- 充值 WebView，兼容旧 H5 支付页的弹窗窗口
- WorkManager 后台刷新任务
- Cloudflare Worker / R2 支持的版本检查与验证后更新下载
- 登录前用户协议与免责声明确认

## 运行时配置

公开构建默认访问维护者运营的辅助端点：

```text
https://echelp.exploith.com
```

客户端通过 `ECHELP_BASE_URL` 获取运行时协议配置与应用更新元数据。辅助后端属于私有基础设施，不是本仓库的一部分。Cloudflare Worker 代码、部署文件、对象存储配置、APK 私有链接、原始协议密钥材料和其他私有基础设施细节均有意排除在公开仓库之外。

客户端会把获取到的协议配置缓存在 Android Keystore 支持的加密偏好存储中，以降低短时间辅助服务不可用对已安装客户端的影响。

## 项目结构

```text
app/
├── data/
│   ├── local/          # DataStore、加密凭证/配置、Cookie 持久化
│   ├── remote/         # Retrofit API、协议配置、更新检查、加密辅助
│   └── repository/     # 认证、e码通、个人数据仓库
├── di/                 # Hilt 模块
├── domain/             # 领域模型与结果类型
├── ui/                 # Compose 页面、导航、主题、WebView 辅助
├── widget/             # 桌面小组件
└── worker/             # WorkManager 后台任务
```

## 构建

前置要求：

- JDK 17
- Android SDK API 35
- 本仓库自带 Gradle Wrapper

构建 debug APK：

```bash
./gradlew :app:assembleDebug
```

运行本地单元测试任务：

```bash
./gradlew :app:testDebugUnitTest
```

本地测试时覆盖辅助端点：

```bash
export ECHELP_BASE_URL="https://your-helper.example.com"
./gradlew :app:assembleDebug
```

## 更新流程

公开客户端期望私有辅助服务提供三类能力：

1. 运行时协议配置
2. 最新版本元数据
3. 通过验证后发放的 APK 下载链接

Android 侧流程见 [docs/CLIENT_UPDATE_FLOW.md](docs/CLIENT_UPDATE_FLOW.md)。私有 Worker / 后端实现、KV 数据、R2 配置、Turnstile 密钥和协议材料不随本仓库发布。

## 安全与隐私

- 长效登录凭证只在用户主动选择时保存。
- 凭证和缓存协议配置优先使用 Android Keystore 支持的 `EncryptedSharedPreferences`。
- 网络日志以元数据为主，并尽量脱敏敏感 header / body。
- 应用更新检查和验证后 APK 下载仅从 Android 客户端侧记录公开契约。
- 不要提交后端代码、Worker 代码、部署配置、对象存储配置、账号凭据、会话 Cookie、APK 签名密钥、原始诊断日志、APK/AAB 构建产物或私有下载链接。

## 用户协议与免责声明

应用内登录前会要求用户阅读并同意用户协议与免责声明。核心边界包括：

- 本应用不是东北大学或相关校园服务的官方应用。
- 用户应仅使用本人账号，并自行承担账号、设备与网络环境安全责任。
- 开发者不会在公开仓库中二次分发 RSA 密钥、私钥、会话票据、Cookie、原始抓包或其他敏感逆向材料。
- 用户自行抓包、逆向、提取、传播或重放请求导致的风险由用户自行承担。

## 状态

这是一个仍在活跃开发中的个人校园工具客户端的清理版开源快照。校园端点、页面和策略可能随时变化，相关功能需要在授权和合规前提下测试。

## 许可证

Apache License 2.0。详见 [LICENSE](LICENSE)。
