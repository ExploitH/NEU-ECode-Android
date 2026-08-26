# NEU eCode 协议选课开发状态

更新时间：2026-08-17 06:03 UTC

## 当前结论

协议选课开发按主人指示暂停。选课系统已关闭，无法继续进行真实批次、目录、已选列表和课表的现场联调。

当前工作区已完成只读数据链路与界面接入，并保留本地提交模拟；真实提交仍由编译期常量禁用。2026-08-26 已从旧机 dirty 工作树恢复，并归档到本地分支 `archive/enrollment-readonly-20260817`。`main` 回到正式提交 `d890b90`（v5.33）。本轮没有生成或发布代表只读接线的新 APK。

- 仓库：`/root/android-projects/NeuECodeKotlin-open-source`
- 归档分支：`archive/enrollment-readonly-20260817`
- 基线提交：`d890b90`
- 归档方式：本地分支提交；未推送 GitHub，未提升版本号，未上传 Worker/R2
- 现场联调：阻塞，原因是选课系统关闭
- 源包备份：`/www/android-dev/artifacts/neu-ecode-enrollment-readonly-20260817.tgz`

## 已完成

### 1. 只读协议契约

只允许以下 5 个读取端点：

1. `POST /xsxk/elective/neu/xskb`：当前课表，表单编码
2. `POST /xsxk/elective/clazz/list`：课程目录，JSON 编码
3. `POST /xsxk/elective/volunteer/select`：方案/推荐课已选，JSON 编码
4. `POST /xsxk/elective/volunteer/xgxk/select`：校公选课已选，JSON 编码
5. `POST /xsxk/elective/select`：全部已选，JSON 编码

课程目录 body 包含 `teachingClassType/pageNumber/pageSize/orderBy`，非 `ALLKC` 时追加 `campus`。三类已选按“方案/推荐课 -> 校公选课 -> 全部已选”串行读取。

### 2. Cookie 与 transport

- Cookie 身份键改为 RFC 语义的 `name + domain + path`
- 服务端 `Set-Cookie` 先写共享 CookieJar，后续请求读取轮换后的值
- WebView Cookie 快照只合并允许的身份 Cookie，排除 `authorization/token/secret`
- 所有选课读取请求由 `Mutex` 严格串行
- 统一限制为最多 2 QPS
- transport 类型层只暴露 5 个只读端点，不提供写端点方法
- 401/403 和明确的登录失效业务消息归类为会话失效

### 3. 会话适配

- 新增非导出的 `EnrollmentPortalActivity`
- 从已认证的 `jwxk.neu.edu.cn` 页面动态提取 `Authorization`、`batchId`、批次名称、`typeCode`、校区和课程分类
- 敏感会话只写入进程内 `EnrollmentSessionStore`
- token 和 batchId 不进入 Intent、导航参数、DataStore、领域读取模型或 UI 文案
- 退出登录或切换账号时清空选课会话

### 4. 数据层与解析器

- 课程目录支持嵌套 `tcList` 递归解析
- 数字字段兼容数字和字符串
- 显式 `0` 人数/容量不会错误继承父级值
- 缺少 `total` 时按本页行数保守推断是否还有下一页
- 课表按 `JXBID/KCH/KCM/SKJS/SKXQ/KSJC/JSJC/SKZC/SKZCMC` 映射
- 三类已选结果去重合并，单个接口普通失败可形成局部警告
- `secretVal` 不进入领域模型

### 5. 界面接入

选课页已改为 4 个视图：

- `课程`：动态分类、搜索、分页、官网已选标记、课表标记
- `已选`：三类已选合并结果和当前课表
- `待选`：本地待选池与权重调整
- `预览`：脱敏的本地提交模拟

页面支持未同步、加载中、成功、空结果、会话失效和局部读取失败状态。首屏读取顺序固定为“课表 -> 当前分类目录 -> 三类已选”。

## 安全边界

- `EnrollmentSandbox.LIVE_SUBMISSION_SUPPORTED = false`
- 生产 transport 中没有 `add`、`del`、`weightAdd` 端点
- 本地预览里出现的 `/clazz/add` 仅是脱敏展示字符串，不会构造或发送网络请求
- 不读取或保存 `secretVal`
- 不自动重试任何写请求；当前实现根本没有写请求客户端
- 不创建选课轮询、定时任务或后台抢课任务
- 应用原有余额同步和会话维护 Worker 与选课功能无关

## 已完成验证

停止前最后一轮真实工具输出：

- `:app:compileDebugKotlin`：成功
- 目标 JVM 单测：10/10 通过
  - `SerializedEnrollmentTransportTest`：3 个
  - `EnrollmentPayloadParserTest`：5 个
  - `EnrollmentPortalSessionDecoderTest`：2 个
- transport 测试覆盖 Cookie `seed -> A -> B -> C` 轮换、串行请求、JSON/Form Content-Type 和 401 会话失效
- 解析器测试覆盖课表字段、嵌套目录、字符串数字、显式零值、缺失 total、三类已选与 `secretVal` 不泄漏
- 会话解码测试覆盖 WebView `evaluateJavascript` 双层 JSON 和不完整结果拒绝
- `git diff --check`：通过

## 尚未完成或无法验证

1. 未运行整个项目的全量单测；只运行了上述选课相关目标测试。
2. 未执行本轮最终 `assembleDebug`，没有生成代表当前只读功能的新 APK。
3. 未在真机或模拟器上验证 Compose 页面和 ActivityResult 返回流程。
4. 未对关闭后的选课系统做真实 API 调用。
5. `Authorization/batchId` 提取脚本、课程分类字段和分页行为尚未用新一轮真实页面确认。
6. 未完成 Repository 调用顺序的独立 MockWebServer 集成测试。
7. `docs/ENROLLMENT_SANDBOX.md` 可能仍含旧的“完全离线”说明，恢复时需要与当前只读实现统一复核。

## 恢复开发入口

选课系统重新开放后，按以下顺序恢复：

1. `git checkout archive/enrollment-readonly-20260817`，确认工作区干净。
2. 使用测试账号/当前登录态打开 `EnrollmentPortalActivity`，只做只读现场验证。
3. 核对动态提取的批次名称、`typeCode`、校区和课程分类；日志与截图不得包含 token、Cookie、batchId 或 `secretVal`。
4. 逐个验证课表、目录和三类已选响应结构，必要时只保存脱敏夹具。
5. 补 Repository 顺序/局部失败测试和 ViewModel 状态测试。
6. 运行 `:app:testDebugUnitTest`、`:app:compileDebugKotlin`、`:app:assembleDebug`。
7. 在真机验证同步、分类、分页、刷新、会话过期、退出登录清理和本地预览。
8. 未经主人明确批准，仍不实现真实提交，也不把该归档分支推到公开 GitHub。

## 关键文件

- `app/src/main/java/com/neko/neuecode/data/remote/enrollment/SerializedEnrollmentTransport.kt`
- `app/src/main/java/com/neko/neuecode/data/remote/enrollment/EnrollmentPayloadParser.kt`
- `app/src/main/java/com/neko/neuecode/data/remote/enrollment/EnrollmentSessionStore.kt`
- `app/src/main/java/com/neko/neuecode/data/repository/EnrollmentRepository.kt`
- `app/src/main/java/com/neko/neuecode/ui/enrollment/EnrollmentPortalActivity.kt`
- `app/src/main/java/com/neko/neuecode/ui/screen/enrollment/EnrollmentViewModel.kt`
- `app/src/main/java/com/neko/neuecode/ui/screen/enrollment/EnrollmentScreen.kt`
- `app/src/main/java/com/neko/neuecode/domain/enrollment/EnrollmentReadModels.kt`
- `app/src/main/java/com/neko/neuecode/domain/enrollment/EnrollmentSandbox.kt`
- `app/src/test/java/com/neko/neuecode/data/remote/enrollment/SerializedEnrollmentTransportTest.kt`
- `app/src/test/java/com/neko/neuecode/data/remote/enrollment/EnrollmentPayloadParserTest.kt`
- `app/src/test/java/com/neko/neuecode/ui/enrollment/EnrollmentPortalSessionDecoderTest.kt`

## 旧交付物说明

先前 OSS 上的 `NEU-eCode-enrollment-sandbox-debug.apk` 仅代表旧的离线沙箱版本，不包含本状态记录中的完整只读接线。不要把旧 APK 的安装结果当作本轮实现的验收结果。
