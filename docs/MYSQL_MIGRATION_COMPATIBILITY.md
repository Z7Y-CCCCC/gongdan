# MySQL 迁移兼容与验收（2026-11-24）

## 修复

- MySQL 的 `ALTER TABLE ... ADD COLUMN` 不支持 V15、V18 使用的 `IF NOT EXISTS` 语法。兼容层只改写 MySQL 执行这两个脚本时的五条加列语句：查当前数据库的列，缺列才添加。V18 的回填与索引语句继续执行。H2 和其他迁移不受影响。
- V15、V18 原文件不变，也不写改历史。Flyway 8.5.13 仍从原文件计算 SQL 校验值（V15 为 `1090301005`、V18 为 `-1652635468`）。兼容层只接受已固定内容的原文件；升级 Flyway 时须重跑 MySQL 验收。
- V16 和 V22 将部分插件清单、版本说明及配置列、外部数据源配置和凭据密文列建成 `TEXT`，而 Hibernate 的 `@Lob` 映射在 MySQL 期望 `LONGTEXT`。新增 V25 扩宽这六列，保留已有数据和空值约束；不修改 V16 或 V22。
- 后端 CI 新增 MySQL 8.4 服务，在打包前执行实际 MySQL 和 H2 迁移检查。

## 验收范围

- 隔离 MySQL 8.4.13 与 H2 的迁移回归：22 项通过。覆盖 MySQL 空库 V1→V25、带已有会话字段及样例数据的 V14→V25，以及含外部数据源和插件记录的 V24→V25。验证 Flyway `validate`、再次执行零迁移、原 SQL 校验值不变，以及旧数据和空值保留。
- 新打包后端在同一 MySQL 8.4.13 隔离环境开启 Flyway 和 Hibernate `validate` 启动，数据库到达 V25，健康接口返回 `UP`。
- 另在本机隔离 MySQL 9.2 验证了 V1→V24 与 V14→V24 的历史兼容修复。该版本不是生产部署基线。
- 未操作真实业务库或其备份，未部署生产。发布前先备份，在实际备份的隔离副本验证；V25 是 DDL，需为表变更预留维护窗口。若数据库已有失败的 Flyway 历史项，应先核对实际结构和备份，再由运维处理；不要自动修复历史、关闭校验或用 Hibernate 自动更新绕过迁移。

参考：[MySQL 8.4 ALTER TABLE](https://dev.mysql.com/doc/refman/8.4/en/alter-table.html)、[Flyway 版本迁移](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations)。
