# Requirements Document

## Introduction

本功能为电脑端管理员账号登录后默认展示的"主页"仪表盘。该主页聚合工厂当前的工序生产任务情况、工单进度、绩效指标与生产实时播报，使管理员一打开系统即可掌握当日生产全貌，无需手动进入各业务模块。

主页与已有的"车间看板"（DashboardPage.vue，深色全屏看板）是两个不同的页面：车间看板为大屏巡检场景的独立深色页面，本主页为浅色背景、与侧边栏共存的常驻业务首页。

主页包含 4 个区块：
1. 工序任务卡片区：横向排列若干个工序统计卡片，按今天/昨天/本月切换。
2. 工单进度报表：按工单状态分类切换的工单列表表格。
3. 绩效指标：6 项汇总指标。
4. 生产实时播报：滚动展示最近的报工记录。

## Glossary

- **Home_Dashboard**：电脑端管理员账号登录后默认进入的主页页面，包含工序任务、工单进度报表、绩效指标、生产实时播报四个区块。
- **Process_Task_Card**：工序任务卡片，展示某一工序在选定时间范围内的生产任务数、计划数、良品数、不良品数、进度百分比与进度条。
- **Process_Task_Section**：工序任务卡片区，由顶部标题栏（含设置按钮、"包含未开始生产任务"复选框、刷新按钮、时间范围切换标签）和横向工序卡片列表组成。
- **Process_Card_Settings**：工序任务卡片区的"设置"功能，允许管理员选择展示哪些工序、卡片显示顺序与每个工序的颜色。
- **Time_Range**：工序任务卡片区与绩效指标使用的时间范围维度，取值为 `today`、`yesterday`、`this_month` 三者之一。
- **Work_Order_Status_Tab**：工单进度报表中的状态分类切换标签，取值为 `not_started`、`in_progress`、`due_within_3_days`、`overdue_unfinished`、`overdue_finished_this_month`、`finished_today`、`finished_this_month`。
- **Work_Order**：系统中已有的工单实体，含字段 `code`（工单编号）、`productCode`（产品编号）、`productName`（产品名称）、`productSpec`（产品规格）、`status`（状态）、`planEnd`（计划结束时间）、`plannedQuantity`（计划数）、`goodQuantity`（良品数）、`defectQuantity`（不良品数）。
- **Work_Order_Report**：报工记录，含字段 `createdAt`（报工时间）、`reporterName`（员工姓名）、`processName`（工序名称）、`workOrderCode`（工号）、`goodQuantity`、`defectQuantity`。
- **Performance_Indicator**：绩效指标，6 项数值（计划数、完成数量、工单数量、不良品率、平均工单生产周期、超期未完成工单），统计范围与工序任务卡片区当前所选 Time_Range 一致。
- **Live_Broadcast**：生产实时播报，按报工时间倒序滚动展示最近的报工记录。
- **Admin_User**：通过电脑端登录的管理员（`role = ADMIN`）。
- **Production_User**：生产人员（`role = PRODUCTION`），仅手机端登录，不会进入 Home_Dashboard。
- **Top_Bar**：主页顶部栏，左侧为页面名称"主页"，右侧为头像图标与当前登录用户姓名。
- **User_Preference_Store**：现有的用户偏好后端接口（`GET/PUT /api/user/preferences/{key}`），用于持久化 Process_Card_Settings。

## Requirements

### Requirement 1: 登录后默认进入主页

**User Story:** 作为管理员，我希望电脑端登录成功后默认进入主页仪表盘，从而第一时间看到工厂生产全貌。

#### Acceptance Criteria

1. WHEN Admin_User 在电脑端通过用户名密码登录成功，THE Home_Dashboard SHALL 作为登录后默认显示的页面。
2. WHEN Admin_User 在电脑端通过手机号登录成功，THE Home_Dashboard SHALL 作为登录后默认显示的页面。
3. WHEN Admin_User 点击侧边栏顶部的"主页"导航项，THE Home_Dashboard SHALL 在主内容区显示。
4. WHILE Home_Dashboard 处于显示状态，THE 侧边栏"主页"导航项 SHALL 处于选中态。
5. IF 当前登录用户的 `role` 不等于 `ADMIN`，THEN THE Home_Dashboard SHALL 不被渲染。

### Requirement 2: 顶部栏显示

**User Story:** 作为管理员，我希望主页顶部栏显示页面名称和我的账号信息，以便确认当前所在页面与登录身份。

#### Acceptance Criteria

1. THE Top_Bar SHALL 在左侧显示固定文字"主页"。
2. THE Top_Bar SHALL 在右侧显示用户头像图标与当前登录用户的 `name` 字段值。
3. WHEN 当前登录用户的 `name` 字段为空字符串或 `null`，THE Top_Bar SHALL 在右侧显示当前登录用户的 `username` 字段值。
4. IF 用户头像图标加载失败或不可用，THEN THE Top_Bar SHALL 隐藏整个右侧用户区域（头像与用户名一并不显示）。

### Requirement 3: 工序任务卡片区结构

**User Story:** 作为管理员，我希望以卡片形式横向查看每个工序的生产任务汇总，以便快速比较各工序产能与进度。

#### Acceptance Criteria

1. THE Process_Task_Section SHALL 在顶部显示标题文字"工序任务"。
2. THE Process_Task_Section SHALL 在标题右侧显示齿轮图标的"设置"按钮。
3. THE Process_Task_Section SHALL 在标题旁显示文字为"包含未开始生产任务"的复选框，该复选框默认勾选状态为未勾选。
4. THE Process_Task_Section SHALL 在右上角显示"刷新"按钮。
5. THE Process_Task_Section SHALL 在右上角显示三个时间范围切换标签，分别为"今天"、"昨天"、"本月"，默认选中"今天"。
6. WHILE Process_Task_Section 处于显示状态，THE Process_Task_Section SHALL 横向排列 Process_Card_Settings 中已配置的全部 Process_Task_Card。
7. WHEN Process_Task_Section 渲染的 Process_Task_Card 总宽度超出可见区域宽度，THE Process_Task_Section SHALL 提供横向滚动以查看溢出的卡片。

### Requirement 4: 工序任务卡片内容

**User Story:** 作为管理员，我希望每张工序卡片清晰展示该工序的关键生产数字与进度，以便快速判断该工序状态。

#### Acceptance Criteria

1. THE Process_Task_Card SHALL 在左上角显示工序名称标签，标签底色取自该工序在 Process_Card_Settings 中配置的颜色值。
2. THE Process_Task_Card SHALL 在主体显示一个大号数字，数值为该工序在所选 Time_Range 内对应的"生产任务数"。
3. THE Process_Task_Card SHALL 在大号数字下方显示文字"生产任务数"。
4. THE Process_Task_Card SHALL 在右侧依次显示三行小指标：第一行"计划数"及对应数值，第二行"良品数"及对应数值，第三行"不良品数"及对应数值。
5. THE Process_Task_Card SHALL 在底部显示一行"进度"文字与百分比数值，百分比四舍五入保留整数并以 `%` 结尾。
6. THE Process_Task_Card SHALL 在底部显示一条进度条，进度条已填充宽度占总宽度的比例等于第 5 条所述的百分比。
7. WHEN Process_Task_Card 计算所得"进度"百分比大于等于 100，THE Process_Task_Card SHALL 将进度条已填充宽度限定为总宽度的 100%。
8. WHEN Process_Task_Card 对应工序的"计划数"等于 0，THE Process_Task_Card SHALL 将"进度"百分比显示为 `0%`。
9. THE Process_Task_Card SHALL 将"进度"百分比按公式 `round(良品数 / 计划数 * 100)` 计算（计划数大于 0 时）。

### Requirement 5: 工序任务卡片区数据加载与刷新

**User Story:** 作为管理员，我希望工序卡片数据按所选时间范围加载并支持手动刷新，以便随时获取最新数据。

#### Acceptance Criteria

1. WHEN Home_Dashboard 首次加载，THE Process_Task_Section SHALL 以 Time_Range 等于 `today`、"包含未开始生产任务"为未勾选状态请求工序卡片数据并渲染。
2. WHEN Admin_User 切换 Time_Range 至"今天"、"昨天"或"本月"，THE Process_Task_Section SHALL 重新请求对应 Time_Range 的工序卡片数据并渲染。
3. WHEN Admin_User 勾选或取消勾选"包含未开始生产任务"复选框，THE Process_Task_Section SHALL 重新请求工序卡片数据并渲染。
4. WHEN Admin_User 点击"刷新"按钮，THE Process_Task_Section SHALL 以当前 Time_Range 与"包含未开始生产任务"勾选状态重新请求工序卡片数据并渲染。
5. THE 工序任务卡片数据接口 SHALL 按工序返回每个工序在所选 Time_Range 内的生产任务数、计划数、良品数、不良品数。
6. WHILE "包含未开始生产任务"复选框处于未勾选状态，THE 工序任务卡片数据接口 SHALL 在统计生产任务数时排除尚未开始的生产任务。
7. WHILE "包含未开始生产任务"复选框处于勾选状态，THE 工序任务卡片数据接口 SHALL 在统计生产任务数时包含尚未开始的生产任务。
8. IF 工序任务卡片数据接口请求失败，THEN THE Process_Task_Section SHALL 保留上一次成功加载的 Process_Task_Card 列表并在卡片区上方显示加载失败提示文字。
9. WHEN Process_Task_Section 首次加载且工序任务卡片数据接口请求失败，THE Process_Task_Section SHALL 在卡片区位置显示加载失败提示文字（此时无上一次成功结果可保留）。

### Requirement 6: 工序任务卡片设置

**User Story:** 作为管理员，我希望可以选择主页展示哪些工序卡片以及它们的颜色与顺序，以便突出关注的工序。

#### Acceptance Criteria

1. WHEN Admin_User 点击工序任务卡片区的"设置"按钮，THE Process_Task_Section SHALL 打开 Process_Card_Settings 设置弹窗。
2. THE Process_Card_Settings 设置弹窗 SHALL 列出当前工厂全部工序，每行包含"是否展示"勾选项、工序名称、颜色选择控件。
3. THE Process_Card_Settings 设置弹窗 SHALL 允许 Admin_User 通过拖拽或上下移动按钮调整工序行的顺序。
4. WHEN Admin_User 在 Process_Card_Settings 设置弹窗点击"保存"按钮，THE Home_Dashboard SHALL 通过 User_Preference_Store 以键 `home_dashboard.process_cards` 持久化所选工序、顺序与颜色。
5. WHEN Admin_User 在 Process_Card_Settings 设置弹窗点击"保存"按钮，THE Process_Task_Section SHALL 按新的配置重新渲染 Process_Task_Card 列表。
6. WHEN Home_Dashboard 加载且 User_Preference_Store 中不存在键 `home_dashboard.process_cards`，THE Process_Task_Section SHALL 默认按工序创建时间升序选取前 4 个工序作为展示工序。
7. WHEN Home_Dashboard 加载且 User_Preference_Store 中不存在键 `home_dashboard.process_cards`，THE Process_Task_Section SHALL 为默认展示的工序按顺序分配预设颜色：第 1 个为蓝色、第 2 个为灰色、第 3 个为黄色、第 4 个为绿色。
8. IF User_Preference_Store 中保存的工序 ID 在当前工厂工序列表中不存在，THEN THE Process_Task_Section SHALL 跳过该 ID 并按剩余配置渲染。

### Requirement 7: 工单进度报表状态切换

**User Story:** 作为管理员，我希望按 7 种业务状态切换查看工单列表，以便聚焦不同时间维度与状态的工单。

#### Acceptance Criteria

1. THE 工单进度报表区 SHALL 在顶部显示标题"工单进度报表"。
2. THE 工单进度报表区 SHALL 在标题下方显示 7 个状态切换标签，依次为"未开始"、"执行中"、"3天内到期"、"超期未完工"、"本月超期完成"、"今日完成"、"本月完成"。
3. WHEN Home_Dashboard 首次加载，THE 工单进度报表区 SHALL 默认选中"未开始"标签并加载对应工单列表。
4. WHEN Admin_User 切换 Work_Order_Status_Tab，THE 工单进度报表区 SHALL 重新请求对应分类的工单列表并渲染表格。
5. WHEN Work_Order_Status_Tab 等于"未开始"，THE 工单进度报表区 SHALL 仅展示 `status = NOT_STARTED` 的 Work_Order。
6. WHEN Work_Order_Status_Tab 等于"执行中"，THE 工单进度报表区 SHALL 仅展示 `status = IN_PROGRESS` 的 Work_Order。
7. WHEN Work_Order_Status_Tab 等于"3天内到期"，THE 工单进度报表区 SHALL 仅展示 `status` 不等于 `COMPLETED` 且 `planEnd` 在当前时间起 3 个自然日内（含当日）的 Work_Order。
8. WHEN Work_Order_Status_Tab 等于"超期未完工"，THE 工单进度报表区 SHALL 仅展示 `status` 不等于 `COMPLETED` 且 `planEnd` 早于当前时间的 Work_Order。
9. WHEN Work_Order_Status_Tab 等于"本月超期完成"，THE 工单进度报表区 SHALL 仅展示 `status = COMPLETED`、实际完成时间在当月、且实际完成时间晚于 `planEnd` 的 Work_Order。
10. WHEN Work_Order_Status_Tab 等于"今日完成"，THE 工单进度报表区 SHALL 仅展示 `status = COMPLETED` 且实际完成时间为当日的 Work_Order。
11. WHEN Work_Order_Status_Tab 等于"本月完成"，THE 工单进度报表区 SHALL 仅展示 `status = COMPLETED` 且实际完成时间在当月的 Work_Order。

### Requirement 8: 工单进度报表表格

**User Story:** 作为管理员，我希望工单列表以表格形式展示关键字段并可排序，以便逐项核对工单。

#### Acceptance Criteria

1. THE 工单进度报表表格 SHALL 包含列：序号、工单编号、产品编号、产品名称、产品规格、状态、操作。
2. THE 工单进度报表表格 SHALL 在"工单编号"、"产品编号"、"产品名称"、"产品规格"、"状态"列表头显示排序箭头图标。
3. WHEN Admin_User 点击列表头的排序箭头，THE 工单进度报表表格 SHALL 按该列在升序与降序之间切换并重新排序当前结果集。
4. THE 工单进度报表表格的"序号"列 SHALL 从 1 起按当前结果集顺序递增显示。
5. THE 工单进度报表表格的"操作"列 SHALL 为每行显示一个"查看"链接。
6. WHEN Admin_User 点击某行的"查看"链接，THE Home_Dashboard SHALL 跳转至工单详情页面并定位到该工单。
7. WHEN 当前 Work_Order_Status_Tab 对应的工单结果集为空，THE 工单进度报表表格 SHALL 在表格主体显示"暂无数据"占位图标与文字。
8. WHEN 工单进度报表表格内容总宽度或总高度超出表格可见区域，THE 工单进度报表表格 SHALL 提供对应方向的滚动条。

### Requirement 9: 绩效指标区

**User Story:** 作为管理员，我希望查看 6 个核心绩效数字，以便快速评估当前生产健康度。

#### Acceptance Criteria

1. THE 绩效指标区 SHALL 在顶部显示标题"绩效指标"。
2. THE 绩效指标区 SHALL 以 2 列 3 行的网格显示 6 项 Performance_Indicator，依次为"计划数"、"完成数量"、"工单数量"、"不良品率"、"平均工单生产周期"、"超期未完成工单"。
3. THE 绩效指标区 SHALL 将"完成数量"的数值文字显示为绿色。
4. THE 绩效指标区 SHALL 将"不良品率"的数值文字显示为红色。
5. THE 绩效指标区"计划数"的值 SHALL 等于在所选 Time_Range 内全部 Work_Order 的 `plannedQuantity` 之和。
6. THE 绩效指标区"完成数量"的值 SHALL 等于在所选 Time_Range 内全部 Work_Order_Report 的 `goodQuantity` 之和。
7. THE 绩效指标区"工单数量"的值 SHALL 等于在所选 Time_Range 内创建的 Work_Order 总数。
8. THE 绩效指标区"不良品率"的值 SHALL 按公式 `所选 Time_Range 内全部 Work_Order_Report 的 defectQuantity 之和 / (goodQuantity 之和 + defectQuantity 之和) * 100%` 计算，结果四舍五入保留两位小数并以 `%` 结尾。
9. WHEN 第 8 条公式分母等于 0，THE 绩效指标区"不良品率"的值 SHALL 显示为 `0.00%`，无论分母为 0 是因为完全没有生产数据还是良品与不良品数量均为 0。
10. WHEN 第 8 条公式计算结果大于 100%，THE 绩效指标区"不良品率"的值 SHALL 按计算所得真实数值显示，不进行截断或封顶。
11. THE 绩效指标区"平均工单生产周期"的值 SHALL 等于在所选 Time_Range 内 `status = COMPLETED` 的 Work_Order 的（实际完成时间 - 创建时间）天数平均值，四舍五入保留 1 位小数并以"天"结尾。
12. WHEN 在所选 Time_Range 内不存在 `status = COMPLETED` 的 Work_Order，THE 绩效指标区"平均工单生产周期"的值 SHALL 显示为 `0.0天`。
13. THE 绩效指标区"超期未完成工单"的值 SHALL 等于当前 `status` 不等于 `COMPLETED` 且 `planEnd` 早于当前时间的 Work_Order 总数。
14. WHEN Process_Task_Section 的 Time_Range 发生变化，THE 绩效指标区 SHALL 按新的 Time_Range 重新请求并渲染。

### Requirement 10: 生产实时播报

**User Story:** 作为管理员，我希望主页持续滚动显示最近的报工记录，以便实时感知车间产出节奏。

#### Acceptance Criteria

1. THE Live_Broadcast 区 SHALL 在顶部显示标题"生产实时播报"。
2. THE Live_Broadcast 区 SHALL 按 `createdAt` 倒序展示最近 20 条 Work_Order_Report。
3. THE Live_Broadcast 区每条记录 SHALL 显示一个圆点图标、`createdAt` 时间戳（格式 `YYYY-MM-DD HH:mm`）、`reporterName` 与 `processName` 拼接成"{reporterName} {processName}"的文本、以及"工号: {workOrderCode}"的链接。
4. WHEN Admin_User 点击某条记录的工号链接，THE Home_Dashboard SHALL 跳转至该工单的详情页面。
5. WHILE Live_Broadcast 区可见且 Home_Dashboard 处于显示状态，THE Home_Dashboard SHALL 每 30 秒向后端轮询最近的 Work_Order_Report 并将新增记录追加到列表顶部。
6. WHEN Home_Dashboard 离开显示状态，THE Home_Dashboard SHALL 停止 Live_Broadcast 的轮询。
7. WHEN Live_Broadcast 区记录条数超过 20，THE Live_Broadcast 区 SHALL 仅保留最新的 20 条并丢弃更早的记录。
8. IF Live_Broadcast 区在初次加载时返回空列表，THEN THE Live_Broadcast 区 SHALL 显示文字"暂无播报"。

### Requirement 11: 响应式与布局

**User Story:** 作为管理员，我希望主页四个区块在不同分辨率下都能合理显示，以便在不同显示器上使用。

#### Acceptance Criteria

1. WHILE 浏览器视口宽度大于等于 1280 像素，THE Home_Dashboard SHALL 将工序任务卡片区置于顶部一行，工单进度报表置于左下，绩效指标区与生产实时播报上下堆叠置于右下。
2. WHILE 浏览器视口宽度小于 1280 像素，THE Home_Dashboard SHALL 将工单进度报表、绩效指标区、生产实时播报依次自上而下纵向堆叠在工序任务卡片区下方。
3. THE Home_Dashboard SHALL 在浅色背景上渲染，区块容器使用白色或近白色卡片底色。

### Requirement 12: 权限与登录态

**User Story:** 作为系统，我希望未登录或非管理员用户访问主页时被正确处理，以保证数据安全。

#### Acceptance Criteria

1. IF Home_Dashboard 任一数据接口请求返回 HTTP 401，THEN THE Home_Dashboard SHALL 清除本地登录态并跳转至登录页面。
2. IF Home_Dashboard 任一数据接口请求返回 HTTP 403，THEN THE Home_Dashboard SHALL 隐藏所有数据区块；隐藏数据区块为强制行为，"无权限访问主页"提示文字的显示在隐藏完成后再尝试，提示文字不可显示时不影响数据区块已被隐藏的状态。
3. THE Home_Dashboard 的所有数据接口请求 SHALL 在 `Authorization` 头中携带当前登录态的 Bearer Token。
