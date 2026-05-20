# Implementation Plan: Home Dashboard (主页仪表盘)

## Overview

实现电脑端管理员登录后默认展示的主页仪表盘，包含工序任务卡片区、工单进度报表、绩效指标、生产实时播报四个区块。后端新增 `HomeDashboardController` 与 `HomeDashboardService` 提供聚合查询接口，前端新增 `HomeDashboardPage.vue` 及四个子区块组件。

## Tasks

- [x] 1. 后端基础设施与数据模型变更
  - [x] 1.1 WorkOrder 实体新增 completedAt 字段
    - 在 `WorkOrder.java` 中新增 `@Column(name = "completed_at") private LocalDateTime completedAt` 字段及 getter/setter
    - 在 `WorkOrderService.applyRequest` 中添加状态变更时写入/清空 `completedAt` 的逻辑
    - 添加数据库迁移脚本：新增列 `completed_at DATETIME NULL`，并对存量 `status='COMPLETED'` 的记录用 `updated_at` 回填
    - _Requirements: 9.11, 9.12_

  - [x] 1.2 新增 TimeRange 枚举与 WorkOrderStatusTab 枚举
    - 创建 `com.heihu.workorder.enums.TimeRange` 枚举（TODAY, YESTERDAY, THIS_MONTH），提供 `toInterval(LocalDateTime now)` 方法返回时间区间
    - 创建 `com.heihu.workorder.enums.WorkOrderStatusTab` 枚举（NOT_STARTED, IN_PROGRESS, DUE_WITHIN_3_DAYS, OVERDUE_UNFINISHED, OVERDUE_FINISHED_THIS_MONTH, FINISHED_TODAY, FINISHED_THIS_MONTH）
    - _Requirements: 5.1, 5.2, 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11_

  - [x] 1.3 新增后端 DTO 类
    - 创建 `ProcessCardItemResponse` 与 `ProcessCardListResponse`
    - 创建 `WorkOrderProgressItemResponse` 与 `WorkOrderProgressListResponse`
    - 创建 `PerformanceResponse`
    - 创建 `RecentReportItemResponse` 与 `RecentReportListResponse`
    - _Requirements: 4.1-4.9, 8.1, 9.1-9.13, 10.2-10.3_

- [x] 2. 后端服务层实现
  - [x] 2.1 实现 HomeDashboardService - 工序卡片数据查询
    - 创建 `HomeDashboardService` 类
    - 实现 `listProcessCards(Long factoryId, TimeRange range, boolean includeNotStarted)` 方法
    - 按 `range` 过滤工单，展开 `task_data_json` 按工序聚合统计 taskCount、plannedQuantity、goodQuantity、defectQuantity
    - 当 `includeNotStarted=false` 时排除未开始的任务
    - _Requirements: 5.5, 5.6, 5.7_

  - [x] 2.2 实现 HomeDashboardService - 工单进度报表查询
    - 实现 `listWorkOrders(Long factoryId, WorkOrderStatusTab tab, LocalDateTime now)` 方法
    - 按 7 种状态标签过滤工单，返回最多 500 行
    - 使用 JPA `@Query` 自定义 SQL 实现各状态过滤条件
    - _Requirements: 7.5, 7.6, 7.7, 7.8, 7.9, 7.10, 7.11_

  - [x] 2.3 实现 HomeDashboardService - 绩效指标查询
    - 实现 `getPerformance(Long factoryId, TimeRange range, LocalDateTime now)` 方法
    - 计算 6 项指标：计划数、完成数量、工单数量、不良品率、平均工单生产周期、超期未完成工单
    - 不良品率分母为 0 时返回 0.00，> 100 不截断
    - 平均生产周期无符合记录时返回 0.0
    - _Requirements: 9.5, 9.6, 9.7, 9.8, 9.9, 9.10, 9.11, 9.12, 9.13_

  - [x] 2.4 实现 HomeDashboardService - 实时播报查询
    - 实现 `listRecentReports(Long factoryId, LocalDateTime since, int limit)` 方法
    - 按 `createdAt` 倒序返回报工记录，支持 `since` 参数用于增量拉取
    - _Requirements: 10.2, 10.5_

  - [ ]* 2.5 编写 HomeDashboardService 单元测试
    - 测试 TimeRange 时间窗口计算的边界情况
    - 测试各状态标签过滤逻辑
    - 测试不良品率分母为 0 的场景
    - 测试进度百分比计算
    - _Requirements: 5.5-5.7, 7.5-7.11, 9.5-9.13_

  - [ ]* 2.6 编写 completedAt 一致性属性测试
    - **Property 5: completedAt 一致性**
    - **Validates: Requirements 9.11, 9.12**
    - 使用 jqwik 验证：status=COMPLETED 时 completedAt 非空，status≠COMPLETED 时 completedAt 为空

- [x] 3. 后端控制器层实现
  - [x] 3.1 实现 HomeDashboardController
    - 创建 `HomeDashboardController` 类，注入 `HomeDashboardService` 与 `TokenService`
    - 实现 `GET /api/home-dashboard/process-cards` 接口
    - 实现 `GET /api/home-dashboard/work-orders` 接口
    - 实现 `GET /api/home-dashboard/performance` 接口
    - 实现 `GET /api/home-dashboard/reports/recent` 接口
    - 所有接口调用 `tokenService.requireAdmin(authorization)` 校验权限
    - 参数不合法返回 400，token 无效返回 401，非 ADMIN 返回 403
    - _Requirements: 12.1, 12.2, 12.3_

  - [ ]* 3.2 编写 HomeDashboardController 集成测试
    - 使用 Spring Boot Test + H2 测试端到端流程
    - 测试权限校验（401/403）
    - 测试参数校验（400）
    - 测试正常数据返回
    - _Requirements: 12.1, 12.2, 12.3_

- [x] 4. Checkpoint - 后端实现验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. 前端 API 层与类型定义
  - [x] 5.1 扩展前端 API 类型与函数
    - 在 `api.ts` 中新增 `HomeRange`、`WorkOrderStatusTab` 类型定义
    - 新增 `ProcessCardItem`、`ProcessCardListResponse`、`WorkOrderProgressItem`、`WorkOrderProgressListResponse`、`PerformanceResponse`、`RecentReportItem`、`RecentReportListResponse` 接口
    - 新增 `ApiError` 类（含 `status` 字段）
    - 实现 `fetchHomeProcessCards`、`fetchHomeWorkOrders`、`fetchHomePerformance`、`fetchHomeRecentReports` 函数
    - 修改 `request<T>` 工具函数在 HTTP 错误时抛出 `ApiError`
    - _Requirements: 12.1, 12.2, 12.3_

- [x] 6. 前端主页面与路由集成
  - [x] 6.1 创建 HomeDashboardPage.vue 主组件
    - 创建 `frontend/src/views/HomeDashboardPage.vue`
    - 实现顶部栏（左侧"主页"文字，右侧用户头像与姓名）
    - 实现响应式布局：≥1280px 时工序卡片区顶部一行，工单报表左下，绩效+播报右下；<1280px 时纵向堆叠
    - 使用浅色背景、白色卡片底色
    - 提供 `provide('homeRange', range)` 供子组件共享时间范围
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 11.1, 11.2, 11.3_

  - [x] 6.2 集成路由与侧边栏导航
    - 在侧边栏顶部新增"主页"导航项，仅 `role=ADMIN` 时渲染
    - 登录成功后（用户名密码/手机号两条链路）若 `role=ADMIN` 则默认设置 `activeMainMenu='home'`
    - 非 ADMIN 用户不渲染 Home_Dashboard
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 6.3 实现 401/403 全局错误处理
    - 任一接口返回 401 时调用 `useAuth().logout()` 清除 token 并跳转登录页
    - 任一接口返回 403 时隐藏所有数据区块，尝试显示"无权限访问主页"提示
    - _Requirements: 12.1, 12.2_

- [x] 7. 前端工序任务卡片区
  - [x] 7.1 实现 ProcessTaskSection.vue
    - 创建组件，显示标题"工序任务"、设置按钮、"包含未开始生产任务"复选框（默认未勾选）、刷新按钮、时间范围切换标签（今天/昨天/本月，默认今天）
    - 维护 `range`、`includeNotStarted`、`cards`、`loading`、`errorMessage` 本地状态
    - 首次加载以 `range=today`、`includeNotStarted=false` 请求数据
    - 切换时间范围/复选框/点击刷新时重新请求
    - 请求失败时保留上次成功数据并显示错误提示；首次失败仅显示提示
    - 横向排列卡片，溢出时提供横向滚动
    - 通过 `provide/inject` 将 `range` 共享给 PerformanceSection
    - _Requirements: 3.1-3.7, 5.1-5.4, 5.8, 5.9_

  - [x] 7.2 实现 ProcessTaskCard.vue 单卡片组件
    - 左上角工序名称标签（底色取自配置颜色）
    - 主体大号数字（生产任务数）+ 下方文字"生产任务数"
    - 右侧三行小指标：计划数、良品数、不良品数
    - 底部进度文字与百分比（`round(良品数/计划数*100)`，计划数为 0 时显示 0%）
    - 进度条宽度按百分比填充，≥100% 时限定为 100%
    - _Requirements: 4.1-4.9_

  - [x] 7.3 实现 ProcessCardSettingsModal.vue 设置弹窗
    - 列出全部工序，每行含勾选项、工序名称、颜色选择控件
    - 支持拖拽或上下移动按钮调整顺序
    - 保存时通过 `PUT /api/user/preferences/home_dashboard.process_cards` 持久化
    - 保存后触发卡片区重新渲染
    - _Requirements: 6.1-6.8_

  - [x] 7.4 实现用户偏好加载逻辑
    - 使用 `useUserPreference('home_dashboard.process_cards')` composable
    - 偏好不存在时按工序创建时间升序取前 4 个，分配蓝灰黄绿预设颜色
    - 偏好中的 processId 在工序列表中不存在时跳过
    - _Requirements: 6.6, 6.7, 6.8_

- [x] 8. 前端工单进度报表区
  - [x] 8.1 实现 WorkOrderProgressSection.vue
    - 显示标题"工单进度报表"
    - 7 个状态切换标签，默认选中"未开始"
    - 切换标签时重新请求对应工单列表
    - 表格列：序号、工单编号、产品编号、产品名称、产品规格、状态、操作
    - 列表头排序箭头，点击在升序/降序间切换
    - 序号从 1 递增
    - "操作"列显示"查看"链接，点击跳转工单详情页
    - 空数据时显示"暂无数据"占位
    - 内容溢出时提供滚动条
    - _Requirements: 7.1-7.4, 8.1-8.8_

- [x] 9. 前端绩效指标区与实时播报区
  - [x] 9.1 实现 PerformanceSection.vue
    - 显示标题"绩效指标"
    - 2 列 3 行网格展示 6 项指标
    - "完成数量"数值绿色，"不良品率"数值红色
    - 响应注入的 `homeRange`，范围变化时重新请求
    - _Requirements: 9.1-9.4, 9.14_

  - [x] 9.2 实现 LiveBroadcastSection.vue
    - 显示标题"生产实时播报"
    - 按 `createdAt` 倒序展示最近 20 条报工记录
    - 每条记录：圆点图标、时间戳（YYYY-MM-DD HH:mm）、"{reporterName} {processName}"、"工号: {workOrderCode}" 链接
    - 点击工号链接跳转工单详情页
    - 挂载时启动 30 秒轮询，增量追加新记录到列表顶部
    - 列表超过 20 条时丢弃旧记录
    - 页面离开时停止轮询
    - 初次加载为空时显示"暂无播报"
    - _Requirements: 10.1-10.8_

- [x] 10. Checkpoint - 前端实现验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. 集成联调与最终验证
  - [x] 11.1 前后端联调与数据验证
    - 确保前端四个区块正确调用后端接口并渲染数据
    - 验证时间范围切换联动（工序卡片与绩效指标同步）
    - 验证工单状态标签切换与表格排序
    - 验证实时播报轮询与增量追加
    - 验证设置弹窗保存与卡片重新渲染
    - _Requirements: 1.1-1.5, 3.1-3.7, 5.1-5.9, 7.1-7.11, 9.14, 10.5-10.7_

  - [ ]* 11.2 编写前端组件测试
    - 使用 Vitest + Vue Test Utils 测试各 Section 组件
    - 测试错误状态处理（401/403/网络错误）
    - 测试时间范围切换触发数据刷新
    - 测试轮询启动与停止
    - _Requirements: 5.8, 5.9, 10.5, 10.6, 12.1, 12.2_

- [x] 12. Final checkpoint - 全部验证通过
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- The backend uses Java (Spring Boot + JPA) and the frontend uses TypeScript (Vue 3)
- All time calculations use factory local timezone (UTC+8) with half-open intervals
- The existing `UserPreferenceController` is reused for process card settings persistence

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3", "2.4"] },
    { "id": 2, "tasks": ["2.5", "2.6", "3.1"] },
    { "id": 3, "tasks": ["3.2", "5.1"] },
    { "id": 4, "tasks": ["6.1", "6.2", "6.3"] },
    { "id": 5, "tasks": ["7.1", "7.2", "7.3", "7.4", "8.1"] },
    { "id": 6, "tasks": ["9.1", "9.2"] },
    { "id": 7, "tasks": ["11.1", "11.2"] }
  ]
}
```
