# 东大码 Android

[English](README.en.md) · [GitHub](https://github.com/ExploitH/NEU-ECode-Android) · [Gitee 镜像](https://gitee.com/exploith/neu-ecode) · [最新发行版](https://gitee.com/exploith/neu-ecode/releases) · [许可证](LICENSE)

**东北大学校园生活的原生伴侣。**  
东大码 Android 以 Kotlin、Jetpack Compose 与 Material 3 重新编排付款码、课表与校园内网入口，让日常校园事务回到一台干净、克制、可审计的客户端里。

当前公开版本：**7.2**（`versionCode 70`）

> 本仓库是经过清理的公开 Android 客户端源码。仓库不包含后端源码、Cloudflare Worker 源码、私有部署配置、账号数据、会话 Cookie、签名密钥、APK 私有下载链接、原始协议密钥材料或原始诊断日志。

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="东大码 山川图标" />
</p>

<p align="center">
  <em>山川为志，河水为程。</em>
</p>

---

## 设计理念

东大码不是校园官方应用的替代品，而是一份面向个人学习、研究与自用便利的开源伴侣。它追求三件事：

1. **把真正常用的校园能力放到原生界面里**，而不是把整座门户塞进 WebView。
2. **让每一次等待都有名字**。同步进度、VPN 状态、登录初始化，都应该让人看见自己正在等待什么。
3. **把敏感材料留在设备与私有基础设施里**。公开仓库只留下可审计的客户端契约。

视觉上，7.0 统一为「山川」品牌：品牌蓝 `#0F45AF`、自适应启动图标、全屏 Lottie 加载，以及校园内网连接页的三态动画。

---

## 此刻能做的事

| 场景 | 7.0 里的样子 |
|---|---|
| 付款码 | 协议成功后居中绘制可扫二维码；失败才进入二级 WebView。桌面小组件走同一套协议码。 |
| 课表 | 只读 JWXT：7 列 × 12 节网格、学期选择、课表设定、相册式左右滑周。按周动态读取，邻周预加载。 |
| 校园内网 | 应用内官方 OpenVPN 3 学生隧道，分流而不劫持公网。Idle / Connecting / Connected 三态 Lottie。 |
| 我的 | 长效登录、余额入口、内网连接、关于与协议、清理缓存。 |
| 更新 | 私有辅助服务提供版本元数据；旧版会收到应用内更新提示。 |

底栏只有三件事：**付款码 / 课表 / 我的**。充值、内网、e码通 WebView 都是二级页面，不抢主航线。

---

## 7.0 亮点

- **首次课表同步更稳**：同名 Cookie 按 `domain + path + name` 共存，避免 JWXT 模块 SESSION 冲掉根 SESSION 导致的首次 403。
- **同步进度可见**：`1/7` 校园网探测到 `7/7` 整理课表。若 `2/7 正在登录教务` 超过 4 秒，才会出现「初次登入教务系统需要初始化资源，请耐心等待！」；同步结束才消失。
- **按周动态翻页**：取消整学期预组合。格子按当前周计算，Pager 只预组合左右各一周。
- **开学前文案分得清**：未填开学日，与已填但学期尚未开始，不再共用一句提示。
- **校园内网视觉系统**：未连接呼吸标、连接中复用山川加载、已连接数据雨。固定槽位、短 Crossfade，切换不跳动、不闪左上角。
- **只保留应用内隧道**：移除「备用：打开已安装的 OpenVPN」。
- **山川主题贯穿启动器、通知小图标与全屏加载**。

---

## 运行时配置

公开构建默认访问维护者运营的辅助端点：

```text
https://echelp.exploith.com
```

客户端通过 `ECHELP_BASE_URL` 获取运行时协议配置与应用更新元数据。辅助后端属于私有基础设施，不是本仓库的一部分。Cloudflare Worker 代码、部署文件、对象存储配置、APK 私有链接、原始协议密钥材料和其他私有基础设施细节均有意排除在公开仓库之外。

客户端会把获取到的协议配置缓存在 Android Keystore 支持的加密偏好存储中，以降低短时间辅助服务不可用对已安装客户端的影响。

---

## 项目结构

```text
app/
├── data/
│   ├── local/          # DataStore、加密凭证/配置、Cookie 持久化
│   ├── remote/         # Retrofit API、协议配置、更新检查、加密辅助
│   ├── repository/     # 认证、e码通、课表、个人数据仓库
│   └── vpn/            # 应用内 OpenVPN 3 服务与控制器
├── di/                 # Hilt 模块
├── domain/             # 领域模型、课表展示、VPN 状态映射
├── ui/                 # Compose 页面、导航、主题、Lottie、WebView
├── widget/             # 桌面小组件
└── worker/             # WorkManager 后台任务
```

更细的协议说明：

- 付款码只读协议：[docs/ECODE_PAYCODE_PROTOCOL.md](docs/ECODE_PAYCODE_PROTOCOL.md)
- JWXT 只读课表：[docs/JWXT_READONLY_SCHEDULE.md](docs/JWXT_READONLY_SCHEDULE.md)
- 应用内 OpenVPN 3：[docs/OPENVPN3.md](docs/OPENVPN3.md)
- 客户端更新流程：[docs/CLIENT_UPDATE_FLOW.md](docs/CLIENT_UPDATE_FLOW.md)

---

## 构建

前置要求：

- JDK 17
- Android SDK API 35
- 本仓库自带 Gradle Wrapper
- `minSdk` 23；应用内 OpenVPN 原生库按 API 24 交叉编译，Android 7.0+ 才能加载隧道核心

构建 debug APK：

```bash
./gradlew :app:assembleDebug
```

运行本地单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

本地测试时覆盖辅助端点：

```bash
export ECHELP_BASE_URL="https://your-helper.example.com"
./gradlew :app:assembleDebug
```

内存紧张的无头构建机建议串行、限制并行：

```bash
./gradlew --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx1408m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8' \
  :app:testDebugUnitTest :app:assembleDebug
```

---

## 更新流程

公开客户端期望私有辅助服务提供三类能力：

1. 运行时协议配置
2. 最新版本元数据
3. 通过验证后发放的 APK 下载链接

Android 侧流程见 [docs/CLIENT_UPDATE_FLOW.md](docs/CLIENT_UPDATE_FLOW.md)。私有 Worker / 后端实现、KV 数据、R2 配置、Turnstile 密钥和协议材料不随本仓库发布。

---

## 安全与隐私

- 长效登录凭证只在用户主动选择时保存。
- 凭证和缓存协议配置优先使用 Android Keystore 支持的 `EncryptedSharedPreferences`。
- 网络日志以元数据为主，并尽量脱敏敏感 header / body。
- 应用更新检查和验证后 APK 下载仅从 Android 客户端侧记录公开契约。
- 学生 VPN 配置、CA、tls-auth、`.ovpn` 与预编译 `.so` 不进入公开 git。
- 不要提交后端代码、Worker 代码、部署配置、对象存储配置、账号凭据、会话 Cookie、APK 签名密钥、原始诊断日志、APK/AAB 构建产物或私有下载链接。

---

## 用户协议与免责声明

应用内登录前会要求用户阅读并同意用户协议与免责声明。核心边界包括：

- 本应用不是东北大学或相关校园服务的官方应用。
- 用户应仅使用本人账号，并自行承担账号、设备与网络环境安全责任。
- 开发者不会在公开仓库中二次分发 RSA 密钥、私钥、会话票据、Cookie、原始抓包或其他敏感逆向材料。
- 用户自行抓包、逆向、提取、传播或重放请求导致的风险由用户自行承担。

---

## 状态

这是一个仍在活跃开发中的个人校园工具客户端的清理版开源快照。校园端点、页面和策略可能随时变化，相关功能需要在授权和合规前提下测试。

当前发行线：

| 版本 | versionCode | 说明 |
|---|---|---|
| 6.0 | 67 | 应用内 OpenVPN 3、协议取码大码、Sleepy 风格课表 |
| 7.0 | 68 | 首次同步 403 修复、同步进度、VPN 三态 Lottie、山川主题、按周动态翻页 |
| 7.1 | 69 | 优化课程显示组件的更新与检测逻辑，将周课表组件开发提上日程 |
| **7.2** | **70** | 优化了桌面小组件的视觉效果，将“每周课表”替换为可切换日期的单日课表 |

大陆访问优先走 [Gitee 镜像](https://gitee.com/exploith/neu-ecode)。GitHub 仍是主仓：`ExploitH/NEU-ECode-Android`。

---

## 许可证

GNU General Public License v3.0。详见 [LICENSE](LICENSE)。

本仓库整体按 GPL-3.0 分发。第三方组件保持各自许可证：

- OpenVPN 3 core：上游双许可 AGPL-3.0-only OR MPL-2.0，本客户端选择 **MPL-2.0**（见 `third_party/openvpn3/NOTICE`）
- Gradle Wrapper：Apache-2.0
- ZXing `com.google.zxing:core`：Apache-2.0
- Lottie Compose `com.airbnb.android:lottie-compose`：Apache-2.0
- 课表周网格 / 周次选择改编自 [Sleepy](https://github.com/lingion/sleepy)（GPL-3.0，见 `third_party/sleepy/NOTICE`）
