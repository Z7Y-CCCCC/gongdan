# 后端功能验收与新增配置

## CI 补充：库存时间映射（2026-11-24）

首个第二轮 CI 实际执行了 **916** 项：MySQL 迁移 **3/3** 通过，但库存精确时间边界回归返回 0 条而非应有的 3 条。用未改源码的单测和 `-DargLine=-Duser.timezone=UTC` 在本机复现，不能用本机 GMT+8 的成功掩盖它。

根因是 Hibernate 5 的 JPA 写入使用 JDBC `Timestamp`，库存原生查询却混用无时区的 JDBC `LocalDateTime` 绑定/读取；驱动会话时区与 JVM 默认时区不同或启动中变更时，两者不等价。现在过滤参数、读取均与 JPA 的 `Timestamp` 映射一致，空时间仍返回 `null`，库存缓存键升级 `page-v3`，不读取旧错误 DTO。没有改 CI 时区配置、关闭测试或降低原断言。

新增 **4 项**：UTC / +08:00 / America/New_York 的独立 H2 会话 3 项，以及真实 JPA 写入后改变 JVM 默认时区并恢复它的 1 项。最新完整 **UTC JVM `clean package`：918 项，0 失败、0 错误、1 本机 MySQL opt-in 跳过**；87 份 XML 汇总一致，新增 4 项包含在 918 内。日志为 `output/functional-ci-backend-utc-final.log`；最新 JAR 已核对库存修改类。

最新隔离 HTTP 另验证 **6/6**：两条入库与一条出库实际写入，精确 start=end 返回 3 条、两页读回原 12 点及真实单位、范围移后一秒返回 0、真实库存仍为 17。证据为 `../output/functional-inventory-http-final.json`。新提交的远端 CI / 启用 MySQL 结果在交付回复中单列；不是生产数据库验收。

## 第二轮补充修复（2026-11-24）

- 普通附件现在只保存可用于 HTTP 响应的具体 MIME 类型；格式错误、通配符和超长类型回退为 `application/octet-stream`。历史记录中的坏 MIME 也可以正常下载，不必重传文件。经签名检查的图片在客户端未提供 MIME 时仍保存正确的 `image/png` 等类型。
- 普通附件、管理端报工图片、移动端报工图片的下载头使用 UTF-8 `Content-Disposition` 编码，中文及 emoji 文件名不再被真实 Tomcat 丢弃。图片入口同时设置 `nosniff`；普通文件仍强制下载，不改为内联 HTML。
- 普通附件的元数据保存失败或后续事务回滚时，删除本次已经写入的文件，避免残留没有数据库记录的孤儿附件。两条新增回归使用真实临时目录及文件，不只验证 mock 的删除调用。
- 两类图片上传均循环读取签名头，避免合法输入流短读时被误判为损坏图片；仍保留原有后缀、MIME 与签名检查。
- `application-prod.properties` 单文件 multipart 默认也由 10MB 改为 20MB，与普通附件业务限制一致；总请求默认仍为 90MB，显式环境变量覆盖仍生效。报工图片自身的 10MB 限制不放宽。
- 单位改名补齐 `sales.orders.list` 与 `boms.list` 缓存清除，刷新后销售明细、BOM 成品及子件单位立即反映新名称，不保留旧缓存。

真实 HTTP 验证由 `FunctionalUploadHttpIntegrationTest` 执行，使用 loopback Tomcat、独立 H2 与自有临时上传目录。测试读取生产文件中的 **multipart 默认值**，并实际完成普通 12MB 附件上传/下载及拒绝 12MB 报工图片；没有启用完整生产 profile、真实微信或生产数据库。其缓存回归实际启用 `ConcurrentMapCacheManager`、确认两个列表已缓存，再修改单位并重新发出 HTTP 查询。图片字节用于签名与传输核对，不声称完成全部图片格式解码认证。

修复前证据分别位于 `output/functional-attachment-before-round2.log`（坏 MIME、中文下载头、缺省图片 MIME、短读失败）、`output/functional-attachment-rollback-before-round2.log`（两个真实孤儿文件失败）和 `output/functional-unit-cache-before-round2.log`（改名后销售单位仍返回旧值）。这些故障不是主动注入的预期成功检查。

第二轮初版 `mvn -B clean package` 成功（2026-11-24 16:33:48 +08:00）：**914 项、0 失败、0 错误、1 跳过**；86 份 Surefire XML 重新汇总一致。初版新增 21 项包含在 914 内，不额外累计。此快照包含 MIME 工具、下载控制器、单位缓存修复及生产配置；日志为 `output/functional-backend-round2.log`。后续 CI 时间映射修复及最新完整统计见上文。

| 第二轮专项 | 最新结果 / 口径 |
| --- | --- |
| 真实 Servlet 上传、下载、Unicode 文件名、MIME、生产 multipart 默认与单位缓存 | `FunctionalUploadHttpIntegrationTest` **9/9**；真实网络与 H2，不替代生产验收 |
| 普通附件服务、短读及真实临时文件回滚 | `CustomFieldAttachmentServiceTest` **14/14**；服务单元回归，含实际本机文件读写 |
| 报工服务及图片签名短读 | `WorkOrderReportServiceTest` **20/20**；保留原有报工与图片边界回归 |
| 普通/生产 multipart 默认及显式覆盖 | `MultipartConfigurationTest` **3/3**；配置回归，不能单独证明部署成功 |
| 依赖视图缓存清除契约 | `InventoryCacheInvalidationTest` **2/2**；另有上面的真实 HTTP 缓存验证 |

## 本轮修复

- 内部沟通联系人预览以前读取整个工厂最近 5,000 条消息，混入别人之间的私聊并产生错误未读数。现在按当前用户参与的会话取最后一条，单独聚合收到的未读数；第三方会话和超过 5,000 条干扰不会覆盖自己的记录。
- SOP、质检、设备、工资规则与现场事件使用的扩展记录不再静默截断 JSON。标题、业务编号、状态、内容分别检查 128/128/32/20,000 字符限制；非对象、损坏或尾随垃圾 JSON 明确拒绝，失败不修改原记录。
- 工单与出入库的自定义字段补齐服务器必填、数值/日期/选项和基本类型检查。新单据必须满足规则，失败不修改库存。编辑工单允许未改变的历史缺失/旧值，不允许把已有必填值清空；没有给没有自定义值入口的产品请求强加字段。
- 单文件 Servlet 限制由 10MB 改为 20MB，与普通附件 20MB 的功能一致。报工图片仍受自身服务的更小限制。普通附件以下载方式返回并设置 `nosniff`；经签名检查的图片仍可内联预览。
- 工单响应补齐真实 `unitName`（取 `product.unit.name`），管理端、移动端列表/筛选/详情/编号查询及看板最近工单共用同一契约。历史产品或单位缺失返回 `null`，不伪报“个”。分页与最近工单预加载单位避免 N+1；单位改名清理工单缓存，并版本化缓存键避免读取旧 DTO。
- 发送测试不再写入 `SIMULATED_OK`。新增真实、受控的消息投递接口。

## 消息测试接口

`POST /api/advanced-records/{id}/send-test` 需要管理员，记录必须属于当前工厂且模块为 `MESSAGE`。

返回 `success`、`message`、`httpStatus` 和更新后的 `record`：HTTP 200 代表投递测试完成，必须检查 `success` 才能判断是否被目标服务接受。真实失败存为 `FAILED`，配置保留；缺失/不允许的配置返回 400。成功代表服务接受请求，不代表终端已读。

| 渠道 | 配置与请求 |
| --- | --- |
| `webhook` | `payload.webhook`；JSON body 包含 `title`、`target`、`content`。 |
| `wechat_work` | `payload.webhook`；企微机器人 text 格式，正文不超过 2,048 UTF-8 字节；HTTP 2xx 之外还必须返回 `errcode=0`。 |
| `wechat_mp` | 记录包含 `appId`、`target`（openid）、`templateId`、`template`（JSON data 对象）。Access token 仅由服务器读取，AppId 必须匹配；响应必须 `errcode=0`。 |

服务器设置示例（替换占位符，不应把真实 token 提交到 Git）：

```text
APP_MESSAGE_ALLOWED_TARGET_HOSTS=HOST,qyapi.weixin.qq.com,api.weixin.qq.com
APP_MESSAGE_ALLOW_HTTP=false
APP_MESSAGE_WECHAT_MP_APP_ID=APP_ID
APP_MESSAGE_WECHAT_MP_ACCESS_TOKEN=TOKEN
```

目标主机必须精确匹配白名单；默认只允许两个微信官方主机和 HTTPS。只在隔离本机接收器测试中显式允许 HTTP，不由前端自动放行。公众号 endpoint 默认是官方模板发送接口；配置的短期 access token 到期后需由运维轮换。

投递禁用自动重试与重定向，连接/读取等待有时限，并有 8 秒总截止；响应最多读取 32KiB。相同记录正在发送时返回 409，避免并发重复投递。不在日志/返回记录中写入服务器公众号 token。

## 自动验证证据

| 功能组 | 证据 |
| --- | --- |
| 登录、短信/手机绑定、授权码、用户/工厂隔离 | 原有认证、绑定、事务、并发与会话集成测试；使用测试配置，真实外部短信/微信不作为已实测声明。 |
| 单位、不良项、部门、工序、客户、供应商、仓库 | `FunctionalOperationsIntegrationTest` 对实际控制器执行创建、读回、修改、跨工厂拒绝、删除与读回验证，不仅检查路由存在。 |
| 产品、BOM、销售单、工单、报工审批、出入库、库存统计 | 原有业务完整性/精度/并发测试；本轮补工单历史值兼容、必填绕过和出入库失败不动库存的真实 API 回归。 |
| 工单计量单位 | 真实 API 验证 `kg` 在管理创建/列表、移动列表/关键词/可报工筛选、详情/编号查询和看板中一致；单位改名“千克”后读回。缺历史产品/单位返回 `null`。5 条不同产品/单位的分页（含总数）与筛选分别只有 2 个 SQL，看板仍为 4 个 SQL。 |
| SOP、设备、质检、消息配置、工资规则、现场事件 | 六个模块逐一保存、更新、读取实际 payload 与状态、删除；严格 JSON 与最大内容边界回归。这里不把配置 CRUD 说成独立生产执行引擎。 |
| 偏好/列设置 | 服务器保存并读回，同工厂不同用户不共享设置。 |
| 内部沟通 | 三人私聊隔离、已读切换、跨工厂拒绝、真实插入 5,001 条无关消息后的预览与未读验证。 |
| 附件 | `FunctionalUploadHttpIntegrationTest` 通过真正 Tomcat multipart 上传/下载 12MB，核对内容与下载头；不是 MockMvc 绕过 Servlet 限制的测试。文件仅写入该测试生成的 Temp 目录，并在测试后清理。 |
| 消息发送 | `MessageDeliveryIntegrationTest` 本机真实 HTTP 接收器验证三种请求、公众号服务端 token、下游拒绝、重定向不跟随、损坏/过大响应与并发防重；不发送真实微信或第三方地址。 |
| 外部数据源、插件、报表和所有核心 API 映射 | 既有适配器/策略/代理测试；路由冒烟扩至外部数据源及 UReport，191 个方法映射无 5xx。路由冒烟中的预期 4xx 不算业务成功。 |

第一轮 `mvn -B clean package` 成功（2026-11-24 14:36:08 +08:00）：893 项、0 失败、0 错误；1 个显式 opt-in 的本机 MySQL 迁移测试类未开启。第一轮新增功能集成 17 项、真实 Servlet 上传 1 项、本机消息投递 7 项均通过；191 个 API 方法映射冒烟无 5xx。单位契约专项先以 34 项回归验证通过。最新第二轮完整统计见上文，不与第一轮重复累计。

第一轮完整打包日志位于 `output/functional-backend-final.log`，最新第二轮日志为 `output/functional-backend-round2.log`。独立数据切片现在显式导入字段验证器，5 项切片回归也单独通过，不能通过禁用生产验证或删除回归测试获得通过。

真实微信终端、实际短信、企业 TLS/数据库凭据、生产容量及生产备份恢复仍需要部署环境和对应凭据。本轮测试使用私有 H2、临时文件与明确的 loopback 接收器，不操作生产业务库。
