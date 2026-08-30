# 设备点检测试插件

这个插件用于验收插件平台，不是生产设备管理模块。它验证：

- 独立进程和健康检查；
- iframe 安全消息桥，不接收核心登录令牌；
- 核心网关注入工厂、用户、角色和工厂级 JSON 配置；
- 工厂数据隔离；
- 插件接口禁止绕过核心网关直连；
- 插件停用或卸载后接口和菜单立即失效。

本地启动：

```powershell
cd plugins\equipment-inspection
mvn spring-boot:run
```

Compose 启动：

```bash
docker compose --profile plugins up --build -d inspection-plugin
```

本地清单使用 `http://127.0.0.1:9102`。Compose 环境登记版本时，浏览器入口仍使用
`http://127.0.0.1:9102/`，后端地址改为 `http://inspection-plugin:9102`。
