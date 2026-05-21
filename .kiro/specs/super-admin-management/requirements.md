# Requirements Document

## Introduction

本功能实现超级管理员账户体系及其管理界面的完整改造。系统将从现有的 API Key 认证方式迁移到基于用户账户的超级管理员认证。超级管理员使用工厂代码 "admin"、用户名 "admin"、密码 "123456" 登录后，可以访问授权码管理界面。该界面扩展为综合管理平台，支持生成工厂代码、生成工厂管理员账号、管理授权码，并可查看和停用所有已生成的工厂代码、工厂管理员账号和授权码。此外，超级管理员可以创建其他具有相同权限的超级管理员账号。

## Glossary

- **Super_Admin**: 超级管理员，使用工厂代码 "admin" 登录的特殊用户，拥有系统级管理权限，可访问授权码管理界面
- **Super_Admin_Factory**: 超级管理员专属工厂，工厂代码固定为 "admin"，用于承载所有超级管理员账户
- **Factory_Code**: 工厂唯一标识代码，由超级管理员生成，用于标识不同的工厂
- **Factory_Admin_Account**: 工厂管理员账号，由超级管理员为特定工厂生成的初始管理员账户
- **Authorization_Code**: 授权码，由超级管理员为特定工厂生成的一次性验证码，用于手机号绑定工厂
- **Management_Panel**: 授权码管理界面，仅超级管理员可见的综合管理页面
- **Auth_Service**: 认证服务，处理用户登录验证和 Token 发放
- **Super_Admin_Service**: 超级管理员服务，处理工厂代码生成、工厂管理员账号生成、授权码生成及停用操作
- **Resource_Status**: 资源状态，表示工厂代码、管理员账号或授权码的当前状态（启用/停用/已过期/已使用）

## Requirements

### Requirement 1: 超级管理员账户初始化

**User Story:** As a 系统运维人员, I want 系统启动时自动创建超级管理员账户, so that 超级管理员可以使用固定凭据登录系统进行管理操作。

#### Acceptance Criteria

1. WHEN the system starts and no Factory with the configured seed factory code (app.seed.factory-code) exists, THE System SHALL create a Factory with code equal to the configured seed factory code and name equal to the configured seed factory name (app.seed.factory-name)
2. WHEN the system starts and no FactoryUser with the configured seed admin username (app.seed.admin-username) exists in the seed Factory, THE System SHALL create a FactoryUser with username equal to the configured admin username, password hash derived from the configured admin password (app.seed.admin-password), role ADMIN, enabled true, and virtualUser false in the seed Factory
3. WHEN the system starts and the seed admin FactoryUser already exists but its role is not ADMIN or enabled is false, THE System SHALL restore the admin's role to ADMIN and enabled to true
4. WHEN the Super_Admin logs in with the configured seed factory code, seed admin username, and seed admin password, THE Auth_Service SHALL authenticate the user and return a valid token within 3 seconds
5. IF a request attempts to create or assign a Factory with a code equal to the configured seed factory code (app.seed.factory-code), THEN THE System SHALL reject the request with an error message indicating the factory code is reserved

### Requirement 2: 超级管理员界面访问控制

**User Story:** As a 超级管理员, I want 只有超级管理员账户能看到授权码管理界面, so that 普通工厂管理员无法访问系统级管理功能。

#### Acceptance Criteria

1. WHEN a user logs in with a factory code of "admin", THE Auth_Service SHALL include a `isSuperAdmin` flag set to true in the login response
2. WHEN a user logs in with a factory code other than "admin", THE Auth_Service SHALL include a `isSuperAdmin` flag set to false in the login response
3. WHILE the `isSuperAdmin` flag is true in the current session, THE Frontend SHALL display the "授权码管理" entry in the left sidebar navigation menu
4. WHILE the `isSuperAdmin` flag is false in the current session, THE Frontend SHALL completely hide the "授权码管理" entry from the left sidebar navigation menu so that non-super-admin users cannot see or access it
5. IF a request to any `/api/super-admin/` endpoint is received without a valid Super_Admin credential header, THEN THE Super_Admin_Service SHALL reject the request with an error indicating authentication failure and SHALL NOT execute the requested operation
6. WHEN the Frontend renders the left sidebar navigation, THE Frontend SHALL evaluate the `isSuperAdmin` flag from the login response to determine whether to include the "授权码管理" menu item in the navigation list
7. IF a user with `isSuperAdmin` set to false navigates directly to the 授权码管理 page URL, THEN THE Frontend SHALL redirect the user to the default dashboard page and SHALL NOT render the 授权码管理 page content

### Requirement 3: 创建工厂代码

**User Story:** As a 超级管理员, I want 在管理界面手动设置并创建新的工厂代码, so that 新工厂可以使用该代码接入系统。

#### Acceptance Criteria

1. WHEN a Super_Admin submits a factory creation request with a factory code (1 to 16 uppercase alphanumeric characters) and a factory name between 1 and 128 characters (inclusive), THE Super_Admin_Service SHALL create a new Factory record with the specified code and name, and return the factory code and factory name in the response
2. IF a Super_Admin submits a factory creation request without a factory code or factory name, THEN THE Super_Admin_Service SHALL return an error indicating the required fields are missing without creating any record
3. IF a Super_Admin submits a factory creation request with a factory code that already exists in the system, THEN THE Super_Admin_Service SHALL return an error indicating the factory code already exists
4. THE Super_Admin_Service SHALL reject any factory creation request with the reserved code "ADMIN" (case-insensitive comparison)
5. IF a Super_Admin submits a factory creation request with a factory name that already exists in the system, THEN THE Super_Admin_Service SHALL return an error indicating the factory name is already in use
6. IF a factory creation request is received without valid Super_Admin credentials, THEN THE Super_Admin_Service SHALL reject the request with an error indicating authentication failure
7. IF a Super_Admin submits a factory creation request with a factory code containing characters other than uppercase letters (A-Z) and digits (0-9), or exceeding 16 characters, THEN THE Super_Admin_Service SHALL return an error indicating the factory code format is invalid

### Requirement 4: 生成工厂管理员账号

**User Story:** As a 超级管理员, I want 为指定工厂生成管理员账号, so that 工厂管理员可以使用该账号登录系统管理工单。

#### Acceptance Criteria

1. WHEN a Super_Admin submits a factory admin account generation request with a valid factory code, admin username (1 to 64 characters), and an optional password, THE Super_Admin_Service SHALL create a new FactoryUser with the specified username, the username as the name field, the password stored as a bcrypt hash (if password is provided use it, otherwise generate a random 6-character password), role ADMIN, enabled true, and virtualUser false in the specified factory, and return the username and the password (provided or generated)
2. WHEN a Super_Admin submits a factory admin account generation request with a factory code that does not exist, THE Super_Admin_Service SHALL return an error indicating the factory code is invalid
3. WHEN a Super_Admin submits a factory admin account generation request with a username that already exists in the specified factory, THE Super_Admin_Service SHALL return an error indicating the username already exists
4. IF a Super_Admin submits a factory admin account generation request without a username or factory code, THEN THE Super_Admin_Service SHALL return an error indicating the required fields are missing
5. WHEN a password is not provided in the request, THE Super_Admin_Service SHALL generate a random password that is exactly 6 characters long and contains only uppercase letters, lowercase letters, and digits (A-Z, a-z, 0-9)
6. WHEN a password is provided in the request, THE Super_Admin_Service SHALL validate that it is at least 6 characters long; if shorter, return an error indicating the password is too short
7. IF a Super_Admin submits a factory admin account generation request with a username that exceeds 64 characters or is empty (whitespace-only), THEN THE Super_Admin_Service SHALL return an error indicating the username is invalid
8. IF a Super_Admin submits a factory admin account generation request for a factory that is currently disabled, THEN THE Super_Admin_Service SHALL return an error indicating the factory is disabled

### Requirement 5: 生成授权码

**User Story:** As a 超级管理员, I want 为指定工厂生成授权码, so that 工厂负责人可以使用该授权码通过手机号绑定工厂。

#### Acceptance Criteria

1. WHEN a Super_Admin submits an authorization code generation request with a valid factory code and expiration minutes between 1 and 1440, THE AuthCode_Service SHALL generate a unique 8-character alphanumeric (uppercase letters A-Z and digits 0-9) Authorization_Code, store it with the factory association and an expiration timestamp calculated as the current server time plus the specified expiration minutes, and return the generated code and expiration time within 2 seconds
2. WHEN a Super_Admin submits an authorization code generation request without specifying expiration minutes, THE AuthCode_Service SHALL use a default expiration of 30 minutes and proceed with code generation using the same rules as criterion 1
3. WHEN a Super_Admin submits an authorization code generation request with a factory code that does not exist, THE AuthCode_Service SHALL return an error indicating the factory code is invalid without generating or storing any code
4. IF a Super_Admin submits an authorization code generation request with expiration minutes less than 1 or greater than 1440, THEN THE AuthCode_Service SHALL return an error indicating the expiration value is out of valid range without generating or storing any code
5. WHEN a Super_Admin generates a new Authorization_Code for a factory that already has one or more unexpired and unconsumed Authorization_Codes, THE AuthCode_Service SHALL generate and store the new code without invalidating the existing active codes, allowing multiple active codes to coexist for the same factory

### Requirement 6: 综合管理列表展示

**User Story:** As a 超级管理员, I want 在管理界面查看所有已生成的工厂代码、工厂管理员账号和授权码及其状态, so that 我可以全面了解系统中所有资源的使用情况。

#### Acceptance Criteria

1. WHEN a Super_Admin requests the management list, THE Super_Admin_Service SHALL return all Factory records (excluding the Super_Admin_Factory with code "admin") with each record including: factory code, factory name, status (enabled/disabled), and creation time, sorted by creation time in descending order
2. WHEN a Super_Admin requests the management list, THE Super_Admin_Service SHALL return all Factory_Admin_Account records (FactoryUsers with role ADMIN, virtualUser false, and not belonging to the Super_Admin_Factory) with each record including: factory code, username, status (enabled/disabled), and creation time, sorted by creation time in descending order
3. WHEN a Super_Admin requests the management list, THE Super_Admin_Service SHALL return all Authorization_Code records with each record including: code value, associated factory code, status (active/expired/consumed/disabled), creation time, and expiration time, sorted by creation time in descending order
4. IF an Authorization_Code has been explicitly disabled by a Super_Admin, THEN THE Super_Admin_Service SHALL calculate its status as "disabled" regardless of consumed state or expiration time
5. IF an Authorization_Code has been used for a binding operation and is not disabled, THEN THE Super_Admin_Service SHALL calculate its status as "consumed" regardless of expiration time
6. IF an Authorization_Code is not consumed, not disabled, and the current server time is at or after the expiration time, THEN THE Super_Admin_Service SHALL calculate its status as "expired"
7. IF an Authorization_Code is not consumed, not disabled, and the current server time is before the expiration time, THEN THE Super_Admin_Service SHALL calculate its status as "active"
8. WHEN a Super_Admin requests the management list and no records exist in any category, THE Super_Admin_Service SHALL return empty lists for each category without returning an error

### Requirement 7: 停用资源

**User Story:** As a 超级管理员, I want 停用工厂代码、工厂管理员账号和授权码, so that 我可以控制系统资源的可用性并处理安全问题。

#### Acceptance Criteria

1. WHEN a Super_Admin submits a disable request for a factory code, THE Super_Admin_Service SHALL set the factory enabled field to false, set the enabled field to false for all FactoryUser accounts in that factory, and mark all Authorization_Codes associated with that factory that are neither consumed nor expired as disabled so they cannot be used for binding
2. WHEN a Super_Admin submits a disable request for a factory admin account, THE Super_Admin_Service SHALL set the FactoryUser enabled field to false for that account
3. WHEN a Super_Admin submits a disable request for an Authorization_Code, THE Super_Admin_Service SHALL mark the code as disabled so it cannot be used for binding (distinct from consumed or expired status)
4. IF a disable or enable request is received without valid Super_Admin credentials, THEN THE Super_Admin_Service SHALL reject the request with an error indicating authentication failure
5. IF a Super_Admin submits a disable request for a resource that does not exist, THEN THE Super_Admin_Service SHALL return an error indicating the resource was not found
6. IF a Super_Admin submits a disable request for the Super_Admin_Factory (the factory configured as the system seed factory), THEN THE Super_Admin_Service SHALL reject the request with an error indicating the super admin factory cannot be disabled
7. WHEN a factory is disabled, THE Auth_Service SHALL reject login attempts for users in that factory with an error indicating the factory has been disabled
8. WHEN a Super_Admin submits an enable request for a previously disabled factory code, THE Super_Admin_Service SHALL set the factory enabled field to true (re-enabling login for users in that factory does not automatically re-enable individual user accounts or authorization codes that were disabled)
9. IF a Super_Admin submits a disable request for a resource that is already disabled, THEN THE Super_Admin_Service SHALL return a success response without modifying the resource (idempotent operation)

### Requirement 8: 创建超级管理员账号

**User Story:** As a 超级管理员, I want 创建其他超级管理员账号, so that 多人可以共同管理系统。

#### Acceptance Criteria

1. WHEN a Super_Admin submits a create super admin request with a username of 3 to 32 alphanumeric characters and a password of 6 to 64 characters, THE Super_Admin_Service SHALL create a new FactoryUser in the Super_Admin_Factory with the specified username, the bcrypt hash of the specified password, role ADMIN, enabled true, and virtualUser false
2. WHEN a Super_Admin submits a create super admin request with a username that already exists in the Super_Admin_Factory, THE Super_Admin_Service SHALL return an error indicating the username already exists without creating any record
3. IF a Super_Admin submits a create super admin request without a username or password, THEN THE Super_Admin_Service SHALL return an error indicating the required fields are missing
4. WHEN a newly created Super_Admin logs in with factory code "admin" and the specified credentials, THE Auth_Service SHALL authenticate the user and return a valid token
5. THE Super_Admin_Service SHALL enforce a minimum password length of 6 characters and a maximum password length of 64 characters for new super admin accounts
6. IF a request to create a super admin is received without valid Super_Admin credentials, THEN THE Super_Admin_Service SHALL reject the request with an error indicating authentication failure
7. IF a Super_Admin submits a create super admin request with a username shorter than 3 characters, longer than 32 characters, or containing non-alphanumeric characters, THEN THE Super_Admin_Service SHALL return an error indicating the username format is invalid

### Requirement 9: 超级管理员认证方式迁移

**User Story:** As a 系统, I want 将超级管理员认证从 API Key 方式迁移到用户账户方式, so that 认证方式统一且更安全。

#### Acceptance Criteria

1. WHEN a request to a super admin API endpoint (authorization code generation or listing) includes a valid Bearer token in the Authorization header and the authenticated user belongs to the Super_Admin_Factory (factory code "admin"), THE System SHALL accept the request and proceed with the requested operation
2. IF a request to a super admin API endpoint includes a Bearer token for a user that does NOT belong to the Super_Admin_Factory (factory code "admin"), THEN THE System SHALL reject the request with an error indicating authentication failure
3. IF a request to a super admin API endpoint is received without a valid Bearer token in the Authorization header (missing, malformed, or expired token), THEN THE System SHALL reject the request with an error indicating authentication failure
4. THE System SHALL no longer accept the `X-Super-Admin-Key` header as a valid authentication method for super admin API endpoints, and any request relying solely on this header without a valid Bearer token SHALL be rejected with an error indicating authentication failure
5. THE System SHALL authenticate the Super_Admin by verifying the Bearer token via TokenService and confirming that the authenticated user's factory has the code "admin"
