# 生产工作台动效改造验收

| 范围 | 改造 | 验收 |
| --- | --- | --- |
| 管理端主页 | 待审批、执行中和逾期入口前置；工单详情以只读抽屉展示接口返回的进度。 | Chromium 22 项，包括筛选和滚动保持、503 重试、390px 布局。 |
| 管理端登录和工作区 | 登录卡仅在进入登录页时播放；工作台准备成功后淡入；侧栏和标签页同步显示当前页。列设置、部门编辑、工单打印等弹窗统一进退场和焦点恢复。 | 工作区 Chromium 17 项；另有弹窗专项 17 项，包括键盘、深色窄屏及减少动态效果。 |
| 移动端看板和报工 | 工单筛选只影响最近列表；刷新失败保留同会话同日的上次数据。报工区显示提交中、确认成功、明确失败或未确认结果；未确认时先核对同工单记录，再决定是否重试。 | 移动 H5 动效与重试 13 项，另有 320px 深色核对流程 5 项及通用 H5 业务回归。 |

管理端 Vitest 790 项通过，移动端 223 项通过；管理端生产构建、移动端类型检查及 H5 / 微信小程序构建通过。未测试微信真机。

## 隔离复现

先在管理端目录执行 `npm run build`，在移动端目录执行 `npm run build`；以下命令从仓库根目录执行。夹具只监听环回地址，数据保存在进程内，不会连接业务后端。

```text
node frontend/scripts/ui-browser-fixture.mjs --overview-demo --port 5196
node mobile/test/browser-fixture.mjs --seed-orders --seed-rejected --mixed-recent --port 5199
```

先创建本地截图目录 `output/playwright/ui-motion/`、`output/playwright/home-redesign/` 和 `output/playwright/mobile-stage3/`。用独立 Playwright CLI 会话打开对应夹具的 URL，再用 `run-code --filename` 运行脚本：

- 管理端 `frontend/scripts/workspace-motion-browser.mjs`、`frontend/scripts/home-redesign-browser.mjs`。
- 移动 H5 `mobile/test/motion-browser-scenario.mjs`；测试提交后响应丢失时，另起一个全新移动夹具运行 `mobile/test/report-review-browser-scenario.mjs`。

截图及短视频在本地 `output/playwright/ui-motion/`，未纳入 Git。
