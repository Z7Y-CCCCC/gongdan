# 黑湖小工单仿品

Spring Boot + Vue 3 + MySQL 9.2 的小工单系统雏形。当前已实现电脑端账号管理、物料清单、自定义配置字段（包含高保真美术风格表单）、以及出入库单单头与明细的动态字段解析等高级特性。

## 默认登录账号

- 工厂代码：`HHGD`
- 用户名：`admin`
- 密码：`123456`

## 本地 MySQL 配置

项目默认连接本机 MySQL 9.2 服务：

- 主机：`localhost`
- 端口：`3308`
- 用户名：`root`
- 密码：`root`
- 数据库：`heihu_workorder`

后端启动时会自动建表，并写入默认工厂和管理员账号。

## 启动方式

1. 确认本机 `MySQL92` 服务已启动，并监听 `3308` 端口。

2. 启动后端：

```bash
cd backend
mvn spring-boot:run
```

3. 启动前端：

```bash
cd frontend
npm install
npm run dev
```

`npm run dev` 是前端开发服务器，启动后会一直占用当前终端窗口；看到 `http://localhost:5173` 后保持它运行即可，需要停止时按 `Ctrl+C`。

前端默认访问 `http://localhost:5173`，接口默认访问 `http://localhost:8080/api`。

## 角色规则

- 管理员：允许电脑端和手机端登录。
- 生产人员：只允许手机端登录，电脑端登录会被后端拒绝。
