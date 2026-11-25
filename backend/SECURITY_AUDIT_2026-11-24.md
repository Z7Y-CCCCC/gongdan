# 后端资源所有权与权限维护审阅（2026-11-24；2026-11-25 续验）

## 范围与证据口径

基线为 `6a707ad`。本次按最新维护范围审阅资源角色、工厂和本人边界，使用普通单元/隔离集成测试，不生成或执行自构造 SQL、XML、命令载荷，不连接外部目标，不读取真实凭据。普通集成测试仅对私有 H2 中的正常测试对象执行框架读写。

控制器清单共 **42 个**，另有保护 UReport servlet 的 `UReportAuthFilter`。下表是入口与已看到的代码边界，不等于每个参数、业务对象、并发组合或部署配置都已经通过安全验收。此前 CI 920 项成功是回归基线，不能据此断言没有权限问题。

2026-11-25 恢复后独立复核精确退出、当前权限、移动照片归属、默认业务列导出和可信代理解析。另确认 TLS 终止代理场景的报表 cookie `Secure` 配置尚未闭环：已按 root 确认引入显式部署开关，见 BSEC-005。继续发现并维护在线列表的极端分页整数溢出，见 BSEC-006。上一轮默认/UTC **994** 项和本日分页修复前 **1006** 项均是明确的历史源码快照；最终完整 UTC 结果在下表独立列示，不把早一快照当成包含随后补丁的结果。

**本日最终源码验收已完成**：`mvn -B '-DargLine=-Duser.timezone=UTC' clean package` 在 10:08:49 +08:00 成功，**1016 项、0 失败、0 错误、1 本机自然 MySQL opt-in 跳过（实际通过 1015）**；96 个新鲜 XML suite 汇总一致，新增 cookie 12 项和分页 10 项已包含。最终 23 个相关 class 和 application.properties，与可执行 JAR、root 当前隔离 API 的独立 classes 副本逐字节一致；这是明确范围内的回归与包一致性证明，不是生产安全认证。

## 1. 角色和身份来源

- `TokenService.requireUser`（`src/main/java/com/heihu/workorder/service/TokenService.java:186–222`，持久路径）先查询持久会话，拒绝过期/被踢会话，再读当前用户、工厂和角色；停用、删除、工厂不匹配会拒绝。授权不只使用登录时的角色快照。
- `requireAdmin`（同文件 `163–169`）要求当前角色 `ADMIN`。
- `requireSuperAdmin`（同文件 `155–161`）同时要求当前角色 `ADMIN` 和超级管理工厂代码 `ADMIN`；普通工厂管理员并不因为角色叫 `ADMIN` 就获得平台权限。
- 工厂/用户 ID 从验证后的会话传给控制器服务，不能以请求体中同名字段替换该身份。部分必需请求头缺失会先由 Spring 返回参数错误；拒绝状态并非所有入口都统一为 401。
- `FactoryUser.id` 是 `factory_users` 全表的 `@Id` / `IDENTITY` 主键（`model/FactoryUser.java:28–30`），不是每厂重新从 1 编号。`UserResponse`、`MobileAuthResponse`、`AuthResponse` 没有独立 `factoryId` 字段；因此移动端采用全表唯一 `user.id` 作为账户区分项时，不能仅因缺少 `factoryId` 就认定已发生跨租户草稿泄漏。
- 无数据库的 `new TokenService()` 是额外的内存构造路径，不能把该路径的行为等同于默认 Spring/JPA 部署的持久会话行为；自定义集成若使用它，需要单独验收权限更新和失效行为。

## 2. 资源边界台账

| 资源类别 / 控制器 | 当前入口角色 | 工厂 / 本人边界与维护注意项 |
| --- | --- | --- |
| 登录、退出、手机号登录、微信登录、初次绑定：`AuthController`、`PhoneAuthController`、`MobileAuthController`、`FactoryBindingController` | 公开认证入口，不预先要求有效 Bearer | 登录由密码、短信、微信 ticket 或工厂授权码服务决定后续身份；`select-factory` 会复查所选账户的凭证。退出只撤销明确提供的 Authorization token，匿名/过期/重复请求为幂等 204；不使用环境 cookie，不按 userId 踢出新登录，见 BSEC-004。初次工厂绑定使用授权码，不应当作无凭证普通 CRUD。短信客户端地址策略见 BSEC-002。 |
| 平台工厂、平台管理员、授权码：`SuperAdminController` | 18 个映射均要求超级管理员 | 跨工厂管理是该角色的设计能力；普通 `ADMIN` 不属于这个边界。授权码服务检查所属工厂、过期、消费状态，并有既有并发回归。 |
| 当前工厂和用户：`FactoryController`、`UserController` | `ADMIN` | 当前工厂从会话获取；用户更新/状态/角色传入操作者及目标用户，服务有本厂查找和自改限制。导入、导出也在管理员角色及当前工厂范围内。机器 CSV 的数据格式不能在未协调调用方时静默改变。 |
| 基础资料：`UnitController`、`DefectItemController`、`DepartmentController`、`ProcessController`、`ProcessRouteController`、`CustomerController`、`SupplierController`、`WarehouseController`、`ProductCategoryController`、`WorkOrderCategoryController` | `ADMIN` | 控制器传当前 `factoryId`；既有普通资料集成覆盖创建/读回/修改和另一私有工厂的修改拒绝。不能由此宣称每类删除、引用、批量和并发组合均已经验完。 |
| 产品、BOM、销售单、工单、出入库和库存：`ProductController`、`BomController`、`SalesOrderController`、`WorkOrderController`、`InboundReceiptController`、`OutboundReceiptController`、`InventoryDetailController` | `ADMIN` | 当前工厂查找和关联产品/单位归属由服务检查；库存写入还有锁/版本及数量完整性规则。库存列表按工厂查单据及关联产品，时间映射回归与权限回归是不同证据。 |
| SOP/设备/质检/消息/工资配置：`ExtensionRecordController` | `ADMIN` | 记录按当前工厂保存、更新、删除；模块允许集合限制在服务中。配置 CRUD 不等同于设备执行、工资结算等额外授权能力。 |
| 移动现场事件：`MobileFieldEventController` | `USER` | 列表是同厂共享；创建者从会话取，并强制模块为 `FIELD_EVENT`。当前并未声明为“只允许本人看本人事件”；收紧这一规则要先确认业务需求。 |
| 首页和移动看板：`HomeDashboardController`、`MobileDashboardController` | 管理端 `ADMIN`，移动端 `USER` | 移动工单汇总是同厂范围，报工个人指标使用当前用户。工厂汇总与个人报工范围不能混为一种权限语义。 |
| 工单/工序可报工人：`MobileWorkOrderController` | `USER` | 工单查找带当前工厂；可报工人和实际报工另检查工单状态及工序人员权限。能看到本厂工单不自动代表能报所有工序。 |
| 报工查询、审批：`WorkOrderReportController`、`MobileReportController` | 管理查询/审批 `ADMIN`；移动 `USER` | 管理员查询和审批为同厂范围；移动列表按本人 reporter，显式 `includeSubmitted=true` 时另包含本人代报。照片已按确认规则加入查看者、上传者及关联报告检查，见 BSEC-001。 |
| 自定义字段与普通附件：`CustomFieldController`、`CustomFieldAttachmentController` | `ADMIN` | 普通附件下载按当前工厂匹配附件；不是对全部员工开放的图片入口。已有文件大小、类型、下载头及本机回滚清理回归。 |
| 通知、私聊、偏好：`NotificationController`、`InternalChatController`、`UserPreferenceController` | `USER` | 服务接收当前身份；通知按接收用户，私聊按当前参与者及本厂联系人，偏好按当前工厂和用户。已有三人/两厂、无关消息和偏好隔离回归。 |
| 插件管理和代理：`PluginControlController`、`PluginProxyController` | 目录/版本写入超级管理员；安装配置 `ADMIN`；运行时/代理 `USER` | 控制器显式传当前工厂、用户及角色，目录写入不是普通厂管理员能力。代理是否允许具体插件/接口由服务再判定；表中没有替第三方插件提供安全认证。 |
| 会话和缓存运维：`SystemMonitorController` | `ADMIN` | 已逐入口看到当前 `factoryId` 传入列表、踢人、缓存内容、清除及恢复服务；不能误报为无工厂参数的全局管理员清除。仍需独立验证部署后真实 Redis 键布局。 |
| 导入及操作日志：`SystemLogController` | `ADMIN` | 读取和写入带当前工厂。业务导入日志由前端上报完成情况，不应把它描述成不可篡改的独立安全审计账本。 |
| 外部数据源：`ExternalSourceController` | `ADMIN` | ID 操作带当前工厂；`SourcePolicy` 用运维登记的工厂目标/地址/SQLite 文件 ID，不能把运维授权当成用户可任意指定出口。此次只静态维护，不执行外部连接或任意查询。 |
| 原生 UReport：`UReportController` 与 servlet filter | 平台超级管理员：角色 `ADMIN` 且工厂代码 `ADMIN` | 按 Ethan 确认的最小维护方案，`/api/ureport/session` 和整个 `/ureport` servlet 都用 `requireSuperAdmin`；普通私有工厂 ADMIN/PRODUCTION 拒绝。全局目录及旧模板保留，不声称已实现每厂原生模板隔离，见 BSEC-003。普通业务报表控制器未改为平台专用。 |

## 3. 需要明确规则的维护项

### BSEC-001：移动照片的个人关联授权已维护

**规则经 root 确认后已实施兼容维护；普通控制器单元回归先验证旧范围差异，再验证新规则，不宣称生产利用验收。**

- 基线 `6a707ad` 的移动控制器仅传 `factoryId` 和 `imageId`，服务只检查图片所属工厂。正常的同厂无关联员工测试在修复前没有收到应有的 404：`output/resource-photo-ownership-before.log`。这是普通测试对象的访问行为，不是 SQL/XML/命令载荷。
- 最新 `controller/MobileReportController.java:58–59` 验 `USER` 后同时传当前查看者 ID。
- 最新 `service/WorkOrderReportService.java:160–182` 在读取文件前检查同厂管理员、上传者及关联报告的 reporter/submitter；没有关联权限返回统一的“图片不存在”404。
- 对照 `repository/WorkOrderReportRepository.java:82–99`：移动报告列表还要求 `reporter.id = userId`，或显式开启代报查询时 `submitterId = userId`。
- 管理端 `controller/WorkOrderReportController.java:79–91` 则先要求 `ADMIN`，该同厂查看能力应保留。

保持路由、DTO 和管理端同厂查看能力不变；未关联报告的待上传图片仅上传者/同厂管理员可读。同厂管理员可读个人元数据缺失的历史图片，无法归属工厂的图片不放行；生产人员不能仅因历史 uploader/reporter 缺失而获得权限。

关联写入维持原有“只可使用本次提交者上传的未关联图片”规则；`service/WorkOrderReportService.java:353–369` 补齐 uploader 为空的拒绝分支，不让历史空元数据导致 500 或绕过。下载拒绝为 404；提交无权引用的图片沿用既有 400 校验契约。普通 HTTP 回归验证失败事务后报告数不增加、图片未被关联，且提交者仍不能下载它。

`ReportImageAuthorizationTest` **14/14** 覆盖两个私有工厂模型、上传者/关联人/管理员/无关联员工、跨厂员工与管理员、缺失个人元数据以及非法引用；repo/token 为 mock，文件为自有临时目录中的正常字节。另有 `FunctionalUploadHttpIntegrationTest.normalImageResourcesRespectPrivateFactoryAndReportOwnershipAcrossActualHttp` **1/1**，使用实际 loopback Tomcat、私有 H2、两个真实测试工厂及 6 个演员验证上传、404、合法关联后 reporter 读取和失败事务不挂图。没有把这些普通断言称为生产渗透测试。

### BSEC-002：客户端地址已统一到显式可信代理策略

**已维护解析和调用边界；生产可信代理部署仍未验收。**

- 基线短信入口和请求日志直接读 XFF 首项，旧 IP helper 还优先采用 CF/X-Real-IP 等请求头；这不是可信代理判定。
- `util/ClientIpResolver.java:20–54` 默认采用 socket peer，仅对 `app.network.trusted-proxies` 明确配置的 IP/CIDR 解析 XFF；从右向左跳过已登记代理，返回最近不受信客户端。没有不受信客户端、头无效或超过 4096 字符/64 hop 时回退到 socket 地址。
- 配置只接受 IP 字面量及有界 CIDR，拒绝主机名和 `/0`；只对已验证的数值 IP 调用 JDK 字面量解析，不请求主机名 DNS。默认不信任内网、Cloudflare 头、X-Real-IP 或任意 XFF 首项。
- 短信入口、登录元数据、会话元数据刷新和请求日志四处注入同一解析器；现有构造方法签名和 `IpAddressUtils.isInternalIp` 定位分类保持不变。无参数 `IpAddressUtils.getClientIp` 现在默认采用 socket；额外的显式配置重载供需要转发链的调用方使用。

短信手机号冷却、验证码和每 IP 计数逻辑未改变，只统一它们收到的可信地址。`ClientIpResolverTest` **21/21** 验证默认直连、显式代理、多 hop、IPv6、无效/过长头和无效配置；`ClientIpConsumerConsistencyTest` **1/1** 验证四处使用同一规则。短信服务是 mock，不发送短信；未将请求头处理测试称为实际生产限流绕过实验。

#### 显式部署配置与未知边界

配置为 `app.network.trusted-proxies`，环境变量为 `APP_NETWORK_TRUSTED_PROXIES`；默认空值。

```text
# Windows 本机直连，无反向代理：保持空值
APP_NETWORK_TRUSTED_PROXIES=

# Windows 仅经本机已知反向代理访问：明确登记其 socket 地址
APP_NETWORK_TRUSTED_PROXIES=127.0.0.1,::1

# Docker：HOST 只是占位，替换为实际反向代理固定 IP 或经过审阅的最小 CIDR
APP_NETWORK_TRUSTED_PROXIES=HOST
```

- 不要把 Docker 默认私网整体或所有内网自动当成可信代理；优先只登记代理容器的固定 IP，并限制谁能直接访问应用端口。
- Cloudflare → Nginx → 应用时，需由部署方核实真实链路并只登记实际代理地址/范围。使用有实际客户端及代理 hop 的可靠 XFF 链；不能因为请求带 `CF-Connecting-IP` 就无条件采用它，也不能相信客户端自带的左侧前缀。
- 如果生产只转发 CF/X-Real-IP、没有可靠 XFF，需先统一代理配置；本维护不会猜测这些头来源。
- loopback 信任是主机级假设，不等于认证某个本机进程。若部署另外启用容器/Servlet 的 RemoteIpValve 或 forward-headers-strategy，需要同样审阅它的信任配置，避免先把不可信头变成 `getRemoteAddr`。
- 本次没有验证生产防火墙、Docker 地址、Cloudflare/Nginx 链或真实公网地址；未知地址保持占位，不猜 CIDR。

### BSEC-003：原生 UReport 已限制为平台超级管理员，旧模板保留

**Ethan 明确选择原生设计器/全局模板仅平台超级管理员的方案；未选择租户模板迁移。**

- `report/UReportController.java:44` 用当前有效会话的 `requireSuperAdmin` 建立原生报表 cookie，普通厂管理员不再因为角色为 `ADMIN` 而获准。
- `report/UReportAuthFilter.java:27–37,46–52` 保护整个原生 servlet 映射，包含设计器、预览及资源路径，不只隐藏前端入口；已有 cookie 也重新验证平台超级管理员身份。无 cookie 为 401，普通工厂 ADMIN/PRODUCTION 为 403，平台工厂的 PRODUCTION 也不会因工厂代码而放行。
- 入口分类首先检查容器已解析的 `servletPath`，再按 context path 处理请求 URI 回退；正常 `/ureport;view=preview/...` 路径参数场景仍根据已解析 `/ureport` servlet 映射保护。不把 `/ureporting` 或 `/ureport-assets/...` 等未映射普通路径误归类为原生 servlet。
- `report/UReportConfiguration.java:39–44` 的 `/ureport/*` 注册、`src/main/resources/ureport.properties:4–5` 的 file provider 与统一 `./ureportfiles`、所有历史模板均未改变。没有删除、重命名、自动分配工厂或迁移旧文件。
- 普通工厂的 `WorkOrderReportController` 等业务报表保持原有角色与当前工厂边界；此次未把它们改为平台超级管理员能力。原生全局模板访问收紧是用户确认的产品规则，不宣称普通厂仍可访问原生全局预览。

维护没有把全局旧模板转换为每厂所有权体系，也没有额外限制或认证平台维护者的第三方引擎能力。普通测试只验证 cookie/角色/正常 servlet 映射/会话桥边界，不调用第三方 servlet 的模板、数据库、XML/SQL/bean 动作；不将门禁测试称为上述底层能力安全验收。

### BSEC-004：退出已串联精确服务端会话撤销

**按 root 最新确认的并发安全契约实施：撤销提供的 token，不从异步退出响应清除共享报表 cookie。**

- `controller/AuthController.java:31–39` 新增 `POST /api/auth/logout`，接收可选 Authorization；成功为无响应体的 **204**，`Cache-Control: no-store`。匿名、未知、过期或重复 token 不先经过 `requireUser`，避免无法退出过期会话。
- `service/AuthService.java:88–91` 委托可写事务 `TokenService.revoke`。`service/TokenService.java:138–153` 的持久路径只计算本次 token 的 SHA-256 并执行参数化的 `UserSessionRepository.deleteByTokenHash`；内存路径只删除本次 raw-token key。退出不查询或锁定用户、不调用 `kickActiveSessions(userId)`，不会撤销另一人或同一用户后续建立的会话。
- `repository/UserSessionRepository.java:18–21` 为精确 tokenHash 绑定删除；重复删除的 0 行结果是正常幂等结果。过期/被踢记录也可以直接删除，不要求该记录仍能认证。
- **响应不发送 `Set-Cookie`**。浏览器会自动处理响应的 cookie 写入，前端的 epoch 检查无法阻止一个迟到旧退出响应清掉期间建立的新报表 cookie；因此不声称异步 `Max-Age=0` 方案可以保证该并发边界。被撤销 token 仍留在旧 `/ureport` HttpOnly cookie 中时，filter 的服务端会话检查会返回 401；cookie 按已有期限自然到期，或被下一次合法 `/api/ureport/session` 建立的新 cookie 覆盖。
- 环境报表 cookie 不作为退出的认证来源；正常浏览器也不会把 `Path=/ureport` 的 cookie 发给 `/api/auth/logout`。客户端必须发送退出操作开始时捕获的 Authorization token；仅清除本地存储/返回登录页不能替代成功的服务端撤销。

`TokenLogoutTest` **7/7** 验证内存精确删除、迟到旧 token、新会话/另一人保留、过期/未知/匿名/重复及持久 hash 绑定和事务声明。`AuthLogoutIntegrationTest` **9/9** 使用私有 H2 和 MockMvc 验证 ADMIN/PRODUCTION、两私有工厂、过期/重复、cookie-only 匿名请求、旧报表 cookie 被 mock filter 拒绝，以及新报表 cookie 已建立后旧退出响应没有 Set-Cookie 并且新 token 仍可用。并发测试按明确响应顺序模拟迟到退出；不是生产浏览器/网络压力验收，未调用第三方 servlet 操作。

### BSEC-005 / Medium（部署条件性）：TLS 终止代理的报表 cookie Secure 开关已补齐

- 上轮 `UReportController` 仅检查 `request.isSecure()`。如果对外 HTTPS 在 Nginx/其他代理终止，再以 HTTP 连接应用，容器通常看到 false；仅发送 `X-Forwarded-Proto` 不等于应用应该无条件信任它。这里是明确部署条件下的凭证传输防护缺口，不把本地 HTTP 开发本身列为漏洞，也未验证生产网络实际暴露。
- `src/main/resources/application.properties:12` 加入 `app.ureport.cookie-secure=${APP_UREPORT_COOKIE_SECURE:false}`；`report/UReportController.java:31–35,44–51` 接收不可变配置，显式 true **或**直达 TLS 时才设置 `Secure`。旧单参数构造方法保持默认 false，当前平台超级管理员校验、HttpOnly、SameSite=Lax、Path=/ureport、有效期和 no-store 不变。
- 本地纯 HTTP 保持 `APP_UREPORT_COOKIE_SECURE=false`；对外 HTTPS、代理到应用 HTTP 的部署设置 true，并限制直接应用端口访问。该开关只决定 cookie 标记，不声明配置后已经启用 TLS，不让 `Forwarded` / `X-Forwarded-Proto` 控制信任，不替代 `APP_NETWORK_TRUSTED_PROXIES`。
- 原生会话已过期/撤销、未授权桥请求和退出均不新增 cookie 写入/删除；BSEC-004 的迟到退出不擦除新 cookie 契约保持。代码中的 cookie 写入路径仍只有 `/api/ureport/session`。
- `UReportCookieSecurityTest` 新增 **12 项**：默认 HTTP/HTTPS、显式开关与 TLS 终止、伪造 Forwarded/XFP、过期/撤销拒绝不写 cookie、两种策略下新 cookie 建立后的迟到旧退出。正常 Spring 属性上下文的基线 **12 项中 3 失败、0 错误**，记录于 `output/resource-cookie-security-baseline-20261125.log`；修后新 12 项及现有 UReport/退出合计 **47/47**，记录于 `output/resource-cookie-security-after-20261125.log`。这些是全量内的子集，不重复累计。首个测试上下文漏注册解析器的夹具错误已独立纠正，不拿该失败作为产品漏洞证据。
- Compose / 环境示例 / Windows 及 Docker 部署传递规则由 root 配套维护；本报告不以配置文档、MockMvc 或 cookie 字符串断言冒充真实 TLS 代理验收。

### BSEC-006 / Low：在线会话列表极端分页整数溢出已修

- `SystemMonitorController` 原将 `(safePage - 1) * safeSize` 与 `from + safeSize` 在 int 内计算后再限界。已通过普通本地已编译控制器探针确认 page=2147483647、size=200 令 from=-400；进一步用真实 MVC、控制器及 `GlobalExceptionHandler`、mock 私有身份/记录复现 **3 个合法 int 参数组合返回 500**，而不是正常空页。
- 这是经过 ADMIN 授权后的请求稳健性缺陷，不是越权读取、全厂数据泄漏、真实服务持续拒绝服务或内存压力实验。另一个 lazy logical list 用例只验证相邻 `to` 加法的整数极限（修前为 400），不分配数十亿个实际会话，也不宣称验证了如此容量。
- `controller/SystemMonitorController.java:59–61` 现在两个边界均先在 long 内计算、与实际 list.size() 限界，再转成 int；page>=1、size 1–200 的既有 clamp、ADMIN/current factory、筛选、total 和普通分页内容不变。邻近监控缓存/恢复接口没有相同分页乘法，不为消除搜索命中扩大权限或数据查询逻辑。
- `SystemMonitorPaginationTest` 新增 **10 项**：3 个极大 page/size、普通及越末页、2 个非正值、200 行 cap、上述 lazy-list 加法边界、2 个身份/角色拒绝。基线 **10 项中 4 失败、0 错误**，其中 3 个 500、1 个边界 400；证据为 `output/resource-monitor-pagination-baseline-20261125.log`。修后与最终完整源码结果见下表，10 项不额外加到全量统计外。

## 4. 验证台账

| 验证集 | 最新确认结果 | 证据与口径 |
| --- | --- | --- |
| 原有会话/角色/普通工厂对象回归定向集 | **308/308** | `output/resource-ownership-baseline-tests.log`；包含超管属性、会话失效、报工及普通资料隔离，互相重叠的旧定向集不另加到全量 |
| 照片个人归属与既有报工回归 | **34/34** | `output/resource-photo-ownership-after.log`；新增照片 14 项和既有报工 20 项，普通对象/mocked repo，不冒充完整数据库渗透验收 |
| 可信代理及现有 IP/日志/会话回归 | **37/37** | `output/resource-trusted-proxy-tests.log`；新增 21 个解析项及 1 个四消费者一致性项，其余为现有子集 |
| 实际 HTTP / H2 照片所有权与失败引用事务 | **1/1** | `output/resource-photo-ownership-http.log`；两个私有工厂/6 个演员，多个正常操作断言，不把断言数当成独立用例数 |
| UReport 原有 ADMIN 角色与 cookie/context 边界（收紧前） | **12/12** | `output/resource-logout-tests.log`；该旧快照不代表已验证新的平台专用规则 |
| 精确退出与旧会话/cookie 回归定向集（UReport 收紧前） | **40/40** | `output/resource-logout-tests.log`；退出 16 项、旧 UReport 12 项及旧会话/事务 12 项，0 failure/error/skip，2026-11-24 19:57:30 +08:00 |
| 已确认平台专用 UReport 门禁、退出与旧会话定向集 | **47/47** | `output/resource-logout-ureport-tests.log`；新版 UReport 19、退出 16 和旧会话/事务 12 项，0 failure/error/skip，2026-11-24 20:02:40 +08:00；旧 12 项门禁已更新规则，另增正常映射/平台角色用例，不另重复累计 |
| 退出改动前完整普通回归 | **971，0 失败/错误，1 跳过** | `output/resource-security-full-default.log`；2026-11-24 19:45:21 +08:00，跳过项是本机自然未启用的 MySQL opt-in，不是禁测；该构建不含随后新增的退出实现 |
| 上轮最终默认 JVM 完整 `clean package` | **994，0 失败/错误，1 跳过** | `output/resource-security-final-default.log`；2026-11-24 20:16:54 +08:00，新鲜 94 个 XML suite 汇总；自然跳过本机未启用的 `LocalMySqlMigrationIntegrationTest`。本代理新增 **72 项**（照片 15、代理 22、UReport 19、退出 16）与 root 导出 4 项已包含，不重复累计定向子集 |
| 上轮最终 UTC 测试 JVM 完整 `clean package` | **994，0 失败/错误，1 跳过** | `output/resource-security-final-utc.log`；2026-11-24 20:23:53 +08:00，相同冻结源码与 94 个新鲜 XML suite 汇总；94/94 suite 的 `user.timezone` 属性均为 UTC，跳过项仍仅本机自然未启用的 MySQL opt-in |
| 上轮默认/UTC 打包与独立 HTTP 快照字节一致性 | **22 个相关 class（含内部类）+ 1 个配置资源一致** | 两次均比较 `target/classes`、各自可执行 JAR 的 `BOOT-INF/classes` 和 API 自有临时 classes；一致资源为 `application.properties`。UTC clean 后快照 API 仍为 UP，并非仅确认 JAR 文件存在 |
| 2026-11-25 新 cookie 配置、原生 UReport 门禁与退出定向集 | **47/47，0 失败/错误/跳过** | `output/resource-cookie-security-after-20261125.log`；新 cookie 12、现有 UReport 19、退出 H2 集成 9、精确 token 单元 7；09:28:26 +08:00 |
| 2026-11-25 分页修复前默认 JVM 完整 `clean package` | **1006，0 失败/错误，1 跳过** | `output/resource-security-resume-final-default-20261125.log`；09:42:25 +08:00，95 个 XML suite 汇总一致；本次 cookie 12 项已经包含，**不含**随后 BSEC-006，跳过仍仅本机自然未启用的 MySQL opt-in，实际通过 1005 项 |
| 2026-11-25 分页修复前默认包与完整源码证明 | **22 个相关 class + 1 个配置资源一致** | `output/resource-security-resume-default-proof-20261125.json`；修改/新增主类（含内部类）与可执行 JAR 逐字节一致，95/95 suite 的 `user.timezone` 属性为 Asia/Shanghai；`src` 文件集合 SHA-256 与同源的下一行 UTC 快照一致，不代表分页补丁最终源码 |
| 2026-11-25 分页修复前 UTC 完整 `clean package` | **1006，0 失败/错误，1 跳过** | `output/resource-security-resume-final-utc-20261125.log`；09:52:58 +08:00，95/95 XML suite 的 user.timezone=UTC，源码与本日默认 1006 快照一致；`output/resource-security-resume-utc-before-pagination-proof-20261125.json` 保留对应证明；此包在停止指令到达前自然完成，不误记为中止或分页修后结果 |
| 2026-11-25 分页修复后 MVC 定向回归 | **10/10，0 失败/错误/跳过** | `output/resource-monitor-pagination-after-final-20261125.log`；精确 `-Dtest=SystemMonitorPaginationTest` 指令，普通页、身份与极值全部保留，`GlobalExceptionHandler` 中的原 500/400 恢复为应有的 200 空页/边界页；这是最终全量内的子集 |
| **2026-11-25 最终源码 UTC 完整 `clean package`** | **1016，0 失败/错误，1 跳过（通过 1015）** | `output/resource-security-final-source-utc-20261125.log`；10:08:49 +08:00，96 个新鲜 XML suite 汇总一致、96/96 user.timezone=UTC；cookie 12 与分页 10 均包含，跳过仍仅本机自然未启用的 MySQL opt-in |
| **最终源、可执行 JAR 与当前隔离 HTTP 副本** | **23 个相关 class + 1 个配置资源，三方逐字节一致** | `output/resource-security-final-source-utc-proof-20261125.json`；最终 `src` SHA-256=`194bb9a72073415c713ca61923617900270b60081879a73e5241d9d053bf7cd3`，与源冻结证明一致；JAR 为 `target/workorder-1.0.0.jar`，当前 API runtime 为 `../output/security-resume-api-final-runtime.json`，UTC clean 后健康仍 UP。UP/字节检查不另算业务测试，实际整合交互由 root 单列 |

日志属于本地忽略产物。此前 `FUNCTIONAL_VALIDATION.md` 中的业务回归、工厂隔离、会话更新和文件回滚证据继续保留，但不能把旧数字当成本次新增安全矩阵。

上轮最终默认构建首轮在 **994 项**中暴露 1 个 smoke 清单差异（无其他 failure/error）：新增退出路由后清单从 191 变为 192，`ApiRouteSmokeTest` 的显式常量尚未更新，见 `output/resource-security-final-default-attempt1.log`。已按本次有意新增的路由更新为 192，并让原生 UReport session 的 smoke 演员使用平台超级管理员；单独 smoke **1/1**、192 个映射且无 5xx（`output/resource-route-inventory-tests.log`，2026-11-24 20:11:32 +08:00）。没有禁用测试、放宽 5xx 断言或把失败的首轮称为成功；最新完整结果以本日 1016 项为准。

上轮默认最终包成功后，用现有 `tools/functional/start-api.ps1` 启动 root 请求的独立 loopback API（端口 64040，`test` profile、私有 H2、`FUNCTIONALTEST`、临时上传目录及 mock provider）。`output/security-api-runtime-round3.json` 为旧轮快照；本日 root 已重启到最终源码独立副本，runtime 为 `../output/security-resume-api-final-runtime.json`。后续 UTC clean 不删除该自有临时 classes 副本。启动/健康和字节校核不是额外业务测试项，不加到 994/1006/1016 中；真实浏览器/HTTP 整合结果由 root 单独验收，不以 UP 冒充所有权矩阵已完成。

UTC 台账指测试 JVM 以 `-Duser.timezone=UTC` 启动且 XML 属性已确认；现有 `WorkorderApplication.init/main` 仍按既有业务契约把应用的默认 `TimeZone` 设为 `Asia/Shanghai`，本次没有关闭这一初始化，也不把整套测试解释为所有应用业务在 UTC 默认时区下运行。

## 5. 尚未验收 / 不能据此下结论

- 没有逐一对全部 42 个控制器的所有 HTTP 方法、关联 ID、删除/批量/导入路径和并发组合执行完整 actor 矩阵。
- 没有执行 SQL/XML/命令载荷、第三方引擎能力利用、外部服务测试、真实短信或真实凭据检查。
- 未逐项匹配完整 Java 依赖树的公开安全公告；本地 JVM/Spring/数据库回归通过不等于第三方组件或 Java 依赖零漏洞。前端 npm 依赖检查若有结果，也不能代替这项后端工作。
- 未验生产代理可信链、Redis 实例/恢复数据、MySQL 运行账户权限、对象存储 ACL、备份恢复、容量与持续负载。
- 移动现场事件和工单当前是同厂共享读取；若产品要求本人/部门保密，需要另定业务规则，不能仅按“没有本人筛选”认定所有共享读取都是漏洞。
- 用户已选择默认安全 XLSX 仅业务列，完整原始 CSV 备份保留密码 hash；root 维护该导出契约及验证。`UserController.java:108–114` 及 `UserService.exportUsersCsv` 的旧机器 CSV 默认仍为 includePasswordHash=true，保持已有调用兼容；UI 安全导出必须显式请求 false（服务端隐私回归覆盖两种模式、角色和工厂边界）。不把缺省 raw API 说成已删除敏感列，不静默加/去 CSV 字符前缀，也不声称原始 CSV 已全部变成安全电子表格文件。
- 后端退出精确撤销及旧报表 cookie 拒绝已有普通回归，见 BSEC-004；前端/移动端的异常处理、重复点击和账号切换由对应代理验收。本报告不以返回登录页代替撤销成功证据，也不宣称实际跨浏览器/代理并发压力验证已完成。

结论只覆盖上述已看到的边界、确认后的兼容维护与明确的普通测试；不承诺所有潜在漏洞已清零。

公开的[基线验收计数与原始产物 SHA256](../docs/evidence/security-ui-baseline-2026-11-25.json)不含完整日志、cookie、数据库或运行配置；原始产物保留在本地。Java SCA 未逐项完成的范围声明保持不变。
