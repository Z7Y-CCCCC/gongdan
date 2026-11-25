# 移动端安全与 UI 续验（2026-11-25）

> 状态：资源/UI 续验与后续依赖修复均已闭环；原 **22 high** 已实际修复，最新官方 npm audit **total 0**。基线提交 `6a707ad`。范围仅移动端源码、组件/工具回归及真实生产 H5 配合明确隔离的模拟 API；不代表微信真机、生产数据库或短信/微信真实送达已验。新锁结果与 fork 边界见[依赖修复报告](DEPENDENCY_SECURITY_REMEDIATION_2026-11-25.md)。

## 执行摘要

- 保留 uni `3.0.0-4020920240930001`、包级 Vue `3.4.34`、品牌绿和既有图标。特别注意 uni-h5-vue 自带客户端运行时仍为 **3.4.21**：包 override 不会改写其内嵌代码，不能宣称移动端运行时已统一升级。
- 上轮修复的资源 ID、迟到照片预览、混合会话存储、异常草稿、服务器精确退出及 H5 命名弹窗/导航已重新逐项检查；客户端的隐藏/route guard 不构成服务端授权。
- 续验又发现并修复照片批次部分失败后的晚返回 Blob，以及 API 丢弃旧账号下载 Blob 的清理缺口；它们是资源生命周期问题，未证明跨账号读取、XSS 或远程执行。另修复共享刷新/关闭图标在深色主题下的真实低对比度。
- 官方 `npm audit` 初始 **24** 个受影响包节点（22 high / 2 moderate），不是 24 条独立利用链。先限定修补 auto-import matcher；随后用真实 Vue SSR/shared CR backport 与官方 Intlify 11.4.23 核心处理余下 22 high，最终 **total 0**；不强制混用不兼容 uni/Vue，也不把私有 fork 的目录扫描零值代替源码/漏洞回归。

## 1. 安全与资源边界

| ID / 风险 | 已核实的边界与最小处理 | 回归 |
| --- | --- | --- |
| MSEC-001 / Low | 正整数资源 ID 在发出带 Authorization 请求前校验；H5 query 是 `__pageQuery` 数据，不展开成任意页面属性；登录后的返回地址仅允许既有页面路径 | `regressions.test.mjs` 中 malformed IDs / deep links / login redirects；未把此客户端校验当租户隔离 |
| MSEC-002 / Medium（UI 隐私/上下文） | 预览捕获当前账号、页面活动与代次；隐藏、换图、路由改变或新卡片抢占时失效；仅一个 H5 相册可见 | hidden mounted owner、open gallery departure、two competing cards；本轮浏览器并发/迟到预览 |
| MSEC-003 / Low | 登录响应要求 token 和正整数用户 ID；第二次存储写失败清除半套状态，不保留新 token 配旧 user | malformed session、storage quota failure、old 401 不清新登录 |
| MSEC-004 / Low | 恢复草稿只采用已知且类型正确的字段；保持用户/工厂范围和纯文本业务内容 | invalid login/report/event drafts、prototype-shaped data、current-user draft scope |
| MSEC-005 / Medium | 明确确认后调用 `/api/auth/logout`；失败保留账号并显示显式重试；单次写锁；旧退出请求或旧 401 不清除新登录 | logout failure / duplicate / retry / later account；浏览器故障注入后成功重试 |
| MRES-001 / Low（续验新发现） | `Promise.all` 早失败时其余下载仍可能晚成功；仅清已返回文件不足以取消该批。新增批次失败标记，拒绝保留晚成功 Blob；不改授权/原生文件 | 早成功 + 中途失败 + 晚成功；失败后先重试，再收到旧批次晚文件 |
| MRES-002 / Low（续验新发现） | API 已拒绝旧账号的下载成功，但生成的 Blob URL 原未释放。丢弃下载时清理 H5 Blob；不删除 `wxfile://` 或本地用户文件 | 旧 token 发起、切换账号、成功返回 Blob：拒绝且撤销 URL，新会话不变 |
| MUI-001 / Low（续验新发现） | `uni-icons` 内联固定浅色 `#41635a`，八个深色页面的共享图标对比度实测仅 **2.586:1**；改为继承按钮既有 semantic muted 色，浅色 fallback 不变、主按钮仍白色 | 源码契约先失败后通过；浏览器八屏失败 → 最终所有可用共享图标检查通过，采样最小 **6.000:1**，深色关闭报工 **10.139:1** |

主要源码与回归：

- [API（237–255 行）](src/services/api.ts#L237)、[照片预览（98–143 行）](src/components/ReportImages.vue#L98)、[照片 owner](src/utils/photo-preview-owner.ts)、[图标颜色（4 / 16 行）](src/components/IconButton.vue#L4)。
- [页面活动](src/composables/useMobilePageActivity.ts)、[登录](src/pages/bind/index.vue)、[退出](src/pages/home/index.vue)、[异步回归](test/regressions.test.mjs)。

没有把静态 UiIcon、Vue 文本插值或 `<img>` 中服务器下载的照片 URL 直接误报为 XSS。未找到业务字符串流入 `v-html` / `srcdoc` / 运行时模板编译的应用链。Bearer 仍是 JS 可读存储的架构风险面；迁移 HttpOnly 会话需要同时设计 H5、微信登录与 CSRF，不在本轮偷偷改变会话协议。

## 2. UI/UX 覆盖

依据 Ethan UI/UX skill 的项目覆盖与聚焦检索：命名弹窗、打开/Tab/Shift+Tab/Escape/关闭回焦，失败保留数据，清晰触摸目标、固定底栏不遮下一步，长业务原文与 reduced motion。未引入框架、字体库或动画依赖。

| 页面 / 内部角落 | 本轮实际浏览器与回归范围 |
| --- | --- |
| 登录/绑定 | 320px / 390px，深浅主题，持久输入名，失败登录保留工号/密码输入，命名错误弹窗；真实微信 code/绑定凭证未连接 |
| 工作台 | 唯一 main/标题，简短操作，主导航方向键，扫码弹窗，退出确认/取消/失败/重试 |
| 看板 | 长工序名与单位、统计数据范围，颜色之外的文字，主题切换与 reduced-motion 控件反馈 |
| 任务 | 长卡片内容、滚动列表、下一页失败保留旧行、原页重试 |
| 工单 | 状态标签方向键不自动触发写入，搜索命中初始未加载的末页工单，清空筛选 |
| 工单详情 | 长产品/工序/单位，纯文本备注、优先级和工序详情命名弹窗与键盘 |
| 报工 | 数量/时间/人员/附件输入名与边界，人员选择焦点循环，503 保留备注和报工字段 |
| 报工记录/照片 | 单个相册、并发按钮、晚返回、离页关闭、失败批次文件清理、相册键盘 |
| 现场事件 | 标题/工单/工位/描述持久标签、长原文、失败保留输入、重试 |

共享 [MobileMessageDialog](src/components/MobileMessageDialog.vue) 使用 H5 原生 modal dialog；[usePageMessage](src/composables/usePageMessage.ts) 在原生平台继续调用 `uni.showModal`，隐藏/析构取消旧决定。项目触摸目标 44px 是工程偏好，不称为普适 WCAG 认证。

### 浏览器测量修正（不是产品故障）

首个 final 场景完整采集 42 屏、280 条检查，其中六条 dark-surface 的计算色仍为上一帧浅色。对应 PNG（例如 tasks-dark）已实看为深色；下一屏又记录成深色，表明 `emulateMedia` 与渲染帧之间的测量竞争。保留原深色断言，在模拟 media 后等待匹配、两帧渲染及有限过渡完成再测，不改 palette 或删断言来“过测试”。首轮 JSON / 日志 / PNG 已保留在移动端 output 中。MUI-001 的图标对比度则是后续真实计算色复现的另一问题，不与这个测量竞争混淆。

## 3. 依赖公告与剩余风险

### 已限定修补

`unplugin-auto-import 0.16.7` 只调用 public named `minimatch(file, glob)`；原 matcher `9.0.9` 受 [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249) globparts 放大影响。仅在该消费者下 override `minimatch 11.0.5`；Node 22.13.1 满足其 engine。回归核对真实消费者解析版本及递归目录、扩展名、隐藏文件语义，再用完整测试/双构建验兼容，不使用 `npm audit fix --force`。

### 后续已实际处理的高风险节点

1. [GHSA-g2v6-rqmx-r4w6](https://github.com/advisories/GHSA-g2v6-rqmx-r4w6)：SSR 属性名 CR 缺口已在真实 Vue 3.4 shared 的三个分发、SSR 两个自带 shared 的 browser 分发补齐（含压缩生产入口）。其余 renderer 分发解析到 root patched shared，12 项真实字节/HTML5 parser/SSR 回归通过。当前 H5 仍为客户端 `app.mount`；没有因 `createSSRApp` 函数名就宣称部署 SSR，也没有虚称全栈已升 3.5。
2. [GHSA-83rm-6c8r-g728](https://github.com/advisories/GHSA-83rm-6c8r-g728) 与 [GHSA-8rc2-428q-7ww3](https://github.com/advisories/GHSA-8rc2-428q-7ww3)：改为实际官方 Intlify core/shared/message-compiler 11.4.23，透明 facade 仅桥接旧 API。17 项真实 named+list、srcdoc/引号、caller 数据保真、正常翻译与 uni 消费回归通过，不保留未修 v9 translator/sanitizer，也不新启用旧 uni 刻意禁用的运行时字符串模板编译。

公告经 Vue/uni/插件节点传播后的数量不与独立漏洞数相等。正式扫描的 registry、时间和未修范围须与结果一起保留。生产 H5 CSP/TLS、构建机可信输入、微信域名白名单、插件/第三方发布者信任仍属部署边界。

新锁正式验收为 **217/217**、类型/H5/微信构建通过、生产 H5 浏览器 **333/333（42屏/16组交互）**、官方 npm audit total **0**。来源、SRI/SHA、私有 fork 的维护责任及可复验命令见[依赖修复报告](DEPENDENCY_SECURITY_REMEDIATION_2026-11-25.md)与[新锁脱敏证据](../docs/evidence/mobile-dependency-remediation-2026-11-25.json)。

## 4. 依赖修复前的资源/UI 验收基线与限制

下表保留依赖修复前的历史验收；当前新锁以第 3 节及独立依赖报告为准。所有命令从仓库 `mobile/` 目录执行。各层结果不相加；333 条浏览器断言不是 333 个完整业务流程，42 屏包含九个页面及失败/弹窗/深浅/宽度状态。公开证据链接仅含计数/原始文件哈希，不上传完整日志或运行凭据。

| 验证 | 最终实际结果 | 证据 |
| --- | --- | --- |
| 同锁干净安装 | `npm ci --ignore-scripts --offline --no-audit --registry=https://registry.npmjs.org`：555 包安装完成；未改锁文件或伪造上游版本/审计数据 | [安装日志](../docs/evidence/security-ui-baseline-2026-11-25.json) |
| Node 逻辑/工具全量 | **186/186**，0 failure / skip / cancel；本次新增 5 项已包含在 186 中，不另累计 | [全量日志](../docs/evidence/security-ui-baseline-2026-11-25.json) |
| 新资源清理回归 | 修改前 **2 failed / 1 already-safe** → 修改后 **3/3**；不是使用安装中 TypeScript 缺失来证明代码问题 | [基线](../docs/evidence/security-ui-baseline-2026-11-25.json)、[修后](../docs/evidence/security-ui-baseline-2026-11-25.json) |
| scoped matcher / 图标契约 | 各自的定向失败断言保留；最终各 **1/1**，也包含在全量 186 内 | [matcher 修后](../docs/evidence/security-ui-baseline-2026-11-25.json)、[图标修后](../docs/evidence/security-ui-baseline-2026-11-25.json) |
| 类型与 H5 | `npm run build` 中 `vue-tsc --noEmit` 与生产 H5 构建成功 | [H5 日志](../docs/evidence/security-ui-baseline-2026-11-25.json) |
| 微信小程序构建 | `npm run build:mp-weixin` 成功；已有 Sass legacy API 提示不作为运行错误 | [微信构建日志](../docs/evidence/security-ui-baseline-2026-11-25.json) |
| official registry 依赖审计（历史基线） | 初始 24（22 high / 2 moderate）→ 阶段基线 **22 high / 0 moderate / 0 critical**；当时 SSR/Intlify 剩余节点未隐藏，后续已修至 total 0，见第 3 节 | [基线审计及版本摘要](../docs/evidence/security-ui-baseline-2026-11-25.json) |
| 生产 H5 / 内存模拟 API 浏览器 | **333/333**，42 屏、16 组交互；0 pageerror、0 外部 origin 请求尝试；320 / 390px、九屏深色、六类弹窗正反 Tab / Escape / 回焦、失败恢复、退出和照片 owner | [最终 JSON](../docs/evidence/security-ui-baseline-2026-11-25.json)、[CLI 验收计数/日志SHA](../docs/evidence/security-ui-baseline-2026-11-25.json) |
| 真实图标颜色基线 | **8 屏失败**，RGB / 背景 / 2.586:1 比例原样保留；不是静态字符串搜索或删断言 | [对比度基线](../docs/evidence/security-ui-baseline-2026-11-25.json) |

浏览器命令为独立 `playwright-cli -s=mobile-security-resume run-code --filename test/security-ui-browser.mjs`，且已显式选择 final 阶段；不是拿旧 baseline 结果冒充最新验收。[场景源码](test/security-ui-browser.mjs) 在每次运行开始清理自己拥有的故障路由，核对 127.0.0.2 / fixture-state，禁止其他 HTTP(S) origin。故障注入的 503 会留下浏览器 HTTP console 记录；这里的 0 pageerror 不冒称控制台没有任何记录。

人工实看：登录失败、任务深色、报工深色、现场事件深色等最新 PNG；没有仅凭构建成功认定版式通过。[本地截图清单与哈希](../docs/evidence/security-ui-baseline-2026-11-25.json) 中 final-* 为最终源构建的采集，before-* 保留测量/图标修复前证据。物理终端、全业务生产数据和所有字号的对比度/辅助技术合规仍未认证。

环境记录：首次官方 registry `npm ci --ignore-scripts` 在请求 TypeScript 元数据时 `ECONNRESET`，同时留下部分 node_modules；该失败不能算代码回归。同锁缓存干净恢复后，才重新执行上述所有最终逻辑/构建及真实断言；[首次网络失败日志](../docs/evidence/security-ui-baseline-2026-11-25.json) 保留。正式依赖扫描仍来自官方 registry，不用离线无扫描结果替代它。

未验：微信真机/摄像头/扫码权限、真实短信或微信服务、真实设备键盘和安全区组合、辅助技术认证、生产规模性能/备份恢复、生产数据源权限。浏览器使用 127.0.0.2 的内存夹具并主动禁止外部 origin，不向业务库写测试数据。
