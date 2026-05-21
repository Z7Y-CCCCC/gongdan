# Implementation Plan: Super Admin Management (超级管理员管理)

## Overview

本实现计划将超级管理员管理功能分解为增量式编码任务。从数据库模型变更开始，逐步构建后端服务层、控制器层，最后实现前端界面变更和 API Key 认证移除。每个任务都引用具体的需求条款，确保完整覆盖。

## Tasks

- [x] 1. Database schema and entity model changes
  - [x] 1.1 Add `enabled` field to Factory entity and database migration
    - Add `enabled` boolean field (default true) to `Factory.java` entity
    - Create SQL migration: `ALTER TABLE factories ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE`
    - _Requirements: 7.1, 7.7, 7.8_

  - [x] 1.2 Add `disabled` and `disabledAt` fields to AuthorizationCode entity and database migration
    - Add `disabled` boolean field (default false) and `disabledAt` LocalDateTime field to `AuthorizationCode.java`
    - Create SQL migration: `ALTER TABLE authorization_codes ADD COLUMN disabled BOOLEAN NOT NULL DEFAULT FALSE; ALTER TABLE authorization_codes ADD COLUMN disabled_at DATETIME NULL`
    - _Requirements: 6.4, 7.3_

  - [x] 1.3 Extend AuthenticatedUser with `factoryCode` field
    - Add `factoryCode` String field to `AuthenticatedUser` class
    - Update all constructors and usages to include factoryCode
    - _Requirements: 2.1, 9.5_

  - [x] 1.4 Extend AuthResponse with `isSuperAdmin` field
    - Add `isSuperAdmin` boolean field to `AuthResponse.java`
    - _Requirements: 2.1, 2.2_

- [x] 2. Backend DTOs for Super Admin API
  - [x] 2.1 Create request DTOs
    - Create `CreateFactoryRequest.java` with `factoryCode` and `factoryName` fields and validation annotations
    - Create `CreateFactoryAdminRequest.java` with `factoryCode`, `username`, and optional `password` fields
    - Create `CreateSuperAdminRequest.java` with `username` and `password` fields
    - Create `GenerateAuthCodeRequest.java` with `factoryCode` and optional `expirationMinutes` fields
    - _Requirements: 3.1, 4.1, 5.1, 8.1_

  - [x] 2.2 Create response DTOs
    - Create `FactoryResponse.java` with factoryCode, factoryName, enabled, createdAt
    - Create `FactoryAdminResponse.java` with factoryCode, username, password, enabled, createdAt
    - Create `ManagementListResponse.java` with lists of factories, factoryAdmins, authCodes
    - Create `FactoryListItem.java`, `FactoryAdminListItem.java` for list items
    - Update existing `AuthCodeListItem.java` to include status field with "active"/"expired"/"consumed"/"disabled" values
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 3. TokenService extension and AuthService modifications
  - [x] 3.1 Add `requireSuperAdmin` method to TokenService
    - Implement method that calls `requireUser`, then verifies the user's factoryCode equals "admin"
    - Throw `ApiException(HttpStatus.FORBIDDEN, "需要超级管理员权限")` if not super admin
    - Update `requireUser` to populate `factoryCode` in AuthenticatedUser by querying Factory
    - _Requirements: 2.5, 9.1, 9.2, 9.3, 9.5_

  - [x] 3.2 Modify AuthService login to include `isSuperAdmin` flag and factory disabled check
    - In login flow, check if the user's factory has `enabled=false`; if so, reject with "该工厂已被停用，无法登录"
    - Set `isSuperAdmin = true` in AuthResponse when factory code is "admin", false otherwise
    - _Requirements: 2.1, 2.2, 7.7_

  - [x] 3.3 Write property tests for TokenService and AuthService
    - **Property 2: Non-admin factory login does not grant super admin flag**
    - **Property 3: Super admin endpoint authentication guard**
    - **Property 14: Disabled factory blocks login**
    - **Validates: Requirements 2.2, 2.5, 7.7, 9.1, 9.2, 9.3**

- [x] 4. SuperAdminService implementation
  - [x] 4.1 Create SuperAdminService with factory management methods
    - Implement `createFactory(code, name)` with validation: code format `[A-Z0-9]{1,16}`, reserved code check, uniqueness check for code and name
    - Implement `disableFactory(factoryCode)` with cascading: set factory.enabled=false, disable all users, disable all active auth codes
    - Implement `enableFactory(factoryCode)` that only sets factory.enabled=true without cascading
    - Implement `getManagementList()` returning factories (excluding admin), factory admins, and auth codes sorted by createdAt desc
    - _Requirements: 3.1, 3.3, 3.4, 3.5, 3.7, 6.1, 6.2, 6.3, 7.1, 7.6, 7.8, 7.9_

  - [x] 4.2 Write property tests for factory creation validation
    - **Property 1: Reserved factory code protection**
    - **Property 4: Valid factory creation succeeds**
    - **Property 5: Factory uniqueness constraints**
    - **Property 6: Invalid factory code format rejection**
    - **Validates: Requirements 1.5, 3.1, 3.3, 3.4, 3.5, 3.7**

  - [x] 4.3 Implement factory admin account generation methods
    - Implement `createFactoryAdmin(factoryCode, username, password)` with validation
    - Implement random password generation: 6 chars from `[A-Za-z0-9]` using SecureRandom
    - Implement `disableFactoryAdmin(userId)` and `enableFactoryAdmin(userId)`
    - Validate factory exists, is enabled, username uniqueness within factory, username length 1-64
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

  - [x] 4.4 Write property tests for factory admin generation
    - **Property 7: Generated password format compliance**
    - **Property 8: Factory admin username uniqueness within factory**
    - **Validates: Requirements 4.3, 4.5**

  - [x] 4.5 Implement authorization code generation and disable methods
    - Implement `generateAuthCode(factoryCode, expirationMinutes)` with default 30 min, range validation [1,1440]
    - Generate 8-char code from `[A-Z0-9]` using SecureRandom
    - Implement `disableAuthCode(authCodeId)` setting disabled=true and disabledAt=now
    - Implement status calculation logic with priority: disabled > consumed > expired > active
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 6.4, 6.5, 6.6, 6.7, 7.3_

  - [x] 4.6 Write property tests for authorization code logic
    - **Property 9: Authorization code format and storage**
    - **Property 10: Authorization code generation non-interference**
    - **Property 11: Authorization code status calculation priority**
    - **Validates: Requirements 5.1, 5.5, 6.4, 6.5, 6.6, 6.7**

  - [x] 4.7 Implement super admin creation method
    - Implement `createSuperAdmin(username, password)` with validation: username `[A-Za-z0-9]{3,32}`, password 6-64 chars
    - Create FactoryUser in admin factory with role ADMIN, enabled=true, virtualUser=false
    - _Requirements: 8.1, 8.2, 8.3, 8.5, 8.7_

  - [x] 4.8 Write property tests for super admin creation
    - **Property 17: Super admin creation with valid inputs**
    - **Property 18: Super admin input validation**
    - **Validates: Requirements 8.1, 8.5, 8.7**

- [x] 5. Checkpoint - Backend service layer verification
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. SuperAdminController and cascading disable tests
  - [x] 6.1 Create SuperAdminController with all endpoints
    - Implement all endpoints as defined in design: factory CRUD, factory admin CRUD, auth code generation/listing/disable, super admin creation
    - Use `tokenService.requireSuperAdmin(authorization)` for authentication on all endpoints
    - Map proper HTTP methods and status codes (201 for creation, 200 for queries and updates)
    - _Requirements: 3.1, 3.6, 4.1, 5.1, 7.4, 8.1, 8.6, 9.1_

  - [x] 6.2 Write property tests for cascading disable and enable
    - **Property 12: Management list excludes admin factory and sorts descending**
    - **Property 13: Cascading factory disable**
    - **Property 15: Factory enable does not cascade to child resources**
    - **Property 16: Disable operation idempotence**
    - **Validates: Requirements 6.1, 7.1, 7.8, 7.9**

- [x] 7. Remove API Key authentication
  - [x] 7.1 Remove `X-Super-Admin-Key` authentication support
    - Remove or deprecate the existing `AuthorizationCodeController` that uses API Key auth
    - Remove any `X-Super-Admin-Key` header checking logic from filters or interceptors
    - Remove the `super.admin.key` configuration property usage
    - Ensure all super admin endpoints only accept Bearer token authentication
    - _Requirements: 9.4_

- [x] 8. Checkpoint - Backend complete verification
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Frontend changes
  - [x] 9.1 Update login response handling and state management
    - Store `isSuperAdmin` flag from login response in app state (Pinia store or reactive state)
    - Persist `isSuperAdmin` in sessionStorage/localStorage alongside token
    - _Requirements: 2.1, 2.2_

  - [x] 9.2 Implement sidebar conditional rendering and route guard
    - Conditionally show/hide "授权码管理" menu item based on `isSuperAdmin` flag
    - Add route guard: redirect non-super-admin users to default dashboard when accessing management page URL
    - _Requirements: 2.3, 2.4, 2.6, 2.7_

  - [x] 9.3 Refactor SuperAdminAuthCodesPage to comprehensive management page
    - Implement tabbed interface with three tabs: 工厂管理, 管理员账号, 授权码管理
    - 工厂管理 tab: factory list with create form, disable/enable buttons
    - 管理员账号 tab: factory admin list with create form, disable/enable buttons
    - 授权码管理 tab: auth code list with generate form, disable button
    - Display status badges for each resource (enabled/disabled/active/expired/consumed)
    - Call new `/api/super-admin/` endpoints instead of old API Key-based endpoints
    - _Requirements: 3.1, 4.1, 5.1, 6.1, 6.2, 6.3, 7.1, 7.2, 7.3, 8.1_

  - [x] 9.4 Add super admin creation UI
    - Add "创建超级管理员" section or dialog in management page
    - Form with username and password fields with client-side validation
    - _Requirements: 8.1_

- [x] 10. Integration tests
  - [x] 10.1 Write integration tests for complete super admin flows
    - Test full login flow: admin factory login returns isSuperAdmin=true
    - Test newly created super admin can login successfully
    - Test factory disable cascades and blocks login
    - Test factory enable only re-enables factory, not child resources
    - Test authorization code generation and status transitions
    - _Requirements: 1.4, 7.7, 7.8, 8.4_

- [x] 11. Final checkpoint - Full system verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties defined in the design document (18 properties total)
- Unit tests validate specific examples and edge cases
- The project uses Spring Boot 2.7 (Java) for backend and Vue.js + Vite (TypeScript) for frontend
- Property testing uses jqwik 1.7.4 for backend and fast-check for frontend
- All super admin endpoints use Bearer token authentication via TokenService

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4"] },
    { "id": 1, "tasks": ["2.1", "2.2"] },
    { "id": 2, "tasks": ["3.1", "3.2"] },
    { "id": 3, "tasks": ["3.3", "4.1"] },
    { "id": 4, "tasks": ["4.2", "4.3"] },
    { "id": 5, "tasks": ["4.4", "4.5"] },
    { "id": 6, "tasks": ["4.6", "4.7"] },
    { "id": 7, "tasks": ["4.8", "6.1"] },
    { "id": 8, "tasks": ["6.2", "7.1"] },
    { "id": 9, "tasks": ["9.1", "9.2"] },
    { "id": 10, "tasks": ["9.3", "9.4"] },
    { "id": 11, "tasks": ["10.1"] }
  ]
}
```
