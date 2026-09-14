# 设备点检测试插件

这个插件用于验收插件平台，不是生产设备管理模块。它验证：

- 独立进程和健康检查；
- iframe 安全消息桥，不接收核心登录令牌；
- 核心网关注入工厂、用户、角色和工厂级 JSON 配置；
- 工厂数据隔离；
- 插件接口禁止绕过核心网关直连；
- 插件停用或卸载后接口和菜单立即失效。

核心服务与插件必须配置相同的非空网关密钥，并同步升级部署。HMAC-SHA256 签名原文依次为
HTTP 方法、原始路径及查询、插件标识、版本、工厂 ID、用户 ID、角色、时间戳、请求体 SHA-256、
`X-Plugin-Config-Base64` 原始值（未传时为空），字段用换行符连接。旧版缺少最后一行配置的签名
会被拒绝；工厂配置不再能在签名后被替换。鉴权匹配规范化的应用路径，矩阵参数不能绕过检查。
记录仍是内存中的演示数据，服务重启后会丢失，不应承载真实生产记录。

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
