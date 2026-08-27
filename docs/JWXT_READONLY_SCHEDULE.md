# JWXT 只读课表协议

Android 客户端复用 `/www/neu-jwxt-schedule` 已验证的只读协议：

1. CAS 动态读取 `lt / execution / _eventId` 和 `login_neu.js` 公钥
2. `RSA-PKCS#1 v1.5(username + password)`，明文 `un/pd` 不进入 POST
3. 跟随 SSO 进入 `jwxt.neu.edu.cn`
4. 只调用 4 个读取端点：当前学期、校区、节次、我的课表

需要本机已开启长效登录（加密保存账号密码）。校园网 / OpenVPN 不可达时，教务接口会失败。

课表 UI 在 `feat/jwxt-readonly-schedule` 前端重构中消费本协议：本周网格、今日列表、只读详情。本文件只记录协议，不含 Cookie / 密码 / `secretVal`。
