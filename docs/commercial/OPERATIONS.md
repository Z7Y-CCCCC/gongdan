# 商业运维手册

生产环境的基础恢复目标为：核心数据 `RPO <= 15 分钟`、`RTO <= 2 小时`。每日 SQL 备份不能单独满足该目标，必须同时启用 5 分钟 binlog 归档、异地不可变对象存储和定期恢复演练。完整治理要求见 `PRODUCTION_SECURITY_BACKUP_DR_PLAN.md`。

## 生产备份前置条件

1. 备份桶必须与在线附件桶分开，最好属于独立云账号。
2. 备份桶开启 Versioning、默认加密和 Object Lock，默认保留模式为 `COMPLIANCE` 且不少于 35 天；仅仅显示“Object Lock 已启用”不算通过。生产主机凭据只允许写入和查询桶策略，不允许删除版本或缩短保留期。
3. Windows 主机安装 AWS CLI v2；Compose 主机安装 `aws`、`curl` 和 SHA-256 工具。
4. MySQL 开启 ROW binlog。Compose 已在 `docker-compose.yml` 固定配置；Windows MySQL 的 `my.ini` 至少包含：

```ini
[mysqld]
server-id=1
log_bin=binlog
binlog_format=ROW
binlog_row_image=FULL
binlog_expire_logs_seconds=3024000
```

修改 Windows MySQL 配置后，在维护窗口重启服务并执行：

```sql
SELECT @@log_bin, @@binlog_format, @@binlog_row_image, @@binlog_expire_logs_seconds;
```

Windows 使用独立备份账号和恢复账号。账号只能允许本机连接，密码由密钥系统生成并写入受 ACL/BitLocker 保护的生产配置，不得进入 Git：

```sql
CREATE USER 'gongdan_backup'@'127.0.0.1' IDENTIFIED BY 'REPLACE_WITH_RANDOM_SECRET';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT ON heihu_workorder.* TO 'gongdan_backup'@'127.0.0.1';
GRANT RELOAD, PROCESS, REPLICATION CLIENT, SHOW_ROUTINE ON *.* TO 'gongdan_backup'@'127.0.0.1';

CREATE USER 'gongdan_restore'@'127.0.0.1' IDENTIFIED BY 'REPLACE_WITH_DIFFERENT_RANDOM_SECRET';
GRANT ALL PRIVILEGES ON heihu_workorder.* TO 'gongdan_restore'@'127.0.0.1';
GRANT ALL PRIVILEGES ON `gongdan\_restore\_drill\_%`.* TO 'gongdan_restore'@'127.0.0.1';
GRANT ALL PRIVILEGES ON `gongdan\_pitr\_drill\_%`.* TO 'gongdan_restore'@'127.0.0.1';
GRANT CREATE, DROP ON *.* TO 'gongdan_restore'@'127.0.0.1';
```

恢复账号权限较高，只允许本机、禁止业务应用使用，并纳入季度复核。对权限要求更严格的环境，应由 DBA 在演练窗口临时启用该账号，演练完成后锁定。

## 日常检查

```powershell
cd C:\www\gongdan
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\status.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\monitor.ps1
```

检查内容：后端健康、MySQL 服务、最新备份时间和校验、磁盘空间、网页入口、移动入口、维护日志和 `runtime\monitor-status.json`。

## MCP 诊断入口

MCP 是独立的只读诊断侧车，默认不随 Compose 启动。需要给 AI Agent 做
健康检查或故障定位时，先在密钥系统配置 `MCP_API_KEY` 和专用的后台管理
账号，再执行：

```bash
docker compose --profile mcp up --build -d mcp
curl -fsS http://127.0.0.1:9300/healthz
```

入口只支持 Streamable HTTP 的 `POST /mcp`。工具调用只允许固定 GET 接口，
不允许 SQL、Shell、任意 URL 或业务写操作；日志和接口返回会脱敏。专用
账号不能和人工管理员共用，否则后端的单点会话策略会让人工账号被挤下线。
发布后建议依次调用 `system_health`、`run_smoke_test`，出问题时再调用
`search_system_logs`、`inspect_online_sessions` 和 `inspect_cache`。完整的
连接配置与协议测试见 `ops/mcp/README.md`。

## 启停与恢复

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-stack.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-backend.ps1
```

Nginx 使用已有 Windows 安装时，由 `NGINX_SERVICE_NAME` 或 `NGINX_EXE` 配置管理。不要同时启动公网隧道作为生产入口。

## 备份策略

- 每日自动备份数据库，全量文件包含精确 binlog 起点。
- 每 5 分钟轮转并归档已经关闭的 binlog，目标 RPO 不超过 15 分钟。
- 每次发布前手动备份数据库和上传文件。
- 本地备份保留不少于 35 天。
- 必须上传到启用 Versioning 和 Object Lock 的独立对象存储；第二磁盘/NAS 只作为快速恢复副本，不能代替不可变副本。
- 每次备份必须有 SHA-256 和 manifest。
- 备份目录不能放在系统盘唯一位置。

Compose 生产检查和手动执行：

```bash
ENV_FILE=.env sh ops/preflight-compose.sh
ENV_FILE=.env sh ops/check-backup-remote.sh
ENV_FILE=.env sh ops/backup-compose.sh
ENV_FILE=.env sh ops/archive-binlogs-compose.sh
ENV_FILE=.env sh ops/monitor-compose.sh
```

Linux 计划任务建议：全量备份每日 02:00；binlog 归档每 5 分钟；监控每 5 分钟；全量恢复和 PITR 演练每周各一次。计划任务必须使用绝对项目路径，并把 stdout/stderr 发送到独立日志或告警系统。

## 恢复演练与真实恢复

非破坏性演练：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-restore.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-pitr-restore.ps1
```

Compose 非破坏性演练：

```bash
ENV_FILE=.env sh ops/test-restore-compose.sh
ENV_FILE=.env sh ops/test-pitr-compose.sh
```

PITR 演练会读取全量备份中的 binlog 坐标，把后续归档日志回放到临时数据库，验证关键表后自动删除临时库，不修改生产库。演练结果追加到备份目录下的 `restore-drills.log`。

需要验证特定时间点时，可传入停止时间。Windows 建议使用 ISO 时间，例如 `-StopDateTime 2026-08-29T14:30:00`；Compose 使用 `PITR_TEST_STOP_DATETIME=2026-08-29T14:30:00`。

真实恢复必须在维护窗口执行，并明确目标数据库名：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\restore-backup.ps1 `
  -BackupPath C:\www\gongdan\data\backups\mysql_YYYYMMDD_HHMMSS.sql `
  -ConfirmDatabase heihu_workorder
```

真实恢复会重建数据库，执行前脚本会自动做安全备份；没有 `-ConfirmDatabase` 或名称不匹配时不会执行。

Compose 真实恢复要求数据库名精确确认。本地附件模式必须同时提供附件备份，除非明确接受不恢复附件：

```bash
CONFIRM_DATABASE=heihu_workorder \
DB_BACKUP_PATH=backups/mysql_YYYYMMDD_HHMMSS.sql \
UPLOAD_BACKUP_PATH=backups/report-images_YYYYMMDD_HHMMSS.tar.gz \
ENV_FILE=.env sh ops/restore-compose.sh
```

S3 附件模式必须先恢复对象版本并完成数据库/对象对账，再开放生产流量。

## 升级与回滚

1. 先查看版本和变更说明。
2. 执行 `verify-release.ps1`。
3. 执行 `preflight.ps1 -Mode Commercial`。
4. 备份数据库和上传文件。
5. 在维护窗口替换 `www`、`server`、`nginx` 和 `scripts`。
6. 启动服务并执行 `smoke-test.ps1 -Login`。
7. 失败时停止流量，保留故障现场，使用安全备份恢复数据库，再恢复上一稳定包。

数据库迁移采用向前兼容原则；已经执行的迁移不能只通过替换 JAR 回滚，必须结合数据库备份评估回退。

## 断电恢复

- MySQL 服务设置为自动启动。
- 安装启动恢复计划任务。
- Nginx 配置为服务或写入 `NGINX_EXE`。
- BIOS/UEFI 开启 `Restore on AC Power Loss`。
- 使用 UPS，避免数据库频繁硬断电。
- 重启后执行一次 `status.ps1` 和 `smoke-test.ps1` 并留存证据。
