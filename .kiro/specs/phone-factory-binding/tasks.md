# Implementation Plan: Phone Factory Binding

## Overview

实现手机号首次登录绑定工厂的完整流程。按照数据层→服务层→控制层→前端的顺序逐步实现，确保每一步都可验证。后端使用 Java Spring Boot + JPA，前端使用 Vue 3。属性测试使用 jqwik（后端）和 fast-check（前端）。

## Tasks

- [x] 1. 数据层：新增 AuthorizationCode 实体和 Repository
  - [x] 1.1 创建 AuthorizationCode 实体类
    - 在 `com.heihu.workorder.entity` 包下创建 `AuthorizationCode` 实体
    - 包含字段：id, code(8位唯一), factory(ManyToOne), createdAt, expiresAt, consumed, consumedAt, version(乐观锁)
    - 使用 `@Version` 注解实现乐观锁
    - _Requirements: 3.1, 3.3, 3.4, 4.2_

  - [x] 1.2 创建 AuthorizationCodeRepository 接口
    - 在 `com.heihu.workorder.repository` 包下创建 Repository
    - 定义 `findByCode(String code)` 和 `findByFactoryIdOrderByCreatedAtDesc(Long factoryId)` 方法
    - _Requirements: 4.1, 5.3_

  - [x] 1.3 修改 PhoneLoginResponse DTO，增加 bindingRequired 和 phone 字段
    - 在现有 `PhoneLoginResponse` 中增加 `bindingRequired` (boolean) 和 `phone` (String) 字段
    - 确保现有登录流程不受影响（默认 bindingRequired=false）
    - _Requirements: 1.1, 1.6_

  - [x] 1.4 创建绑定流程相关 DTO
    - 创建 `BindFactoryRequest`：phone, factoryCode, authorizationCode（带 @NotBlank 校验）
    - 创建 `BindingResponse`：token, user, defaultPassword
    - 创建 `GenerateAuthCodeRequest`：factoryCode(@NotBlank), expirationMinutes(可选)
    - 创建 `AuthCodeResponse`：code, expiresAt
    - 创建 `AuthCodeListItem`：code, status, createdAt, expiresAt
    - _Requirements: 2.1, 2.7, 2.10, 3.1, 5.3_

- [x] 2. 服务层：修改 PhoneAuthService 首次登录检测
  - [x] 2.1 修改 PhoneAuthService.loginByPassword 方法
    - 当 `findAllByPhone(phone)` 返回空列表时，不再抛出异常
    - 返回 `PhoneLoginResponse` 并设置 `bindingRequired=true`、`phone=请求手机号`
    - 不验证密码，不返回 token 或 user 信息
    - _Requirements: 1.1, 1.3, 1.6_

  - [x] 2.2 修改 PhoneAuthService.loginBySmsCode 方法
    - 当 `findAllByPhone(phone)` 返回空列表时，先验证短信验证码
    - 验证码有效：返回 `bindingRequired=true` 响应
    - 验证码无效：抛出认证错误，不暴露 bindingRequired 状态
    - _Requirements: 1.4, 1.5_

  - [x] 2.3 编写 PhoneAuthService 首次登录检测的属性测试
    - **Property 1: Unregistered phone returns bindingRequired without credential validation**
    - **Property 2: Registered phone proceeds with normal login flow**
    - **Property 3: SMS login with valid code for unregistered phone returns bindingRequired**
    - **Property 4: SMS login with invalid code returns auth error without revealing binding state**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6**

- [x] 3. Checkpoint - 确保首次登录检测逻辑正确
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. 服务层：实现 AuthorizationCodeService
  - [x] 4.1 创建 AuthorizationCodeService 类
    - 实现 `generateCode(String factoryCode, Integer expirationMinutes)` 方法
    - 生成 8 位字母数字随机码，计算过期时间（默认 30 分钟）
    - 验证 expirationMinutes 范围 [1, 1440]，超出范围抛出异常
    - 验证 factoryCode 存在，不存在抛出异常
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 4.2 实现 AuthorizationCodeService.validateAndConsume 方法
    - 验证授权码存在、匹配工厂、未过期、未消费
    - 使用乐观锁标记为已消费（设置 consumed=true, consumedAt=now）
    - 捕获 `OptimisticLockException` 并转换为业务异常
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 4.3 实现 AuthorizationCodeService.listByFactory 方法
    - 查询指定工厂的所有授权码，按创建时间降序排列
    - 计算每个授权码的状态：active（未过期未消费）、expired（已过期）、consumed（已消费）
    - _Requirements: 5.3_

  - [x] 4.4 编写 AuthorizationCodeService 的属性测试
    - **Property 10: Generated auth codes are exactly 8 alphanumeric characters with correct expiration**
    - **Property 11: Out-of-range expiration minutes causes rejection**
    - **Validates: Requirements 3.1, 3.3, 3.4, 3.6**

- [x] 5. 服务层：实现 FactoryBindingService
  - [x] 5.1 创建 FactoryBindingService 类
    - 实现 `bindFactory(String phone, String factoryCode, String authorizationCode)` 方法
    - 使用 `@Transactional` 注解确保原子性
    - 流程：验证必填字段 → 验证工厂存在 → 验证工厂未绑定 → 验证手机号未绑定该工厂 → 验证并消费授权码 → 创建 FactoryUser → 生成默认密码 → 发放 Token
    - 创建 FactoryUser：role=ADMIN, enabled=true, virtualUser=false, username=phone, name=phone, phone=phone
    - 生成 6 位默认密码，bcrypt 加密后存储
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 6.1, 6.3_

  - [x] 5.2 编写 FactoryBindingService 的属性测试
    - **Property 5: Successful binding creates correct FactoryUser and returns token with default password**
    - **Property 6: Auth code factory mismatch causes rejection**
    - **Property 7: Expired auth code causes rejection**
    - **Property 8: Already-bound factory rejects new binding**
    - **Validates: Requirements 2.1, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 6.1**

  - [x] 5.3 编写并发绑定的集成测试
    - **Property 9: Auth code consumption is atomic — only one concurrent request succeeds**
    - 使用多线程模拟并发绑定请求，验证乐观锁行为
    - **Validates: Requirements 4.2, 4.3, 4.5**

- [x] 6. Checkpoint - 确保所有服务层逻辑正确
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. 控制层：实现 FactoryBindingController
  - [x] 7.1 创建 FactoryBindingController
    - 路径：`/api/auth/phone/bind-factory`，POST 方法
    - 接收 `@Valid @RequestBody BindFactoryRequest`，调用 FactoryBindingService
    - 返回 `BindingResponse`
    - _Requirements: 2.1, 2.7_

- [x] 8. 控制层：实现 AuthorizationCodeController 和超级管理员认证
  - [x] 8.1 添加超级管理员 API Key 配置
    - 在 `application.properties` 中添加 `app.super-admin.key` 配置项
    - 在 `application.properties` 中添加 `app.binding.default-password-length=6` 配置项
    - _Requirements: 5.1, 5.2_

  - [x] 8.2 创建 AuthorizationCodeController
    - 路径：`/api/super-admin/auth-codes`
    - POST 方法：生成授权码，通过 `@RequestHeader("X-Super-Admin-Key")` 获取密钥并验证
    - GET 方法：查询授权码列表，同样验证超级管理员密钥
    - 密钥不匹配时返回 401 错误
    - _Requirements: 3.1, 5.1, 5.2, 5.3, 5.4_

  - [x] 8.3 编写超级管理员访问控制的属性测试
    - **Property 12: Non-super-admin requests to auth code endpoints are rejected**
    - **Property 13: Auth code listing returns complete sorted results**
    - **Validates: Requirements 5.1, 5.2, 5.3**

- [x] 9. Checkpoint - 确保后端 API 完整可用
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. 前端：实现绑定流程页面和路由
  - [x] 10.1 创建工厂绑定页面组件
    - 创建 Vue 3 组件，包含工厂代码和授权码输入表单
    - 表单验证：工厂代码和授权码不能为空
    - 调用 `/api/auth/phone/bind-factory` API
    - 绑定成功后显示默认密码并跳转到登录页
    - _Requirements: 2.1, 2.10, 6.1_

  - [x] 10.2 修改登录页面逻辑，处理 bindingRequired 响应
    - 当登录接口返回 `bindingRequired=true` 时，携带手机号跳转到绑定页面
    - 不显示错误提示，直接引导用户进入绑定流程
    - _Requirements: 1.1, 1.6_

  - [x] 10.3 创建超级管理员授权码管理页面
    - 创建授权码生成表单：选择工厂、设置有效期（分钟）
    - 创建授权码列表展示：显示 code、状态、创建时间、过期时间
    - 请求头携带 `X-Super-Admin-Key`
    - _Requirements: 3.1, 5.1, 5.3_

  - [x] 10.4 编写前端绑定逻辑的属性测试（fast-check）
    - 测试表单验证逻辑：任意非空字符串通过验证，空字符串被拒绝
    - 测试 API 错误处理：各种错误响应正确映射到用户提示
    - **Validates: Requirements 2.10**

- [x] 11. 端到端验证：默认密码登录
  - [x] 11.1 编写默认密码登录的集成测试
    - **Property 14: Default password round-trip — binding password works for login**
    - 完整流程：绑定 → 获取默认密码 → 使用默认密码登录 → 验证成功
    - **Validates: Requirements 6.1, 6.2**

- [x] 12. Final checkpoint - 确保所有测试通过
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (jqwik for backend, fast-check for frontend)
- Unit tests validate specific examples and edge cases
- 乐观锁并发测试需要使用 `@SpringBootTest` 配合真实数据库或 H2 内存数据库
- 超级管理员密钥应通过环境变量或配置文件注入，不硬编码

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.3", "1.4"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["2.1", "2.2", "4.1"] },
    { "id": 3, "tasks": ["2.3", "4.2", "4.3"] },
    { "id": 4, "tasks": ["4.4", "5.1"] },
    { "id": 5, "tasks": ["5.2", "5.3", "7.1", "8.1"] },
    { "id": 6, "tasks": ["8.2"] },
    { "id": 7, "tasks": ["8.3", "10.1", "10.2", "10.3"] },
    { "id": 8, "tasks": ["10.4", "11.1"] }
  ]
}
```
