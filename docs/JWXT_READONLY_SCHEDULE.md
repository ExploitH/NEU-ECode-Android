# JWXT 只读课表协议

Android 客户端复用 `/www/neu-jwxt-schedule` 已验证的只读协议：

1. CAS 动态读取 `lt / execution / _eventId`，从页面引用的 `login_neu.js` / `login_neu_new.js` 获取 RSA 公钥
2. `RSA-PKCS#1 v1.5(username + password)`，明文 `un/pd` 不进入 POST
3. 跟随 SSO 进入 `jwxt.neu.edu.cn`
4. 只调用 4 个读取端点：当前学期、校区、节次、我的课表

需要本机已开启长效登录（加密保存账号密码）。校园网 / OpenVPN 不可达时，教务接口会失败。

课表 UI 在 `feat/jwxt-readonly-schedule` 前端重构中消费本协议：本周网格、今日列表、只读详情。本文件只记录协议，不含 Cookie / 密码 / `secretVal`。

## 新版统一认证前端兼容

新版密码页引用 `login_neu_new.js`，仍使用同一 RSA 密码协议和 `loginForm` 隐藏字段；额外的 `t_un / t_pd / t_c` 保留页面提供的空值。无需更换 RSA 算法或教务查询端点。

页面同时下发 `script[type="text/html"]` 内的短信登录、二次认证模板。模板包含“当前设备需进行身份验证”和示例手机尾号，但不代表当前需要验证。`CasSecondAuthParser` 在判断挑战及提取字段、图片、手机尾号前，先移除 script、template、style 和注释块；登录前后都复用这一判断，避免密码 POST 前误入短信分支，或登录失败后再次被模板误导。真实的二次认证表单、可见图形验证码仍停止自动登录并交给用户。

回归使用脱敏的真实页面 `app/src/test/resources/cas/login-neu-new.html`。保留空白隐藏字段和模板结构，动态 `lt / execution` 已移除；无凭据、Cookie 或 ticket。

验证命令：

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin
```

在线检查默认跳过。连接校园网后，显式设置 `NEU_LIVE_AUTH_FILE` 指向仓库外的私有双行凭据文件，再只运行 `NeuCasLiveSmokeTest`；它使用新建内存 CookieJar，验证真实 Kotlin/OkHttp 登录、课表、成绩、考试及会话复用，只输出聚合计数。不要在环境变量或命令行中直接传密码。若需要验证 Android Cookie 持久化和页面交互，还须运行模拟器。

本轮已验证：完整 JVM 测试（在线项默认跳过）、显式在线 Kotlin/OkHttp 读接口、内部 debug APK 构建，以及 API 35 模拟器中的 `DebugVpnProbeActivity`「实测课表」。模拟器通过宿主机的分流 OpenVPN 访问校园网；此次没有重新测试 App 内 OpenVPN 握手，也没有验证真实短信挑战发送流程。这些修复纳入 7.8 发行线，线上发布状态以版本服务为准。
