# 个人 UI/UX 技能与工单界面改造

## 个人技能

- 已安装：`C:/Users/27323/.codex/skills/ethan-ui-ux`，入口 `SKILL.md`，可用 `$ethan-ui-ux` 调用；保留正常自动发现，不修改其它全局配置。
- 按 Ethan 的偏好组织：删除重复宣传/说明；明确操作优先图标；不明确的业务操作保留短名；动画服务于反馈、层级和状态，支持减少动态效果。
- 跨项目通用，不强制绿色、Vue 或工单业务规则。工单专用约束单独位于 `design-system/gongdan/MASTER.md`。
- 查询入口为 Python 标准库工具，中文关键词补充、跨栈检索、版本提醒；默认不联网、不写项目、不安装依赖。每次检索先检查上游文件 SHA-256。

示例（从技能目录运行）：

```powershell
python -B scripts/query.py "图标 标签" --domain ux --json
python -B scripts/query.py "props destructure" --stack vue --target-version 3.4.34 --json
python -B scripts/query.py --verify
```

## 来源与改造边界

主基底：[UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/tree/e8b065f0f3aaf2e680fd125f000e4f5e351ed108)，固定 SHA `e8b065f0f3aaf2e680fd125f000e4f5e351ed108`，MIT。

项目副本 `tools/ui-ux/` 保留许可、来源清单和哈希；精选资料有 4,355 行、22 个技术栈。未安装上游 CLI、字体/图库或 GSAP。保留上游字节，不直接执行检索结果里的示例。Windows Git 属性保护 vendored 文件，避免换行转换破坏哈希。

补充依据：GitHub Primer 的表格/表单/按钮命名规范，以及 W3C 颜色与目标尺寸标准。Primer 已归档，只用固定参考，不引入旧文档站依赖。未复制没有许可证的 `manikandan-22/ux-guidelines`。

Bilibili 参考是金石未开的公开合集与视频说明，不声称逐条观看了全部视频或阅读了全部字幕；用户允许改用 GitHub 综合资料。

明确过滤：Vue 3.5 API 不能直接给移动端 Vue 3.4.34；`v-memo="[item.id]"` 不足以表示可变数量/状态；Vue `ref` 可以持有复杂对象，不能照搬上游的禁止建议。检索是参考，不等于应用已经通过验收。

## 界面与确认缺陷

| 范围 | 已落地 |
| --- | --- |
| 登录/工作区 | 移除宣传 Hero、重复说明及装饰；修正旧 CSS 导致的登录卡偏位和蓝色残留下边框；侧栏支持键盘；标签页名称对应真实页面。 |
| 通用操作 | 明确动作图标化，保留 title/ARIA 名称；日志、行高、打印方式、权限、审批等保留业务短名；列图标统一为现有 SVG 家族。 |
| 表格/加载 | 删除全局 MutationObserver/innerHTML 表头装饰；保留排序、数据、实际加载阶段、进度与错误重试。 |
| 通知失败 | 失败保留之前的数据和查询，不把网络错误显示成“共0条”；错误状态不显示无效总数/分页，重试成功后恢复真实计数。 |
| 真正的主页 | 确认 App 实际引用 `views/HomeDashboardPage.vue`，不是同名旧组件；去掉重复用户区域，统一字体、主题表格和操作状态。 |
| 主页查看工单 | 原空 handler 改为真实导航与单条 GET 只读详情；不依赖列表上限，不触发编辑/写入；目标/会话改变时丢弃旧响应，错误可重试。 |
| 公共弹窗 | 列设置、删除确认、工序卡设置补齐焦点进入、Tab 循环、Escape 和恢复；停用缓存页释放键盘所有权；快速嵌套关闭回父窗；不聚焦 hidden/inert 旧目标。 |
| 列设置 | 不能应用零可见列；取消不改原设置；删除未实现的“最多4列”限制说明；修正“全部”换行。 |
| 手机九页 | 44px 命中区、有名称的图标按钮；保留底栏/业务状态/审批和驳回信息；修正分页被底栏覆盖、报工短名不可见和重报布局；减少动态效果。 |

## 验证与复现

- 技能入口校验通过；项目与技能位置的查询工具各 14/14 测试通过。覆盖跨目录运行、中文查询、版本警告、无安装/网络/项目写入、篡改拒绝与许可完整性。
- 独立技能前向测试：非绿色、非 Vue 的原生文件管理器实现及 24 项离线浏览器检查通过。Vue 3.4 示例给出安全解构/完整 memo 依赖，但该独立示例未编译运行，不作已验证声明。
- 管理端最终全量 290/290，类型检查与生产构建通过，见 `output/ui-final-frontend-tests.log`、`output/ui-final-frontend-build.log`；UI Node 契约 16 项、图标结构扫描 673 个按钮，缺失名称 0。扫描不是完整 AccName 或可访问性认证。
- 手机最终 115/115，类型检查、H5 和微信构建通过，日志为 `mobile/test/mobile-ui-all.log`、`mobile/test/mobile-ui-build-h5.log`、`mobile/test/mobile-ui-build-weixin.log`。
- 管理端隔离复现：`frontend/scripts/ui-browser-fixture.mjs` + `frontend/scripts/ui-browser-scenario.mjs`，20 项检查通过。覆盖 390px 登录、真实主页入口/查看工单、键盘导航、危险取消、列弹窗/深色/640px、503 错误恢复及减少动态效果；没有应用脚本异常，HTTP 503 是明确模拟的测试故障。结果/截图在 `output/playwright/management-ui/`。
- 手机生产 H5 390×844 流程通过；任务 20→40/41、未下载工单搜索、报工弹层/待审/驳回、现场事件单写入、返回看板；13 张截图与结果位于 `output/playwright/mobile-ui/`。
- UI 结构检查接入 `npm test` 的 pretest；参考工具测试加入 CI。没有运行远端 CI。

保留此前系统审计修复，详见 `SYSTEM_DEEP_AUDIT_2026-11-24.md`。本轮没有生产部署、提交、微信真机/读屏认证或生产压测；已确认缺陷修复与通过回归不代表所有潜在 bug 清零。
