# 分析报表集成

## 方案

当前系统保留原有“生产报表”和“车间看板”，新增可选的“报表 > 分析报表”入口，使用 Apache Superset 的 Embedded SDK 展示一个经过工厂级行权限过滤的 Dashboard。

- 本系统负责登录、工厂切换和权限判断。
- 后端通过 Superset 服务账号换取 guest token，Superset 密钥和服务账号密码不下发浏览器。
- guest token 只授权配置的一个 Dashboard，并附带 `factory_id = 当前工厂 ID` 规则。
- Superset 是独立服务，建议连接专用的只读报表账号或只读视图；不让它使用核心应用写账号。
- 工单审批、报工和库存写入仍走本系统 API，不通过 Superset 操作。

Superset 的嵌入能力和 guest token 由官方 Embedded SDK 提供，guest token 支持 RLS；本集成使用的是 view-only guest embed。需要完整 Superset 自助查询、用户组权限或更细的行列权限时，应单独评估 SSO/商业嵌入能力。

## 配置

在 `.env` 或 Windows 部署配置中设置：

```dotenv
APP_ANALYTICS_SUPERSET_URL=https://analytics.example.com
APP_ANALYTICS_SUPERSET_USERNAME=workorder-embed
APP_ANALYTICS_SUPERSET_PASSWORD=replace-with-a-real-secret
APP_ANALYTICS_DASHBOARD_ID=the-dashboard-id
APP_ANALYTICS_TIMEOUT_SECONDS=5

# 这是前端镜像构建期的 CSP 白名单，不是运行时后端地址配置。
APP_ANALYTICS_ALLOWED_ORIGINS=https://analytics.example.com
```

只配置了部分值时，入口保持停用。修改 `APP_ANALYTICS_ALLOWED_ORIGINS` 后必须重建 frontend 镜像：

```sh
docker compose build frontend
docker compose up -d backend frontend
```

本地验证可以使用空配置启动；页面会显示“分析报表尚未启用”。

## Superset 数据准备

首次接入不要直接把全部业务表开放给分析服务。建议创建只读报表视图，所有 Dashboard 数据集统一包含 `factory_id` 字段，例如：

```sql
CREATE OR REPLACE VIEW reporting_work_order_output AS
SELECT
  wo.factory_id,
  wo.code AS work_order_code,
  wo.product_name,
  wo.status,
  wo.planned_quantity,
  wo.good_quantity,
  wo.defect_quantity,
  wo.created_at
FROM work_orders wo;
```

然后给 Superset 使用的数据库账号只授予这些视图的 `SELECT` 权限。嵌入 Dashboard 中的每个数据集都要能够安全应用 `factory_id = N` 规则；不要把没有该字段的全局数据集混入同一个带工厂 RLS 的 Dashboard。

推荐先制作一个最小 Dashboard：工单数、计划数、良品数、不良率、按产品产出、按人员产出和最近报工。确认两个工厂账号互相看不到数据后，再增加库存和外部数据源。

## 许可与边界

Apache Superset 主仓库采用 Apache-2.0；本项目通过独立服务和前端 SDK 对接，不把 Superset 源码并入后端。部署时仍要保留 Superset 及其依赖的许可证和 NOTICE 文件，并由发布方完成依赖许可证审查。

JimuReport 更偏中国式复杂报表、套打和打印，但其当前仓库 README 同时声明 GPL-3.0 与补充条款，并包含同类竞争限制；因此没有直接把它放进本项目。若后续重点变成送货单/工资单/套打，应先做法律和商业授权评估，再考虑将 UReport2 或其他 Apache-2.0 报表引擎作为单独模块。

## 安全检查清单

1. Superset 服务账号只授予 Dashboard 所需数据集的读取权限，并关闭不需要的 SQL Lab/下载能力。
2. Superset 的允许嵌入域名只填写本系统实际域名；不要使用空白允许列表或通配符。
3. `APP_ANALYTICS_SUPERSET_PASSWORD` 只存在于后端运行环境，不能写入 frontend build args、日志或浏览器存储。
4. Dashboard 的所有数据集完成跨工厂隔离验收后，才在生产配置中填写 `APP_ANALYTICS_DASHBOARD_ID`。
5. guest token 只通过 `/api/analytics/guest-token` 由当前管理员会话换取，不能做成公开 URL。
