# Requirements Document

## Introduction

本功能实现手机号首次登录时绑定工厂的完整流程。当用户使用手机号登录但系统中无关联工厂记录时，系统不再返回"该手机号未注册"错误，而是引导用户进入绑定工厂界面。用户通过输入工厂代码和授权码完成绑定，绑定成功后成为该工厂的第一个管理员。同时，系统提供授权码生成管理界面，供超级管理员为指定工厂生成带有效期的授权码。

## Glossary

- **Phone_Auth_Service**: 手机号认证服务，处理手机号登录、验证码登录及首次登录绑定工厂的逻辑
- **Binding_Service**: 工厂绑定服务，负责验证授权码并将手机号与工厂建立关联
- **AuthCode_Service**: 授权码服务，负责生成、存储和验证工厂授权码
- **Factory_Code**: 工厂唯一标识代码，用于在绑定流程中定位目标工厂
- **Authorization_Code**: 由超级管理员为特定工厂生成的一次性验证码，用于验证绑定请求的合法性
- **Super_Admin**: 超级管理员（开发者），拥有授权码生成界面的访问权限
- **Factory_Admin**: 工厂管理员，通过绑定流程创建的第一个工厂用户，拥有 ADMIN 角色
- **Binding_Request**: 包含手机号、工厂代码和授权码的绑定请求
- **Expiration_Minutes**: 授权码的有效时长，以分钟为单位，可在生成时配置

## Requirements

### Requirement 1: 手机号首次登录检测

**User Story:** As a 工厂负责人, I want 使用手机号登录时系统自动检测是否需要绑定工厂, so that 我不会收到"未注册"错误而是被引导完成绑定流程。

#### Acceptance Criteria

1. WHEN a phone number is submitted for login and no FactoryUser records exist for that phone number, THE Phone_Auth_Service SHALL return a response with a `bindingRequired` flag set to true and SHALL NOT return an authentication error or validate credentials
2. WHEN a phone number is submitted for login and at least one FactoryUser record exists for that phone number, THE Phone_Auth_Service SHALL proceed with the existing login flow (password validation or SMS verification) without setting the `bindingRequired` flag
3. WHEN the login type is "password" and no FactoryUser records exist for the phone number, THE Phone_Auth_Service SHALL return a response with `bindingRequired` set to true without validating the password, within 2 seconds of receiving the request
4. WHEN the login type is "sms" and no FactoryUser records exist for the phone number, THE Phone_Auth_Service SHALL first verify the SMS code and only return the `bindingRequired` response if the SMS code is valid
5. IF the login type is "sms", no FactoryUser records exist for the phone number, and the SMS code verification fails, THEN THE Phone_Auth_Service SHALL return an authentication error indicating the verification code is incorrect or expired, without revealing the `bindingRequired` state
6. WHEN the Phone_Auth_Service returns a `bindingRequired` response, THE Phone_Auth_Service SHALL include the phone number in the response and SHALL NOT include a token or user information

### Requirement 2: 工厂绑定流程

**User Story:** As a 工厂负责人, I want 通过输入工厂代码和授权码完成绑定, so that 我的手机号能关联到正确的工厂并获得管理员权限。

#### Acceptance Criteria

1. WHEN a Binding_Request is received with a valid Factory_Code and a valid Authorization_Code, THE Binding_Service SHALL create a new FactoryUser record with the ADMIN role, enabled set to true, and virtualUser set to false for the specified factory
2. WHEN a Binding_Request is received with a Factory_Code that does not exist in the system, THE Binding_Service SHALL return an error message indicating the factory code is invalid without processing further validations
3. WHEN a Binding_Request is received with an Authorization_Code that does not match the specified factory, THE Binding_Service SHALL return an error message indicating the authorization code is invalid
4. WHEN a Binding_Request is received with an expired Authorization_Code, THE Binding_Service SHALL return an error message indicating the authorization code has expired
5. WHEN a Binding_Request is successful, THE Binding_Service SHALL set the new FactoryUser username to the phone number from the request
6. WHEN a Binding_Request is successful, THE Binding_Service SHALL set the new FactoryUser name to the phone number from the request and set the phone field to the same phone number
7. WHEN a Binding_Request is successful, THE Binding_Service SHALL issue an authentication token for the newly created FactoryUser and return it in the response
8. WHEN a Binding_Request is received for a factory that already has at least one FactoryUser with virtualUser set to false, THE Binding_Service SHALL return an error message indicating the factory has already been bound
9. IF a Binding_Request is received with a phone number that is already associated with a FactoryUser in the same factory, THEN THE Binding_Service SHALL return an error message indicating the phone number is already bound to this factory
10. IF a Binding_Request is received with an empty or missing Factory_Code or Authorization_Code, THEN THE Binding_Service SHALL return an error message indicating the required fields are missing without querying the database

### Requirement 3: 授权码生成

**User Story:** As a Super_Admin, I want 为指定工厂生成授权码, so that 工厂负责人可以使用该授权码完成绑定。

#### Acceptance Criteria

1. WHEN a Super_Admin requests authorization code generation with a valid Factory_Code and Expiration_Minutes between 1 and 1440, THE AuthCode_Service SHALL generate a unique Authorization_Code, store it with the factory association and expiration timestamp, and return the generated code and its expiration time in the response
2. WHEN a Super_Admin requests authorization code generation with a Factory_Code that does not exist, THE AuthCode_Service SHALL return an error message indicating the factory code is invalid
3. THE AuthCode_Service SHALL generate Authorization_Code values that are exactly 8 alphanumeric characters long
4. WHEN an Authorization_Code is generated, THE AuthCode_Service SHALL calculate the expiration time as the current time plus the specified Expiration_Minutes
5. WHEN a Super_Admin requests authorization code generation without specifying Expiration_Minutes, THE AuthCode_Service SHALL use a default expiration of 30 minutes
6. IF a Super_Admin requests authorization code generation with Expiration_Minutes less than 1 or greater than 1440, THEN THE AuthCode_Service SHALL return an error message indicating the expiration value is out of valid range

### Requirement 4: 授权码验证

**User Story:** As a 系统, I want 在绑定时验证授权码的有效性, so that 只有持有合法授权码的用户才能完成工厂绑定。

#### Acceptance Criteria

1. WHEN the Binding_Service validates an Authorization_Code, THE AuthCode_Service SHALL check that the code exists in storage, that the code is associated with the specified Factory_Code, that the current server time is before the code's expiration timestamp, and that the code has not been marked as consumed
2. WHEN an Authorization_Code passes validation, THE AuthCode_Service SHALL mark the code as consumed before the Binding_Service creates the FactoryUser record, so that any concurrent validation request for the same code will be rejected
3. IF an Authorization_Code has already been consumed, THEN THE AuthCode_Service SHALL reject the validation and return an error indicating the code has been used
4. IF an Authorization_Code does not exist in storage, THEN THE AuthCode_Service SHALL reject the validation and return an error indicating the code is invalid
5. IF two concurrent Binding_Requests submit the same Authorization_Code, THEN THE AuthCode_Service SHALL ensure that only one request succeeds and the other receives an error indicating the code has been used

### Requirement 5: 授权码管理界面访问控制

**User Story:** As a Super_Admin, I want 只有超级管理员能访问授权码生成界面, so that 授权码的生成受到严格控制。

#### Acceptance Criteria

1. IF a request to generate an Authorization_Code is received without valid Super_Admin credentials, THEN THE AuthCode_Service SHALL reject the request with an error indicating authentication failure
2. IF a request to list existing Authorization_Codes is received without valid Super_Admin credentials, THEN THE AuthCode_Service SHALL reject the request with an error indicating authentication failure
3. WHEN a Super_Admin requests the list of Authorization_Codes with a valid Factory_Code, THE AuthCode_Service SHALL return all Authorization_Codes associated with that Factory_Code, each including: the code value, status (active, expired, consumed), creation time, and expiration time, sorted by creation time in descending order
4. IF a Super_Admin requests the list of Authorization_Codes with a Factory_Code that does not exist in the system, THEN THE AuthCode_Service SHALL return an error indicating the factory code is invalid

### Requirement 6: 绑定后密码设置

**User Story:** As a 工厂管理员, I want 绑定成功后设置登录密码, so that 后续可以使用手机号加密码的方式登录。

#### Acceptance Criteria

1. WHEN a Binding_Request is successful, THE Binding_Service SHALL generate a fixed default password of 6 characters, store its bcrypt hash in the new FactoryUser record, and return the default password value in the `defaultPassword` field of the binding response
2. WHEN a Factory_Admin submits a phone number and the default password via the password login flow, THE Phone_Auth_Service SHALL authenticate the user and return an authentication token following the standard login process
3. IF the Binding_Service fails to store the password hash during binding, THEN THE Binding_Service SHALL roll back the entire FactoryUser creation and return an error message indicating the binding failed
