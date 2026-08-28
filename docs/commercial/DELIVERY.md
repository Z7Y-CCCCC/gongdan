# 黑湖小工单商业交付说明

## 交付版本

- 当前版本：`1.0.0`
- 交付形态：Windows Server 原生部署包，Java 8、MySQL、Nginx
- 演示环境：`demo` profile，允许模拟微信身份和日志短信，仅限内网演示
- 商业环境：`prod` profile，要求正式微信配置、HTTPS、Flyway、非 root 数据库账号和明确备份策略

## 第三级交付定义

第三级不是“页面能打开”，而是以下条件同时成立：

1. 源码、构建包、数据库迁移脚本和版本号可对应。
2. 发布包可以在目标服务器重复部署。
3. 正式配置预检不通过时，系统拒绝进入上线步骤。
4. 数据库、上传文件和日志有明确的备份及保留策略。
5. 备份可以校验，恢复演练可以执行，真实恢复受确认保护。
6. 启动、健康检查、监控、告警、日志和断电恢复路径有记录。
7. 关键验收项有测试证据，P0/P1 问题为零。
8. 版本升级、回滚、故障响应和维护责任已经交接。

## 发布物

- `release-windows/`：Windows 发布包
- `BUILD-INFO.txt`：构建时间、版本和 Git 提交
- `RELEASE-MANIFEST.json`：发布文件 SHA-256 清单
- `config/gongdan.env.example`：内网演示模板
- `config/gongdan.prod.env.example`：商业环境模板
- `scripts/preflight.ps1`：上线前配置预检
- `scripts/verify-release.ps1`：发布包完整性校验
- `scripts/smoke-test.ps1`：网页、移动端、后端和登录冒烟测试
- `scripts/backup-database.ps1`：数据库和上传文件备份
- `scripts/test-restore.ps1`：非破坏性恢复演练
- `scripts/restore-backup.ps1`：受确认保护的真实恢复
- `scripts/monitor.ps1`：运行期健康、备份和磁盘监控

## 上线门禁

```powershell
cd C:\www\gongdan
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-release.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\preflight.ps1 -Mode Commercial
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup-database.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-restore.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1 -Login
```

任何一步失败，都不能签署正式上线验收单。

## 当前边界

- 当前办公室 Windows Server + MySQL 9.2 配置适合内网演示；商业 `prod` 发布包按 MySQL 8.4 LTS 验证，因为当前 Java 8 / Spring Boot 2.7 / Flyway 组合不把 MySQL 9.2 作为生产迁移基线。
- 本地上传目录只适合单后端实例，并且必须有第二份备份；多实例必须配置 S3 兼容对象存储。
- 没有域名、公网 IP、微信 AppID/Secret 和短信网关时，只能完成内网演示验收，不能签署公网小程序和第三方联调验收。
- 断电恢复可以由 Windows 任务和 MySQL InnoDB 完成软件层恢复；服务器自动上电仍需要 BIOS/UEFI 和 UPS 配合。
