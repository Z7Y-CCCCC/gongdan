# 插件平台底座

本项目采用“核心系统 + 插件清单 + 独立服务”的渐进式扩展方案。核心系统管理目录、
版本和工厂绑定，客户专属业务部署为独立进程，不把第三方代码加载到核心 JVM 中。

## 当前已落地

- `plugin_catalog`：插件目录、运行模式、核心兼容范围和清单。
- `plugin_versions`：可上线版本、镜像、制品、校验值和发布说明。
- `factory_plugins`：工厂级安装、启停、版本、灰度、JSON 配置和健康状态。
- `POST /api/platform/plugins/{pluginKey}/install`：安装到当前工厂，初始为停用状态。
- `DELETE /api/platform/plugins/{pluginKey}/install`：卸载并清除当前工厂的插件配置。
- `POST /api/platform/plugins/{pluginKey}/enable`：启用指定 READY 版本。
- `POST /api/platform/plugins/{pluginKey}/disable`：只下线当前工厂的插件。
- `POST /api/platform/plugins/{pluginKey}/rollback`：切回最近一个更旧的 READY 版本。
- `POST /api/platform/plugins/{pluginKey}/health/refresh`：刷新独立服务健康状态。
- `GET /api/platform/plugins/runtime-manifest`：返回当前工厂和角色可见的动态入口。

管理入口是“系统配置 > 插件中心”。目录和版本登记仅限超级管理员；安装、启停、切换、回滚、
配置和卸载均写入系统操作日志的 `PLUGIN` 模块。

## 插件清单

```json
{
  "frontendEntry": "http://127.0.0.1:9102/",
  "backendUrl": "http://127.0.0.1:9102",
  "healthUrl": "/actuator/health",
  "menus": [
    { "label": "设备点检", "icon": "clipboard", "path": "/" }
  ],
  "requiredRoles": ["ADMIN", "PRODUCTION"]
}
```

核心前端读取运行清单，在“插件扩展”菜单中显示入口。独立页面通过 `iframe sandbox`
加载；页面白屏或服务不可用时不会让核心工作台一起白屏。运行清单只返回当前角色允许、
已经启用并且健康状态为 `UP` 的独立插件。

清单中的 `requiredRoles` 控制入口和代理访问的角色范围；`permissions` 使用稳定的
`模块:动作` 标识登记插件所需权限，核心会校验格式和重复项，后续接入细粒度授权时无需
修改插件接口。`configSchema` 支持 `string`、`integer`、`number`、`boolean`、`object`、
`array` 类型，以及数值范围、字符串长度、数组长度、枚举和必填字段；工厂配置保存时会
拒绝未声明字段和不符合规则的值。

## 安全调用边界

远程插件接口统一使用 `/api/plugins/{pluginKey}/...`。核心后端会重新校验工厂、角色、
启用版本和灰度比例。每条代理线路有独立超时和熔断状态，单个插件失败不会拖垮核心接口。

核心登录令牌不会传给 iframe。插件页面只能向工作台发送受约束的消息请求；工作台会
校验消息来源、插件来源和插件键，再代表当前登录人调用核心代理。核心到插件服务的请求还会
使用时间窗、请求体摘要和 HMAC 网关签名，插件服务必须验证签名。允许的方法仅为
`GET`、`POST`、`PUT`、`PATCH` 和 `DELETE`，路径必须位于
`/api/plugin/{pluginKey}` 下，请求体上限与后端代理上限均为 1 MiB。

代理会剥离登录凭据、Cookie、来源头、转发头和插件伪造头，然后注入可信的工厂、用户、
角色、版本和网关签名。配置不再通过运行清单暴露给浏览器；插件如需配置，应由核心在服务端
通过受控的代理请求提供。插件响应中的 Cookie、重定向和跨域授权头不会透传。

生产环境必须显式设置：

```dotenv
APP_PLUGIN_ALLOWED_BACKEND_HOSTS=quality-plugin,inspection-plugin
APP_PLUGIN_ALLOWED_FRONTEND_ORIGINS=https://workorder.example.com
APP_PLUGIN_PROXY_MAX_REQUEST_BYTES=1048576
APP_PLUGIN_GATEWAY_SECRET=use-a-random-secret-of-at-least-32-characters
```

`APP_PLUGIN_GATEWAY_SECRET` 必须至少 32 个字符。Compose 中插件端口默认只绑定到
`127.0.0.1`，生产环境不要把插件服务端口直接暴露到公网；插件服务必须使用同一密钥验证
HTTP 方法、转发后的插件路径、插件版本、工厂、用户、角色、时间戳和请求体摘要。

主机和来源使用精确白名单；不接受通配符、内嵌凭据、查询串、片段、路径穿越或健康检查
重定向。插件健康状态为 `DOWN` 时从运行清单移除，代理快速返回 `503`。

## 发布和回滚

登记版本目前只登记制品元数据，不负责构建或启动镜像。部署平台应先启动新版本，健康
检查通过后再切换，旧版本保留一段时间。回滚只允许切换到更旧的 READY 版本。

插件数据库必须使用独立 Schema 和 Expand-Contract 迁移。插件不能修改核心表或依赖
跨库外键。核心事务和可选插件应通过 Outbox 事件解耦，不能同步等待插件完成。

## 独立插件样板

`plugins/quality-management` 是既有质量管理演示插件。`plugins/equipment-inspection` 是
本轮新增的设备点检验收插件，覆盖更严格的边界：没有可信网关上下文时业务 API 返回
`403`；记录按工厂隔离；页面通过安全消息桥调用核心代理；工厂配置会显示在页面中。

设备点检插件本地启动：

```powershell
cd plugins\equipment-inspection
mvn spring-boot:run
```

两个样板也可以通过 Compose 一起启动：

```bash
docker compose --profile plugins up --build -d
```

清单见 `plugins/equipment-inspection/plugin-manifest.json`。本地使用
`127.0.0.1:9102`；Compose 中后端地址改为 `http://inspection-plugin:9102`。两个样板
都使用内存记录，进程重启后数据会清空，只用于平台验收。

## 当前能力边界

当前“安装”是把已经登记、已经部署的插件绑定到工厂，不是从远程商店下载和执行代码。
`artifactUrl`、`imageRef` 和 `checksum` 仍是发布元数据；核心尚未负责拉取制品、验证签名、
扫描漏洞、启动容器或执行数据库迁移。因此当前已经能验证目录到运行、停用、故障隐藏和
卸载的完整业务链路，但还不能宣称具备商业插件商店的供应链安全能力。

## 下一阶段

1. 建立签名制品仓库，下载后验证 SHA-256 与发布者签名，并接入 SBOM 和漏洞门禁。
2. 增加镜像版本发布、资源限额、健康检查、旧版本保留和失败自动回滚。
3. 用 Outbox 事件可靠推送工单、报工和库存变化。
4. 为生产插件补独立数据库、迁移、细粒度权限、限流和审计边界。
5. 增加兼容性矩阵、升级前备份和灾难恢复演练。
