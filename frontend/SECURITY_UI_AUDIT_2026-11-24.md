# 管理端安全与 UI 复核（2026-11-24 基线，2026-11-25 续做）

> 状态：本轮已确认项已修复并完成本地复验；最终完整测试、构建、两套管理端浏览器与原生报表专项均已记录。基线 `6a707ad`；工作区 `仓库 checkout`。
> 本报告限定本地源码、普通组件/工具回归与明确标记的隔离夹具。浏览器端不是授权边界；服务端角色、工厂、附件和插件策略以独立后端/真实 HTTP 证据为准。

## 执行摘要

2026-11-25 续做已闭环默认安全 XLSX、明确原始 CSV 备份、打印 serializer 保真、批写/导入和晚 picker 的 captured scope、原生 UReport 平台 gate 及离开工作区守卫。部门弹窗此前只修成员容器；本轮新增3个键盘/语义失败回归，才补齐实际 dialog/title、入焦、Tab/Shift+Tab、两层 Escape 和回焦。

保留 Vue 3.5/Vite 技术路线、品牌色与既有图标体系，**Vue 已安全补丁更新到 3.5.42，Vite 为 6.4.6**，未做框架大版本重建。官方管理端 npm audit 当前为0；这不代表移动包、第三方插件、服务端或部署全部无风险。

正式 `npm run build` 已成功：[构建日志][E-BUILD]。最新完整 `npm test -- --pool=threads --maxWorkers=1`：65 文件、782/782；两套生产包浏览器分别为模拟故障交互28/28与真实H2的40工作区58/58，另有原生报表实际编辑/取消守卫15/15。不同层次不求和，不用旧截图、定向子集或构建绿替代。

## 1. 已确认安全问题（FSEC）

### FSEC-01 / Low：新密码被放到全局成功提示（已修）

- 原 `handleResetPassword` 将新密码拼入全局 notice，扩大肩窥/诊断收集的暴露面；不是服务器越权，也未证明未授权用户获取密码。
- [UserManagementPage.vue:317–349][S-USER] 现在仅提示目标账号密码已重置，请求密码/API与成功清空输入的契约不变。
- [PageCornerUi.test.ts][T-CORNER] 验证发送的密码完全相同，但 notice 不含它。此结论不等于禁止管理员按既有流程查看新建账号的初始凭据。

### FSEC-02 / Medium：原始 CSV 的文本公式风险（默认 XLSX 已接入）

- 原始 CSV 只做引号/分隔符转义，业务文本 `=1+1` 等前缀保持原样；直接用电子表格打开时可能被解释为公式/链接。未做真实 Excel 公式执行，不能称为 RCE 或已外传。
- Ethan 已确认并实施：默认可见导出是显式文本 XLSX，普通数字/负数保型；原始 CSV 仅作为明确备份入口，常显“不宜直接用Excel打开”。不静默添加/去掉原始 CSV 前缀或改机器列/导入语义。
- [spreadsheet.ts:41–84][S-XLSX] 的 `prepareXlsxCell` / `createXlsxBuffer` 拒绝 formula、hyperlink、richText 等对象，校验 Unicode、控制符及32767文本上限，普通数字/布尔保型，大数/高精度转文本；literal OOXML escape 与 CR 保真，快照同步捕获且只编码一次。
- 默认用户 XLSX 仅业务列：前端 [api.ts:492–503][S-API] 的 `fetchUsersExportBlob` **明确发送 includePasswordHash=false**，[UserManagementPage.vue:418–449][S-USER] 转成全量 XLSX并绑定当前页面/账号。显式敏感 CSV 备份仍发送 true、保留原列序/字节和密码 hash。
- **不能误写为服务端 legacy CSV 默认已去 hash：**[UserController.java:108–114][B-USER] 保留无参数时 includePasswordHash=true 的旧协议及 ADMIN/当前工厂授权；默认 UI 安全来自显式 false，而非改变机器客户端默认值。
- 工单/产品/四类基础资料、库存明细/余额/汇总的 XLSX 与备份入口均已接入。范围如实显示：库存明细当前页、余额 API 全匹配、汇总已完整加载范围、用户全量；不以屏幕10行假冒全量。
- [DefaultSpreadsheetExports.test.ts][T-EXPORT] 验证范围、原文/数值、备份字节、错误重试、防重以及失活/会话变更后抑制晚下载；实际文件编码另见 FUX-10。原始 CSV 仍不是“已净化的安全 Excel 文件”。

## 2. 已确认 UI/操作问题（FUX）

| ID | 影响 / 原因 | 已实现修复 | 验证阶段 |
| --- | --- | --- | --- |
| FUX-01 / Medium | CacheList 选择 A→B，慢 A 响应覆盖 B 的键/内容，误导后续清理 | 名称/键/值逐层 latest；切换清理关联数据，危险请求捕获目标 | 原组件基线失败→修后通过；新 B/F 28/28，键/内容迟到响应、清理防重及取消状态实测，见 [专项浏览器][E-BROWSER-UI] |
| FUX-02 / Medium | 监控失败假空/假零；OnlineUsers 缩页只改页码 | 常驻错误/重试；成功页码与数据一起提交，失败保留旧页并重试目标页 | [监控回归][T-MON]；独立 HTTP27另含极端分页/权限，不代表真实Redis验收 |
| FUX-03 / Medium | 清理/强退确认关闭后仍 pending，可重复写 | 写入锁、处理中确认、捕获目标，失活/账号变化取消上下文 | 9项监控定向回归已过；新 B/F 28/28，键/内容迟到响应、清理防重及取消状态实测，见 [专项浏览器][E-BROWSER-UI] |
| FUX-04 / Medium | 日志模块/类型/账号串响应；失败翻页旧数据配新页 | 单一上下文加载、latest取消、初始只读一次、失败可见/准确重试；dialog语义与键盘关闭 | [日志回归][T-LOG] 原5失败/1已安全，修后6通过；新 B/F 日志翻页503/原页保留/重试与窄屏已实测，见 [专项浏览器][E-BROWSER-UI] |
| FUX-05 / Medium | 旧成员Teleport不在父焦点范围；首次容器修复没有证明父dialog语义/循环/外层Escape | 成员披露留在实际modal；新增标准role/title、useDialogFocus、Tab双向循环和分层Escape/回焦；保存/失败草稿契约不变 | 本轮 [DepartmentDialogKeyboard][T-DEPT] **3失败基线→3通过**；39项合并集包含它，不能将先前结构检查描述成已验完整modal |
| FUX-06 / Low | 权限标签将真实部门/员工“保存”译成Save | 固定UI部分显式翻译，业务部分v-text；移除权限是具名原生按钮 | 已选标签/表格原失败回归修后通过 |
| FUX-07 / Low | 动态503假空；绩效无错误/重试 | 真实错误/重试/忙状态，代次/失活保护；未知KPI不伪造0 | 页面corner与旧轮询/KPI回归已过 |
| FUX-08 / Medium | UReport失活清iframe可能丢未保存修改 | App所有主菜单赋值、工作区关闭/其它/全部、退出和beforeunload接入同一守卫，确认发生在失活前；强制会话失效仍清旧数据 | [AppSession][T-APP] 与报表组件回归已过；平台cookie/原生HTML见HTTP27；完整原生嵌套编辑操作仍不冒称全验 |
| FUX-09 / Medium | 批量循环/晚picker重新取token，离开页面后继续调度；同实例菜单ABA可复活旧操作 | 本次维护的工单/产品/四类基础资料批写、导入及危险确认捕获token/目标/lookup；每个await前后guard，旧finally不清新状态；基础资料额外捕获menu+generation | [BaseData专用回归][T-BASE] **85/85**；晚picker12项先失败再修；方法/组件复用实验不夸大主App跨租户 |
| FUX-10 / Low | 打印Excel直接serializer把 literal escapes/CR静默改写，普通内存cell测试未发现 | 最终writeBuffer前每个merged master按对象Set只准备一次，复用prepareXlsxCell，样式/图片/模板不改 | [真实打印serializer][T-PRINT-ENC] **8项**（4种模板回读+4类输入拒绝）；[纯工具编码][T-XLSX] **36项**（含真实默认/合并布局回读和限界/对象拒绝），不说36项都执行真实Excel |

### 2.1. 关键实现边界与最新源码位置

- 导航：[App.vue:181–189、398–461、2922–2959][S-APP]；共享注册/去重/已确认变化 [workspaceLeaveGuards.ts:10–35][S-GUARD]。报表 [UReportPage.vue:29–60、88–106][S-UREPORT] 注册 analytics dirty guard；[reportLocale.ts:25–35][S-REPORT-LOCALE] 按原生 save 控件判定，未知控件不假称clean。
- 原生平台 gate：[UReportController.java:44–48][B-UREPORT] 建桥、[UReportAuthFilter.java:46–52][B-UREPORT-FILTER] 对cookie重新 requireSuperAdmin；普通工厂ADMIN/PRODUCTION不能仅改前端旗标就进入。普通业务报表权限/工厂语义未改为平台专用。
- 批写/导入：[WorkOrderPage.vue:1390–1420、1470–1764、1767–1841][S-WO]；[ProductDefinitionPage.vue:448–674][S-PRODUCT]；[BaseDataPage.vue:49–140、331–429、553–652、1109–1254、1825–1988][S-BASEDATA]；共同 [usePageOperationScope.ts:4–20][S-SCOPE]。
- picker 失效不能简单清成undefined再让旧change捕获新登录；基础资料保存旧scope直到change消费或下一次明确trigger，token+menu generation防ABA。FileReader未完成时取消、处理函数失效，错误可重试，early return也释放本次读锁。
- 真实打印入口 [WorkOrderPage.vue:2070–2370][S-WO]，master去重准备在2333–2342；不能对prepared文本或merged别名重复编码。
- 部门父dialog [DepartmentManagementPage.vue:47、71–76、329–369][S-DEPT]；成员第一层Escape关闭披露并回触发器，第二层允许父dialog关闭并回编辑按钮。3项普通组件键盘验证不等于所有浏览器/屏幕阅读器认证。

**已经派发的写入不能靠前端guard撤回。**它可阻止后续行/日志/晚UI回写，但首个已发送请求可能在服务端完成，此前成功批量行不自动回滚；普通pending promise测试不是服务端原子取消或生产压力验收。

### 2.2. FBUILD-01：App 的68个 phantom .value 类型错误（已兼容修复）

源模板原本是 unitFilterForm.name 等，并没有日志中的 .value。[Vue语言工具生成代码][E-VIRTUAL-BEFORE] 给被narrow过的绑定加入withDotValue assertion和.value访问；Vue语言工具3.3.14/TS5.7.2遇到17个静态 if(false) 块时丢掉assertion narrowing，回退reactive原类型，产生68个TS2339。

最小修复仅将17个旧内嵌视图条件换成 `v-if="(false as boolean)"`：[App.vue:3927–5288][S-APP]。仍永远不渲染；字段/业务策略保留，未降依赖、排除App、禁类型检查或删除不存在的.value。独立TS fixture和真实Vue+esbuild样本验证：类型断言擦除，假input/div分支可静态裁剪，只保留comment vnode：[擦除产物][E-ERASE]。最终 `vue-tsc --noEmit` 无错误：[类型检查日志][E-TYPE]，之后正式build通过。

## 3. 40 个真实工作区逐页清单

证据类别：S=源码/构建结构，U/C=组件与工具回归，B/F=浏览器+模拟 API，B/H=浏览器+真实隔离 HTTP/数据库。旧轮 `58/40` 只证明旧包路线/部分交互，**不能替代本轮新增状态/表单细节验收**。

| 工作区 | 页面 / 对应细节 | 本轮检查与关联回归 |
| --- | --- | --- |
| home | HomeDashboard、工序卡片设置、绩效、生产动态、详情导航 | 范围/偏好/颜色与顺序、设置保存失败、动态/KPI 失败恢复、工单 ID 导航；FUX-07 |
| dashboard | Dashboard 统计、进度/单位/图表 | S：真实数据与非色彩文字、比例与无数据；当前生产规模/实际屏幕组合未全验 |
| notifications | NotificationCenter 查询、已读/未读/归档、分页 | U/C：旧请求/错误/计数隔离；正文作为文本与业务名保留 |
| advanced | SOP/设备/质量/工资规则/现场事件/消息配置 | U/C：JSON/必填、保存/更新/删除/重试、防重/筛选；真实外部消息凭据/送达未验 |
| report | ReportCenter 业务报表及原生报表入口 | 业务报表保留原工厂/角色语义；原生 analytics 仅平台超管，导航守卫已接入；FUX-08 |
| production-sales | SalesOrder 创建/编辑/删除/明细 | U/C：事务入口、必填、保留草稿、批量部分失败；客户业务文本只作文本 |
| production-workorder | WorkOrder 列表/只读定位/新建编辑/工序/物料/自定义字段/附件/打印 | U/C：动态必填、BOM 十进制、部分删除、精确 ID；批写/导入 scope、默认 XLSX 和四类真实打印文件回读；FUX-09/10 |
| production-task | Task 查询、筛选、任务/工序明细 | S、既有 U/C：查询错误、顺序、权限重新检查；不拿前端隐藏当权限 |
| production-reporting | ReportApproval 查询/审批/驳回/明细 | U/C：单次写入、驳回必填、错误/草稿、状态文字 |
| production-workbench | 同 ReportApproval（独立入口） | 与报工共享组件不是多算测试；检查入口/导航与操作含义 |
| inventory-inbound | InboundReceipt 创建/明细/动态字段/删除 | U/C：单头明细必填、失败不改库存、草稿/错误；图片/备注文本 |
| inventory-outbound | OutboundReceipt 创建/明细/动态字段/删除 | U/C：权限/数量/失败闭环；不把前端禁用当库存锁 |
| inventory-detail | InventoryDetail 日期筛选/分页/列设置/导出 | U/C：服务器时区筛选；FSEC-02 默认 XLSX、当前页范围与原始备份边界 |
| inventory-balance | InventoryBalance 产品明细/筛选/全匹配导出 | U/C：偏好、错误、筛选/明细导航；API 全范围导出不拿表格 10 行代替 |
| inventory-summary | InventorySummary 日期/汇总/排序/导出 | U/C：全分页读入与日期界限；FSEC-02 汇总导出保留数值/业务原文 |
| basedata-product | ProductDefinition 新建编辑/分类/删除/导入/导出 | U/C：CSV 完整解析后写；捕获账号/lookup、失活与晚 picker/读结果保护、防重；默认 XLSX/显式 CSV；FUX-09 |
| basedata-bom | Bom 成品/用料/数量与损耗/编辑/删除 | U/C：十进制与单位/超限保留旧用料，不隐式 Number 舍入 |
| basedata-unit | BaseData 单位/筛选/删除/列配置/导入导出 | U/C：CSV 完整校验、读错误重试、账号/栏目/picker/批写 scope；属于基础资料85项共享集，不另加数；默认 XLSX+明确备份 |
| basedata-defect | BaseData 不良项/新增编辑/删除/导入导出 | S、U/C：业务编号与原文保留；同85项集验证晚 picker/读/写/确认及栏目失效；默认 XLSX+明确备份 |
| basedata-process | BaseData 工序/权限多选/不良项/采集数据/SOP/打印/导入导出 | FUX-06：UI/业务名分离、机器权限不译；同85项集验证 scope、晚 picker 与原文；既有 HTML 转义另保留 |
| basedata-route | BaseData 路线步骤/顺序/新建编辑/导入导出 | U/C：步骤类型/顺序、process lookup 快照、外部确认回调失效；同85项集；默认 XLSX保留业务名、原始CSV备份不改 |
| basedata-warehouse | Warehouse 新建编辑/查询/删除/分页 | U/C：错误重试、末页回退、保存草稿/防重 |
| basedata-supplier | Supplier 新建编辑/查询/删除/分页 | 同共享业务生命周期但独立数据 API，名称/地址为文本 |
| basedata-customer | Customer 新建编辑/查询/删除/分页 | 同上；App token-key 析构缓存是实际会话边界，不误报已跨登录泄露 |
| custom-product | CustomReceiptFields scope=product | U/C：类型/必填、选项、保存/删除失败、类型结构 |
| custom-bom | 同组件 scope=bom | 独立 scope，不混写其它业务字段；接口授权以服务器为准 |
| custom-workorder | CustomWorkOrderFields | U/C：保存/删除重试、必填/类型、未知引用型关系仍不称实现 |
| custom-reporting | 通用字段 scope=reporting | U/C：类型/日期/选项/错误，不把语言选项改成 API 业务值 |
| custom-process | 通用字段 scope=process | 同通用字段契约；未对无字段写入入口强加“已支持”声明 |
| custom-customer | 通用字段 scope=customer | S、U/C：作用域与删除确认/错误 |
| custom-receipt | 通用字段 scope=receipt | U/C：与真实出入库单必填校验联合，失败留值 |
| custom-receipt_detail | 通用字段 scope=receipt_detail | U/C：单头/明细区分；不冒称引用关系查找 |
| sysconfig-department | Department 查询/分页/成员编辑/删除 | 既有成员/草稿/会话回归保留；本轮新增3项键盘基线失败→修后通过，验证实际 role/title、入焦、双向循环、两层Escape与回焦；FUX-05 |
| sysconfig-user | UserManagement 新建/编辑/权限/状态/解绑/密码/导入/日志/导出/打印 | U/C：防重/会话、FSEC-01；默认 XLSX显式请求无哈希全量业务列，敏感CSV备份仍含hash；接口授权见独立HTTP27子集 |
| sysconfig-factory | FactorySettings 名称/状态/保存 | U/C：失败不假称工厂停用，保存防重/草稿与旧会话响应 |
| sysconfig-plugin-center | PluginCenter 注册/安装/版本/健康/配置/卸载 | U/C、旧 B/H 33：新包必须复验；业务版本路由≠二进制升级 |
| sysconfig-external-data-sources | ExternalDataSource 新建编辑/凭据/测试/启停/归档/schema/日志 | U/C：KEEP/REPLACE/CLEAR、凭据来源变化、失败保留与可重试；真实企业凭据未验 |
| monitor-online | OnlineUsers 查询/分页/强退 | FUX-02/03：最新查询/正确页、失败重试、强退锁与确认上下文 |
| monitor-cache | CacheMonitor 指标/图例/状态/刷新 | FUX-02：失败不伪造 0B/0%；标签/图例/非颜色状态 |
| monitor-cacheList | CacheList 名称/键/内容/恢复/清理 | FUX-01/02/03：键/内容身份、原生选择、错误/重试与危险动作锁 |

补充：Login/绑定与微信/手机号分支、超级管理员授权码、原生 analytics iframe、插件 iframe、日志弹窗、工序卡片设置、打印/Excel模板仍单独列示。原生报表平台 gate/离开守卫及四类打印 serializer 已维护；不能因40条路线存在、3项部门键盘或某个文件回读通过，就称所有内部弹层/手机实机通过。

## 4. XSS/URL/权限检查与排除的误报

- Vue插值与日志v-text保持纯文本，文件名/错误里的标签形状只显示文字，没有新增img/script节点。UiIcon的v-html仅取开发者静态SVG字典，不把API HTML送进去。
- BaseData/User打印HTML入口逐项转义，既有姓名/备注回归保留原文且无脚本节点；不能因document.write存在就报已利用XSS。物理打印机/所有字体未验。
- 插件iframe保持不同源HTTP(S)、明确origin/source、当前epoch/token/version、body/并发限制；上下文不含会话token或私有配置。第三方发布者、CSP和部署策略另验。
- UReport有意同源且有原生脚本/保存API；没有盲加sandbox。平台门禁与未保存修改守卫已维护，不代表第三方报表引擎的SQL/XML/bean等底层能力已安全验收。
- App在3662行按token重建KeepAlive，基础资料按栏目key失活；组件同实例换token实验不自动等于主App生产跨租户链。先前“共享客户composable跨登录缓存泄漏”缺少实际链路，不作High漏洞。
- PluginRuntime的root.dark scoped候选已由Vue compileStyle排除，未无证据换palette。普通Bearer API不能仅因没见CSRF token就报cookie CSRF；报表cookie/同源/部署路径仍单独审。

## 5. 边界与仍待实测范围

1. 报告只覆盖管理端已列维护与证据；不保证40个工作区所有关联ID、批量/删除/附件路径、所有嵌套弹层或并发组合都已无漏洞。
2. JS可读bearer/localStorage是架构风险面，未找到完整XSS窃取链。迁移HttpOnly需要同时协调服务器、CSRF、微信/扫码和刷新恢复，不只改UI存储。
3. 生产CSP/Trusted Types、TLS、可信代理链、真实Redis恢复数据、第三方插件/引擎与供应链须另验；前端官方audit0不能替代移动端或后端依赖审计。
4. 390px长姓名部门、日志内滚动、深浅主题、reduced-motion、键盘和模拟503/退出防重已由 B/F 28/28覆盖；B/H 58/58覆盖40工作区、实际单位CRUD、双语言/主题/时区/窄屏登录，另有HTTP27和原生报表15项。320px、辅助技术、所有内层弹窗与生产数据量组合不因此自动通过。
5. 微信真机、摄像头、真实短信/公众号/企业外部数据源、生产容量/负载/物理打印/备份恢复未验。本地夹具凭据不是生产凭据，不能将可打开网页或UP视为这些能力已验。

## 6. 验证台账（不同层次与重叠子集不求和）

| 验证 | 已确认结果 | 证据及口径 |
| --- | --- | --- |
| 原监控/日志/corner失败回归 | 监控9失败→修后9通过；日志5失败/1已安全→6通过；corner5失败/2通过 | [监控基线][E-MON-BEFORE]、[日志基线][E-LOG-BEFORE]、[corner基线][E-CORNER-BEFORE]；保留历史失败，不把首轮写为成功 |
| 旧扩展定向回归 | 7文件54项通过 | [旧扩展日志][E-EXPANDED]；含轮询/打印/日期/权限旧子集，不额外加到全量 |
| 基础资料账号/栏目/picker/导入/确认 | **85/85** | [最终日志][E-BASE-FINAL]；晚picker/同实例菜单12项先失败见[基线][E-PICKER-BEFORE]；有效CSV原文/数值规则不改 |
| 默认/合并XLSX纯工具编码 | **36项通过** | [纯工具测试][T-XLSX]；含真实writeBuffer→load及校验/拒绝断言，不等于36次真实Excel交互 |
| 工单真实打印serializer | **8项通过** | [打印测试][T-PRINT-ENC]；4模板回读和4拒绝用例，不把内存cell或假writeBuffer当文件验收 |
| 部门真实dialog键盘补充 | **新增3项，3失败→3通过** | [基线][E-DEPT-BEFORE]与[修后39项合并集][E-DEPT-AFTER]；包含旧回归，新增3项不重复计数 |
| 实际隔离HTTP/H2 | **27/27，0失败** | [HTTP结果][E-HTTP]；覆盖角色、用户两种导出、退出、平台报表cookie/真实HTML与运维分页边界；是另一个子集，不代表全部控制器/照片/Redis矩阵 |
| App类型检查与正式npm build | **通过** | [旧类型错误][E-BUILD-BEFORE]、[最终类型检查][E-TYPE]、[正式build][E-BUILD]；仍检查App/旧模板，没有禁测/跳过类型 |
| 官方frontend npm audit | **总0：含dev与prod** | [官方JSON][E-AUDIT]；仅此锁文件/公告时点，不作“供应链永远安全”保证 |
| 最新完整npm test | **65文件、782/782，0失败/跳过** | [验收计数/日志SHA][E-FULL]；Node UI16/16、插件UI42/42、国际化83/83分别通过；调度单worker threads适配共享主机，不改变断言/类型检查 |
| 最新生产包浏览器：状态/故障/交互 | **B/F 28/28，0 pageerror / 外部请求** | [专项结果][E-BROWSER-UI]；使用最终生产dist与明确模拟API/故障，不称为后端授权验证；下载XLSX另做实际回读/ZIP5项检查 [E-BROWSER-XLSX] |
| 最新生产包浏览器：40工作区逐页 | **B/H 58/58、40工作区、0 pageerror / 非注入5xx** | [真实API浏览器][E-BROWSER-REAL]；真实H2/Servlet、单位CRUD和语言/主题/时区/窄屏登录；路线存在不等于所有内部弹层完成 |

补充：[原生报表实测][E-NATIVE] **15/15**：真实平台登录、原生单元格属性编辑、导航/关页/刷新/退出的逐个真实confirm取消及原文/iframe保留，接受后离页与UI退出后cookie立即401。没有保存或删除模板，不等于全部原生排版/数据源/打印功能通过。初版CLI对话框与Windows参数问题已改为显式分步CLI和UTF-8源文件；未用模拟DOM冒称原生编辑。

## 7. 仓库来源与公开证据索引

[S-APP]: src/App.vue
[S-GUARD]: src/utils/workspaceLeaveGuards.ts
[S-UREPORT]: src/components/UReportPage.vue
[S-REPORT-LOCALE]: src/utils/reportLocale.ts
[S-SCOPE]: src/composables/usePageOperationScope.ts
[S-WO]: src/components/WorkOrderPage.vue
[S-PRODUCT]: src/components/ProductDefinitionPage.vue
[S-BASEDATA]: src/components/BaseDataPage.vue
[S-XLSX]: src/utils/spreadsheet.ts
[S-USER]: src/components/UserManagementPage.vue
[S-API]: src/api.ts
[S-DEPT]: src/components/DepartmentManagementPage.vue
[B-USER]: ../backend/src/main/java/com/heihu/workorder/controller/UserController.java
[B-UREPORT]: ../backend/src/main/java/com/heihu/workorder/report/UReportController.java
[B-UREPORT-FILTER]: ../backend/src/main/java/com/heihu/workorder/report/UReportAuthFilter.java
[T-APP]: src/__tests__/AppSession.test.ts
[T-BASE]: src/components/__tests__/BaseDataImportSessionSafety.test.ts
[T-XLSX]: src/__tests__/XlsxCellEncoding.test.ts
[T-PRINT-ENC]: src/components/__tests__/WorkOrderPrintEncoding.test.ts
[T-DEPT]: src/components/__tests__/DepartmentDialogKeyboard.test.ts
[T-EXPORT]: src/components/__tests__/DefaultSpreadsheetExports.test.ts
[T-CORNER]: src/components/__tests__/PageCornerUi.test.ts
[T-MON]: src/components/__tests__/MonitorSafetyUi.test.ts
[T-LOG]: src/components/__tests__/SystemLogsSafetyUi.test.ts
[E-MON-BEFORE]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-LOG-BEFORE]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-CORNER-BEFORE]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-EXPANDED]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-BASE-FINAL]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-PICKER-BEFORE]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-DEPT-BEFORE]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-DEPT-AFTER]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-HTTP]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-BUILD]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-BUILD-BEFORE]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-TYPE]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-AUDIT]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-VIRTUAL-BEFORE]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-ERASE]: ../docs/evidence/security-ui-baseline-2026-11-25.json

[E-FULL]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-BROWSER-UI]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-BROWSER-REAL]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-BROWSER-XLSX]: ../docs/evidence/security-ui-baseline-2026-11-25.json
[E-NATIVE]: ../docs/evidence/security-ui-baseline-2026-11-25.json

本轮临时浏览器与模拟/真实H2夹具已关闭，结果文件保留；原生报表分步CLI入口：[check-ureport-browser.ps1](../tools/functional/check-ureport-browser.ps1)。

公开日志/截图入口提供[脱敏计数与原始文件 SHA256](../docs/evidence/security-ui-baseline-2026-11-25.json)，不是完整运行日志下载；原始产物仅在本地保留。
