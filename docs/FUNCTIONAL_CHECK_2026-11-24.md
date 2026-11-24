# 功能验收矩阵（2026-11-24）

本轮针对可确认的功能缺口、错误恢复、数据完整性、会话隔离及 UI 交互进行了修复和回归。以下区分真正的网络请求、数据库集成、组件测试和浏览器夹具；不把路由存在、配置保存或构建成功当成业务闭环，也不宣称所有潜在 bug 已清零。

测试仅使用独立 H2、临时文件、合成数据和明确的 loopback 接收器。未操作生产业务库；最终发布与 GitHub 状态由主代理收尾。

## 1. 验证类型与统计口径

| 标记 | 实际验证方式 | 不能据此推断 |
| --- | --- | --- |
| U/C | 单元、属性或组件测试；Vue/TS setup、jsdom、API/生命周期 mock 按测试需要使用 | 不是浏览器布局、真实网络或生产数据库验收 |
| I | Spring 服务、JPA/H2 和控制器集成；部分请求经 MockMvc | MockMvc 没有经过真实 Tomcat 网络与 multipart 限制 |
| H | 真实 HTTP socket、Tomcat 或本机下游接收器；存储仍为隔离测试环境 | 不是公网服务、实际微信终端或生产 TLS/数据库验收 |
| B/F | 真正 Chromium、生产前端/H5 或原始插件 HTML/JS；API 或父消息桥由隔离夹具响应 | 点击与 CSS 是真实的，但夹具响应不证明服务器业务正确 |
| B/H | 真正浏览器与隔离 Spring/H2 HTTP 服务联验 | 不是生产环境、真实设备或容量压测 |
| S | 类型检查、构建、静态契约和运维配置保护 | 不是业务运行、实际部署、恢复演练或可访问性认证 |

- 每个全量套件只记录最新确认结果。定向测试是全量套件的子集，不追加到总数；重复运行的 pretest 同样不重复累计。
- 浏览器的 `checks` 是场景断言，不是独立测试用例或独立功能数量。插件的 42 个 Node 测试、29 个浏览器检查和 14 个 Java 测试分别列示，不合成为“85 个功能”。
- 后端 191 个方法映射冒烟属于后端套件；预期 4xx 仅说明拒绝路径正常，不能算业务成功。上传、消息投递、单位契约等专项也不在 893 之外重复加数。
- 此前文档的 866/224/109、290/115 等数字属于早先阶段快照，本文件不与其累加。

## 2. 功能矩阵

| 功能区域 | 已验证的流程 / 本轮补全 | 验证类型与证据 | 范围或未验边界 |
| --- | --- | --- | --- |
| 认证、绑定、工厂和用户 | 登录/失败、授权码及用户边界、事务/并发、工厂和会话隔离；移动手动登录返回及草稿隔离 | U/C、I：[后端验收][B1]、[移动验收][M1]；最终 H 场景待 §3 收尾 | 实际短信、微信授权平台和真实账号未验；测试凭据不代表外部发送成功 |
| 基础资料 | 单位、不良项、部门、工序、客户、供应商、仓库实际创建、读回、修改、拒绝跨工厂操作、删除后读回；CSV 写入前解析/校验 | I：[B1]；管理端 U/C：[F1]、[F2] | 文件内容校验不等于任意旧数据或全部导入规模兼容 |
| 产品及 BOM | 产品管理、DECIMAL 精度和十进制字符串响应、定点编辑/换算、超限保留旧用料；自动编码不依赖同毫秒唯一性 | U/C、I：[B1]、[深查记录][D1]；管理端定向证据 [F1] | 外部调用方需适配数量字符串契约；未做生产规模物料计算压测 |
| 销售单、工单 | 原有业务完整性和并发回归；工单创建/编辑、自定义必填、批量删除部分失败闭环；主页单条只读详情导航 | U/C、I：[B1]、[D1]；B/F：[F5] | 主页详情浏览器使用夹具 API；真实 H 闭环另列，不混为同一证据 |
| 工单计量单位 | 管理/移动列表、服务端筛选、详情/编号查询、看板一致返回产品真实单位；单位改名后读回；缺失为 `null`；分页预加载避免 N+1 | I：[B1]；U/C、B/F：[M1] | 混合产品汇总不附伪统一单位；H2 查询次数不是 MySQL 生产性能承诺 |
| 工序任务、进度及卡片 | 权限复查、过滤后顺序、错误不假装 empty；卡片偏好读写、草稿失败保留、全部工序顺序和颜色显示 | U/C：[F1]、[F3]、[M1] | 偏好持久化测试与最终 HTTP/API 联验分开；颜色不代替业务状态文字 |
| 报工及审批 | 人员/权限、数量/时间、代报口径、待审/驳回/重报、单次写入和旧会话响应防护 | U/C、I：[B1]、[M1]；B/F：[M5] | 移动真实点击使用合成 API；最终真实 HTTP 报工→审批链路待 §3 确认 |
| 出入库与库存 | 入/出库单头与明细必填、失败不改库存、库存查询/统计和既有并发完整性；管理端提交错误闭环 | U/C、I：[B1]、[F2] | 未验生产库锁等待/吞吐；隔离 HTTP 库存变化另列于 §3 |
| 自定义字段 | 工单、出入库服务器必填/类型/日期/选项检查；历史工单未变旧值兼容；删除/保存失败重试和防重；移除未实现的假开关 | U/C、I：[B1]、[F2]、[D1] | 引用字段仍保存 opaque ID，不声称已实现关系查找/引用存在性验证；未向无字段入口的产品请求强加必填 |
| 普通附件 | 真 Tomcat 上传/下载 12MB，核对内容与下载头；Servlet 20MB 与普通附件入口一致，普通文件强制下载及 `nosniff` | H：`FunctionalUploadHttpIntegrationTest`，见 [B1]、[B2] | 报工图片另有更小服务限制；生产对象存储、公网下载及恢复未验 |
| 通知与内部沟通 | 列表失败保留真实旧数据；通知快照真实状态、已读/未读/归档与顶部计数；三人私聊隔离及 5,001 条无关消息不覆盖自己的预览 | U/C、I：[B1]、[D1]；B/F：[F5] | 通知每类历史补发仍有 50 条成本上限；不是完整历史无限回填或生产推送认证 |
| SOP、设备、质检、工资规则、现场事件 | 六类扩展记录严格对象 JSON/长度/类型；保存、更新、读取、删除实际 payload 与状态；管理端分页、账期/人员筛选、锁定和晚响应防护 | U/C、I：[B1]、[F1] | 证明配置/记录 CRUD，不把它描述成独立 SOP 执行、工资结算或设备自动采集引擎 |
| 消息发送测试 | 去掉 `SIMULATED_OK`；三种请求格式、服务端公众号 token、下游拒绝、重定向/过大响应限制、超时和防重；前端区分接口完成与 `success` | H：本机真实接收器；I/U/C：[B1]、[F1] | 接受请求不代表终端已读；真实企微/公众号/第三方端点未投递；不提交 token |
| 外部数据源 | 既有适配器、白名单/策略和映射回归；管理端错误、重试及输入保留 | U/C、I：[B1]、[F2] | 真实 JDBC/企业 API 凭据、公网连接、不同版本数据库和生产 TLS 未全验 |
| 插件中心及主系统消息桥 | 加载失败/重试、KeepAlive/会话隔离；locale/context 更新不重载；frame/window/origin/token/version 校验、epoch 及并发 32 上限 | U/C：[F3]；S：CSP/反向代理策略 [O1] | 主系统桥组件测试与独立插件 B/F 是不同层；未验第三方插件或生产代理安装升级 |
| 两个内置插件页面 | 握手/超时/刷新恢复、必填、异步 form 捕获、防双提交、连接与保存互斥；保存期间字段锁定并恢复原 disabled；POST 已提交后 GET 失败明确“已保存” | U/C：42 项 [P1]；B/F：29 检查 [P2]；Java 14 项 [P3]、[P4] | 浏览器是原始 HTML/JS 和真正跨 origin iframe，但父消息桥接隔离夹具；Java 服务测试不等于独立生产持久化验收 |
| 插件语言及主题 | English/中文往返保留输入、option 业务值与业务记录；仅翻译空备注 fallback；来源首次绑定后固定；真实浅/深色 CSS、原生色系、390px、Tab 和 reduced-motion | U/C：[P1]；B/F：[P2]、插件截图目录 | 无真实读屏器认证；明确状态文字仍保留，不只靠颜色 |
| 报表中心、UReport | 页面加载/错误/重试、入口导航与 iframe 语言消息已有组件回归；原生设计器/查询表单/打印/Excel 最终运行时联验由主代理收尾 | U/C：[F2]、[F4]；最终 B/H **待确认** | 此前原生浏览器实验使用旧 H2 与 bootstrap 模板 shim，只作诊断证据，不冒充最终包无 shim 验收；打印机、导出字体/企业模板未全验 |
| 移动首页/看板 | 数据库全范围聚合、日期/会话刷新、失败重试、最近工单和工序排名各 5 条；统计不下载 5,000 条后推断全集 | I：[B1]、[D1]；U/C、B/F：[M1]、[M5] | 聚合标量/41–42 工单夹具不是大规模生产数据；H2 4 查询不代表容量基线 |
| 移动工单、任务及历史 | 工单状态/关键词服务端搜索、20 条按需分页；任务 20→40/41、未下载工单查找；报工记录/履历服务器筛选与分页、失败保留前页并重试同页 | U/C：[M1]、[M2]；B/F：[M5] | 第 251 页回归证明可请求第 5,001 条位置，未声称插入 6,000 条数据库记录；已加载记录统计不伪称全量 |
| 移动报工表单/现场事件 | 上下文加载失败的常驻错误和重试；草稿/输入保留、重复写入和会话校验；上传/移除照片、提交/继续、现场事件返回刷新 | U/C：[M1]、[M2]；B/F：[M5] | 拍照硬件、断网系统级后台恢复与生产对象存储未验 |
| H5 扫码 | 相册/拍照入口、本地真实 QR 像素解码、文件选择识别 QA-042、手输降级；仅生成内部工单路由，拒绝外链/非法 ID/编码 | U/C：真实生成像素样本 [M2]；B/F：[M1]、移动截图目录 | 文件选择和像素解码不是摄像头权限/实际拍照验收；扫码照片不上服务器 |
| 报工照片及 H5 原生弹层 | 鉴权图片下载、自有全屏画廊；246×246 实图、Tab/Escape/焦点返回；补原生 API CSS，退出弹窗 300×175 可点击并回手动登录 | U/C：[M2]；B/F：[M6]、[M7] | H5 原生预览曾实测失败，已替换；微信仍走原生预览但未做微信真机确认 |
| 通用 UI、图标及国际化 | 删重复说明与全局表头装饰；明确图标有名称、不明确业务操作保留短名；危险取消、列设置零列保护、焦点循环与恢复、错误不假零、减弱动画 | U/C、S：[F1]–[F4]；B/F：[F5]；移动/插件对应证据 | 图标结构扫描/词典覆盖不是完整 AccName、WCAG 或翻译质量认证；生产业务名称及机器 CSV 头保持原值 |
| 构建、部署配置与运维工具 | 移动类型检查/H5/微信构建；两插件新 JAR 字节核对；Nginx/CSP、Compose 参数、Windows 保护、只读 MCP 协议检查 | S：[M3]、[M4]、[O1]–[O4]；管理端最终构建待 §3 | 只验证本地配置/构建保护，不等于已部署、实际 MySQL/Redis/S3 运行、备份恢复/PITR 演练或远端 CI 成功 |

## 3. 最终统计（不同类型不合并）

| 验证集 | 最新确认结果 | 口径 / 状态 |
| --- | --- | --- |
| 后端全量 `mvn -B clean package` | Maven 报告 **893** 项，失败 0、错误 0、跳过 1；14:36:08 打包成功 | [B2]；1 个显式 opt-in MySQL 测试类未开启，不称“893 个真实 HTTP 成功动作” |
| 移动全量 `npm test` | **130/130**，失败/跳过 0 | [M2]；定向子集不另加数 |
| 移动类型检查、H5、微信生产构建 | 成功 | [M3]、[M4]；编译不能代替微信真机验收 |
| 插件 HTML/JS Node 回归 | **42/42** | [P1]；两插件合计，不并入管理端 Vitest 数 |
| 插件 Chromium 真实 iframe 场景 | **29/29** 检查、0 pageerror | [P2]；API/桥响应为夹具，两份插件源码真实执行 |
| 插件 Java 服务测试 | 设备 **8/8**、质量 **6/6** | [P3]、[P4]；两插件 JAR 已重新 package，并逐字节比对最新静态文件 |
| 管理端全量 Vitest | **441/441**（47 个测试文件），另有 Node UI 16/16、插件 UI 42/42、国际化契约 83/83 | `output/functional-frontend-full-final.log`；Node 测试与 Vitest 分开记录 |
| 管理端类型检查/生产构建/最新国际化 | 成功；51 个 SFC 国际化编译、词典覆盖和占位符保护通过 | `output/functional-frontend-build-final.log`；生产构建包含最终语言资源 |
| 管理端 Chromium UI 夹具 | **20/20** 检查 | [F5]；1 次 HTTP 503 为主动注入故障；不累加到 Vitest |
| 最新隔离真实 HTTP 业务场景 | **20/20** 真实 HTTP 场景检查 | `output/functional-http-scenario-final.json`；真实登录、七类资料 CRUD、偏好、扩展记录、HTTP webhook、十进制 BOM、报工审批和库存变化 |
| 最新管理端→真实 API / 原生 UReport 浏览器联验 | **58/58** 检查，40 个真实工作区；原生设计器、查询表单和分页预览无 shim 验证 | `output/playwright/management-functional/browser-final.log`；`output/ureport-native-real-browser.log`、`output/ureport-native-preview-final-browser.log`；native HTML/JS 与最终 JAR、classpath、dist 一致 |
| 配置与运维保护 | CSP **12/12**；Compose **13/13**；Windows **33** 检查；只读 MCP 协议通过 | [O1]–[O4]；不同工具的检查指标分别列示，不冒称真实部署/恢复测试 |
| GitHub 更新与远端 CI | 随本次 `master` 提交更新；远端结果见 [GitHub Actions](https://github.com/Z7Y-CCCCC/gongdan/actions) | CI 已接入管理端、后端/MySQL、移动双端、两插件、运维与 Windows 保护；最终 commit 与远端检查结果在交付回复中报告 |

## 4. 证据索引与复跑

代码和日志路径以仓库根目录为基准。日志/截图属于本地忽略产物，文档与测试源码可随 GitHub 提交；没有要求把测试账号、token、运行时 classpath 或临时数据库提交到 Git。

- 后端：[功能说明][B1]；最终日志 [B2]；主套件包含 `FunctionalOperationsIntegrationTest`、`FunctionalUploadHttpIntegrationTest`、`MessageDeliveryIntegrationTest`、单位 DTO/查询与原有事务/并发测试。
- 管理端：定向日志 [F1]–[F4] 互相重叠，不能求和；UI 真实点击结果 [F5] 与截图 `output/playwright/management-ui/`。最终全量/构建及真实 API 场景由 §3 主代理收尾。
- 移动：[功能说明][M1]、全量 [M2]、构建 [M3]/[M4]、核心真实点击 [M5]、图片 [M6]、退出 [M7]；截图 `output/playwright/mobile-functional/`。QR 与图片检查使用实际像素，不是只替换解码函数。
- 插件：[Node 回归源码](../frontend/scripts/plugin-ui-functional.test.mjs) 与 [P1]；真实跨 origin iframe 场景 [P2]；Java [P3]/[P4]；浅/深色、窄屏、保存中、已提交后刷新失败、恢复截图在 `output/playwright/plugin-functional/`。自有浏览器及 5129/5130 夹具监听均已关闭。
- 独立 HTTP 场景：[运行脚本](../frontend/scripts/functional-api-scenario.mjs) 只接受带 `real-h2-api` 标记的隔离 loopback 夹具，不应改成生产域名后执行。

```powershell
# 从仓库根目录执行；浏览器夹具不可连接生产业务数据。
mvn -B -f backend/pom.xml clean package
npm --prefix frontend test
npm --prefix frontend run build
node --test frontend/scripts/plugin-ui-functional.test.mjs
npm --prefix mobile test
npm --prefix mobile run build
npm --prefix mobile run build:mp-weixin
mvn -B -f plugins/equipment-inspection/pom.xml test
mvn -B -f plugins/quality-management/pom.xml test
```

## 5. 发布前仍需独立确认

1. 真实 AppID、微信真机授权/预览、短信、摄像头权限与硬件拍照；真实企微、公众号及第三方消息送达/终端已读。
2. 企业外部数据库/API、公网域名、TLS、代理、真实 Redis/对象存储凭据与生产插件安装升级。模拟 provider 或 loopback 接收器不替代这些连接。
3. 本轮 H2 功能及查询次数验证不替代生产 MySQL 兼容/锁行为、容量基线、大量历史数据迁移与真实备份恢复/PITR 演练。前轮隔离 MySQL 记录见 [迁移兼容说明](MYSQL_MIGRATION_COMPATIBILITY.md)。
4. 原生 UReport 最终包和打印/Excel 已按 §3 完成局部 UI、真实 ExcelJS 回读与无 shim 浏览器验收；物理打印机、所有字体和业务模板组合未全验。
5. 引用型自定义字段关系校验、独立 SOP/设备采集/薪资结算引擎不在“配置 CRUD 已通过”的声明内。仍有限额的通知历史补发也不是无限历史遍历。
6. 浏览器视觉/键盘检查不等于完整辅助技术、所有屏幕/浏览器或可访问性认证。构建和本地 CI 同款命令通过不等于已经部署或远端 CI 已完成。

[B1]: ../backend/FUNCTIONAL_VALIDATION.md
[B2]: ../backend/output/functional-backend-final.log
[D1]: SYSTEM_DEEP_AUDIT_2026-11-24.md
[F1]: ../output/frontend-functional-targeted.log
[F2]: ../output/frontend-functional-support.log
[F3]: ../output/frontend-functional-extra.log
[F4]: ../output/frontend-functional-ureport-locale-tests.log
[F5]: ../output/playwright/management-ui/browser-qa.log
[M1]: ../mobile/FUNCTIONAL_CHECK_2026-11-24.md
[M2]: ../mobile/test/mobile-functional-all.log
[M3]: ../mobile/test/mobile-functional-build-h5.log
[M4]: ../mobile/test/mobile-functional-build-weixin.log
[M5]: ../output/playwright/mobile-functional/core-browser-final.log
[M6]: ../output/playwright/mobile-functional/photo-browser-final.log
[M7]: ../output/playwright/mobile-functional/logout-browser-final.log
[P1]: ../output/playwright/plugin-functional/plugin-ui-functional.log
[P2]: ../output/playwright/plugin-functional/plugin-browser-functional.log
[P3]: ../output/playwright/plugin-functional/equipment-plugin-maven-test.log
[P4]: ../output/playwright/plugin-functional/quality-plugin-maven-test.log
[O1]: ../output/functional-plugin-csp-tests.log
[O2]: ../output/functional-ops-compose-tests.log
[O3]: ../output/functional-windows-guards.log
[O4]: ../output/functional-mcp-tests.log
