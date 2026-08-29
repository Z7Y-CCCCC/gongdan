# 插件平台底座

本项目的插件扩展采用“核心系统 + 插件清单 + 独立服务”的渐进式方案。插件目录和工厂启停配置由核心系统管理，真正的客户专属业务可以部署为独立进程，避免把第三方代码加载到核心 Spring Boot JVM 中。

## 当前已落地

- `plugin_catalog`：插件目录、运行模式、兼容的核心版本和清单。
- `plugin_versions`：可上线版本、镜像地址、制品地址、校验值和发布说明。
- `factory_plugins`：工厂级启停、启用版本、灰度比例、JSON 配置和健康状态。
- `GET /api/platform/plugins/runtime-manifest`：返回当前工厂和当前角色可见的动态插件入口。
- `POST /api/platform/plugins/{pluginKey}/enable`：启用当前工厂的插件。
- `POST /api/platform/plugins/{pluginKey}/disable`：只下线当前工厂的插件。
- `POST /api/platform/plugins/{pluginKey}/rollback`：切回最近一个 READY 旧版本。
- `POST /api/platform/plugins/{pluginKey}/health/refresh`：检查内置插件或独立服务的健康状态。

管理端入口是“系统配置 > 插件中心”。插件版本只有 `READY` 状态才允许启用。所有启停、登记、版本切换和回滚操作会写入系统操作日志的 `PLUGIN` 模块。

## 插件清单示例

```json
{
  "frontendEntry": "http://127.0.0.1:9101/",
  "backendUrl": "http://127.0.0.1:9101",
  "healthUrl": "/actuator/health",
  "menus": [
    { "label": "质量管理", "icon": "activity", "path": "/" }
  ],
  "requiredRoles": ["ADMIN"]
}
```

启用后，核心前端会读取运行清单，在“插件扩展”菜单中显示入口。页面优先通过 iframe sandbox 加载，插件页面白屏或服务不可用时不会让核心工作台一起白屏。

## 发布和回滚规则

登记版本目前只登记插件制品元数据，不负责构建或启动 Docker 镜像。生产部署应由部署平台先启动新版本，健康检查通过后再把网关路由切换过去。旧版本保留一段时间后再清理。

插件数据库必须使用独立 Schema，并采用 Expand-Contract 迁移：先增加结构、兼容新旧代码，再延迟删除旧字段。插件不能直接修改核心表，也不能依赖跨库外键。

核心事务和可选插件之间使用事件解耦。报工审批、工单状态等核心事务不应该同步等待质量或绩效插件；后续接入 Outbox 后，由 Redis Streams 或消息队列可靠投递事件。

## 后续接入独立服务

1. 为首个质量管理插件建立独立 Spring Boot 服务和 Dockerfile。
2. 通过统一网关转发 `/api/plugins/quality-management/**`，网关注入租户和用户上下文。
3. 加入超时、限流、熔断、重试和每个插件的资源限制。
4. 增加镜像签名校验、部署记录、灰度租户和自动回滚阈值。

当前实现已经把生命周期控制面和前端挂载点固定下来；Docker 编排、网关代理和 Outbox 属于下一阶段的运行环境能力，不会伪装成仅靠单体进程就能提供的“完全隔离”。
