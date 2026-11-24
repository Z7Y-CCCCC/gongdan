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
记录现保存到插件独立的文件数据库，SQL 查询按已签名工厂 ID 隔离，成功保存后重启仍可读回。
这不代表已实现生产设备采集、复杂业务权限或完成生产备份恢复验收。

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

## 记录持久化

本地默认数据库为进程工作目录下的 `./data/equipment-inspection.mv.db`。服务安装时用
`PLUGIN_DATA_DIR` 指定绝对、可写目录；Compose 使用独立的 `inspection-plugin-data` 数据卷，
容器重启/重建不删除记录。不要使用会删除数据卷的 `docker compose down -v` 来保留数据。

可用 `PLUGIN_DATABASE_PASSWORD`（Compose 为 `INSPECTION_PLUGIN_DATABASE_PASSWORD`）设置数据库访问密码，首次创建后须保持一致；它不代替
文件权限或磁盘加密。写入失败会返回错误，不会显示“保存成功”。数据库生成记录 ID，重启及
并发创建不会复用 ID。设备编号、点检结果、备注仍分别限制为 64、32、500 个字符。

旧版本仅保存在内存，请在升级前导出尚存活的演示记录；此前已经丢失的记录无法恢复。
离线备份先停止插件，再复制数据库文件；多实例并发访问、在线备份及生产恢复流程需独立验收。

`mvn -B clean package` 覆盖签名 API、文件数据库重启和工厂隔离。仓库根目录执行
`node tools/functional/check-plugins.mjs`，可验证最新 JAR 的隔离真实 HTTP 和进程重启链路。
