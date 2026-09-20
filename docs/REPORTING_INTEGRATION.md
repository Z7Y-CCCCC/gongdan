# 高级报表集成

## 方案

系统保留原有“生产报表”和“车间看板”，新增“报表 > 高级报表”。高级报表使用 UReport2，直接嵌入现有 Spring Boot JVM，不依赖 Docker、独立 BI 服务或独立元数据库。

UReport2 是纯 Java/Spring 报表引擎，支持浏览器设计器、预览、打印以及 Excel/PDF 导出。官方项目和许可说明见 <https://github.com/youseries/ureport>。

后端通过 `/api/ureport/session` 将当前系统的管理员 Bearer 会话换成仅限 `/ureport` 路径的 HttpOnly Cookie；UReport2 Servlet 的所有请求都经过现有管理员会话校验。

## 本地启动

启动后端和前端即可：

```powershell
cd backend
mvn spring-boot:run

cd ..\frontend
npm run dev
```

登录系统后打开“报表 > 高级报表”。直接访问 `/ureport/designer` 但没有通过系统登录时应返回未授权，而不是打开设计器。

## 报表模板存储

默认模板目录为应用工作目录下的 `ureportfiles`。生产环境应让运行 Java 进程的账号拥有该目录的读写权限，并把目录纳入备份。也可以通过 JVM 参数覆盖：

```text
-Dureport.fileStoreDir=C:\\www\\gongdan\\data\\ureportfiles
```

## 工厂数据隔离

UReport2 本身不会自动理解本系统的 `factory_id` 权限。上线前必须完成以下约束：

1. 报表 SQL 明确使用当前工厂参数，或只查询已经按工厂隔离的只读视图。
2. 不允许工厂管理员把任意业务库连接配置到报表数据源。
3. 使用两个工厂账号分别预览同一模板，确认不能看到对方数据。
4. 报表模板变更需要保留审计记录并纳入备份。

当前集成先保护 UReport2 设计器和预览入口为管理员功能；复杂的工厂参数注入应在确定模板字段后继续封装，不能依赖前端隐藏字段。

## 部署

裸机部署只需按现有方式启动 Java 后端、前端静态站点、MySQL 和 Redis。无需安装或启动 Docker，也无需填写 Superset 地址、账号或 Dashboard ID。

原先的 Superset 适配代码已从默认集成中移除；如果以后需要跨系统自助分析，可以作为独立可选服务再次接入，但不作为本系统的运行前提。
