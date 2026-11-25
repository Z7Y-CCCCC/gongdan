# 安全与 UI 续验总览（2026-11-25）

> 基线 `6a707ad`。本轮只使用本机源码、生产构建产物、明确隔离的模拟 API、loopback HTTP 和私有 H2/临时文件；**没有连接生产系统或验证真实业务凭据**。本地验收与 GitHub CI 分开记证：CI 以本轮实际提交的 Actions 运行结果为准，不沿用基线的成功 CI。

## 1. 本轮具体维护

- **资源与会话边界（BSEC）**：照片下载补齐查看者/上传者/关联报工授权；客户端地址统一采用显式可信代理规则。退出只撤销所提供的精确 token，不用旧退出响应清掉新报表 cookie。原生 UReport 仅平台超级管理员可进入，旧全局模板保留，普通业务报表权限不改。
- **安全导出与保真（FSEC-01/02、FUX-10）**：重置密码不再进入全局 notice；默认 XLSX 使用文本单元格，保留公式形状文本、OOXML literal/CR、前导零与普通负数。用户 XLSX 明确请求无 hash 业务列；原始敏感 CSV 备份及 legacy API 默认协议有意保留，不静默改字节。打印最终 serializer 按 merged master 只准备一次，不能用内存 cell 断言代替文件回读。
- **异步操作与键盘（FUX）**：本次维护的工单、产品、四类基础资料批写/导入和危险确认捕获账号、目标和 lookup；账号/栏目/失活变化取消后续调度，晚 picker、FileReader 和旧 finally 不覆盖新状态。监控/日志失败可见并可重试，不假空/假零。部门本轮3个失败用例补齐实际 dialog/title、入焦、Tab/Shift+Tab、两层 Escape 与回焦；原生报表离开/关闭/刷新/退出守卫已接入，实际编辑专项见下表。
- **移动与构建**：修复照片批次部分失败后晚返回、旧账号下载 Blob 的释放及深色图标低对比度。管理端保留 Vue 3.5/Vite 路线，Vue 补丁至3.5.42；App 的68个 phantom `.value` 是 Volar/TS 不可达分支 narrowing 问题，17个旧条件改为 `(false as boolean)`，仍静态裁剪且完整类型检查通过，不靠降依赖或排除 App。
- **后续移动依赖修复**：22 个 high 传播节点的三个根公告已实际处理；Vue 3.4 使用有来源/许可证/真实漏洞回归的 CR 安全 backport，Intlify 翻译核心采用官方 11.4.23 与最小旧 API facade。最新官方 npm audit 为 **0**，新锁完整回归 **217/217**、类型/双构建及浏览器通过；[修复详单][MOBILEDEPS]明确保留 fork 维护责任与内嵌 CSR 未统一升级的边界。
- **部署契约**：[legacy Compose][LEGACY] 已加 local-only 警告，80/3001绑定 loopback；仍含示例账号及运维工具，不用于生产或透过隧道公开。[主 Compose][COMPOSE] 与 [Windows环境样例][WINENV] 承接显式可信代理和 Secure-cookie flags；这不表示实际生产链路已验。

## 2. 已确认验证结果

**下面各行是不同层次/重叠子集，不能加成一个“总测试数”。**

| 层次 | 结果与证据 |
| --- | --- |
| 管理端完整 Vitest | **65文件、782/782**；单 worker threads，[验收计数/日志SHA][FRONTTEST] |
| 管理端 pretest | Node UI **16**、插件 UI **42**、i18n **83** 分别通过；不是额外的 Vitest 文件数 |
| 管理端构建/依赖 | 正式 [build通过][FRONTBUILD]；[官方 npm audit][FRONTAUDIT] **0**，只代表此包/锁文件/公告时点 |
| 移动端（依赖修后新锁） | **217/217**，类型/H5/微信构建通过；新生产 H5+模拟 API **333/333、42屏、16组交互**，0 pageerror/外部 origin 请求尝试；[汇总][MOBILEPROOF] |
| 移动端依赖 | [官方审计及真实补丁证据][MOBILEAUDIT] **22 high → 0，所有 severity total 0**；不把目录未识别的私有 fork 仅凭扫描零值当成安全证明 |
| 后端最终完整回归 | UTC测试JVM `clean package` **1016项：1015通过、0失败/错误、1个未启用MySQL opt-in自然跳过**；[日志][BACKTEST] |
| 后端产物一致性 | 96个新鲜XML suite；**23个相关class+1配置资源**与可执行JAR及现有隔离API副本逐字节一致，[证明][BACKPROOF]。旧默认/UTC **1006** 是分页补丁前源码，不冒充最终结果 |
| 管理端专项 B/F | 真实生产dist+模拟 API **28/28**，0 pageerror/外部请求；[结果][BF] |
| 管理端逐页 B/H | 真实隔离API/H2，**40工作区、58/58**，0 pageerror/非故障注入5xx；[结果][BH]，不复写40页表 |
| 独立 HTTP / 下载 | 新边界 [27/27][HTTP]、核心 [20/20][CORE]、实际下载 XLSX回读/ZIP [5项][DOWNLOAD] 各自通过，不与浏览器或单元集求和 |
| 运维与差异 | [配置契约22/22][OPS]；另有[4次真正Compose实渲染][COMPOSERENDER]（默认/显式flags × 默认/全部profiles，后者7/7服务），没有启动/构建/拉取容器；`git diff --check` 已通过，不等于真实TLS/防火墙/备份演练 |
| 原生报表实际编辑守卫专项 | **15/15**；[实际原生编辑与分步CLI结果][NATIVEPROOF]：4种真实confirm取消都保留同一iframe/文本，取消退出不发撤销请求，接受离页后可实际退出，旧原生cookie立即401，模板写入0 |

UTC指测试JVM设置；应用仍保留既有 Asia/Shanghai 初始化，不把它解释为所有业务默认时区都已切到UTC。

## 3. 有意保留的风险与未验边界

- **原始 CSV 备份风险有意保留**：公式前缀与用户 hash 列可能敏感，不宜直接用 Excel 打开，文件须按敏感备份保管；默认 UI XLSX无 hash 不等于 legacy CSV 缺省接口已去 hash。
- **已派发写入不能撤回**：前端guard可停止后续行/日志和晚UI写回，已发送请求可能完成，部分成功不会自动回滚。组件同实例换账号实验受主App `key=token`/栏目key边界，不能夸大为生产跨租户利用。
- **依赖修复仍有明确边界**：上述移动端 22 high 已修复，官方扫描 total 为 0；安全 backport 的漏洞行为与上游字节单独验收，不是整个 uni/Vue 内嵌客户端统一升级，也不是私有 fork 的永久安全认证。Java SCA尚未逐项匹配完整依赖公告，不能用 npm audit0或JVM回归替代。
- **仍未验**：生产TLS/CSP/可信代理链、MySQL账户/迁移和Redis恢复、对象存储/备份容量、微信真机/摄像头/短信及企业外部服务、第三方插件/报表引擎底层能力和全部原生报表排版。JS可读token仍是架构风险面；没有把 iframe、document.write 或顺序ID本身当成已利用漏洞。

本轮不承诺每个漏洞或每个嵌套角落已清零。所有本轮专用CLI浏览器、64040隔离JVM、53009模拟夹具、51791真实API代理及51789接收夹具均已关闭；验证产物保留，未动生产服务。

## 4. 追溯入口

详细规则、失败基线及逐页范围：[后端审计][BACKAUDIT] · [管理端审计][FRONTAUDITDOC] · [移动端审计][MOBILEAUDITDOC]。

关键可复验入口：[隔离JVM的HTTP脚本:1–18][HTTPSCRIPT] · [原生报表CLI:1–12][NATIVESCRIPT] · [移动生产H5专项:1–20][MOBILESCRIPT]；核心实现：[操作scope:4–20][SCOPESOURCE] · [XLSX准备工具:41–84][XLSXSOURCE]。公开源码采用仓库相对链接；日志/截图的公开入口只提供脱敏计数和原始文件 SHA256，完整产物保留本地，不接受任意生产目标来替换本机夹具。

[BACKAUDIT]: ../backend/SECURITY_AUDIT_2026-11-24.md
[FRONTAUDITDOC]: ../frontend/SECURITY_UI_AUDIT_2026-11-24.md
[MOBILEAUDITDOC]: ../mobile/SECURITY_UI_AUDIT_2026-11-25.md
[FRONTTEST]: evidence/security-ui-baseline-2026-11-25.json
[FRONTBUILD]: evidence/security-ui-baseline-2026-11-25.json
[FRONTAUDIT]: evidence/security-ui-baseline-2026-11-25.json
[MOBILEPROOF]: evidence/mobile-dependency-remediation-2026-11-25.json
[MOBILEAUDIT]: evidence/mobile-dependency-remediation-2026-11-25.json
[BACKTEST]: evidence/security-ui-baseline-2026-11-25.json
[BACKPROOF]: evidence/security-ui-baseline-2026-11-25.json
[BF]: evidence/security-ui-baseline-2026-11-25.json
[BH]: evidence/security-ui-baseline-2026-11-25.json
[HTTP]: evidence/security-ui-baseline-2026-11-25.json
[CORE]: evidence/security-ui-baseline-2026-11-25.json
[DOWNLOAD]: evidence/security-ui-baseline-2026-11-25.json
[OPS]: evidence/security-ui-baseline-2026-11-25.json
[LEGACY]: ../deploy_compose.yml
[COMPOSE]: ../docker-compose.yml
[WINENV]: ../deploy/windows/gongdan.prod.env.example
[HTTPSCRIPT]: ../tools/functional/check-security-http.mjs
[NATIVESCRIPT]: ../tools/functional/check-ureport-browser.ps1
[MOBILESCRIPT]: ../mobile/test/security-ui-browser.mjs
[SCOPESOURCE]: ../frontend/src/composables/usePageOperationScope.ts
[XLSXSOURCE]: ../frontend/src/utils/spreadsheet.ts

[NATIVEPROOF]: evidence/security-ui-baseline-2026-11-25.json

[COMPOSERENDER]: evidence/security-ui-baseline-2026-11-25.json

[MOBILEDEPS]: ../mobile/DEPENDENCY_SECURITY_REMEDIATION_2026-11-25.md
