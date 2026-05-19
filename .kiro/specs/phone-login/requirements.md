# 需求文档

## 简介

在现有工厂账号登录（工厂编号 + 用户名 + 密码）的基础上，新增手机号登录方式。手机号登录支持两种子方式：手机号 + 密码登录、手机号 + 短信验证码登录。登录界面通过 Tab 切换"手机号登录"和"工厂账号登录"两种模式。

## 术语表

- **Login_Page**: 系统的登录页面，包含"手机号登录"和"工厂账号登录"两个 Tab
- **Phone_Login_Module**: 手机号登录模块，处理手机号 + 密码和手机号 + 验证码两种登录方式
- **SMS_Service**: 短信验证码服务，负责生成、发送和校验短信验证码
- **Auth_Service**: 认证服务，负责验证用户凭证并签发令牌
- **FactoryUser**: 系统中的用户实体，包含手机号（phone）字段

## 需求

### 需求 1：登录方式 Tab 切换

**用户故事：** 作为用户，我希望在登录页面通过 Tab 切换"手机号登录"和"工厂账号登录"，以便选择适合的登录方式。

#### 验收标准

1. THE Login_Page SHALL 展示"手机号登录"和"工厂账号登录"两个 Tab 选项
2. WHEN 用户点击"手机号登录" Tab，THE Login_Page SHALL 显示手机号登录表单并隐藏工厂账号登录表单
3. WHEN 用户点击"工厂账号登录" Tab，THE Login_Page SHALL 显示工厂账号登录表单并隐藏手机号登录表单
4. THE Login_Page SHALL 默认选中"手机号登录" Tab

### 需求 2：手机号 + 密码登录

**用户故事：** 作为用户，我希望使用手机号和密码登录系统，以便无需记忆工厂编号即可快速登录。

#### 验收标准

1. THE Phone_Login_Module SHALL 在手机号登录表单中提供"密码登录"和"验证码登录"两种子方式的切换
2. WHEN 用户选择"密码登录"子方式，THE Phone_Login_Module SHALL 显示手机号输入框和密码输入框
3. WHEN 用户提交有效的手机号和正确的密码，THE Auth_Service SHALL 验证凭证并返回认证令牌和用户信息
4. IF 提交的手机号在系统中不存在，THEN THE Auth_Service SHALL 返回"手机号或密码不正确"的错误提示
5. IF 提交的密码与该手机号对应用户的密码不匹配，THEN THE Auth_Service SHALL 返回"手机号或密码不正确"的错误提示
6. IF 该手机号对应的用户账号已被禁用，THEN THE Auth_Service SHALL 返回"账号已被禁用"的错误提示

### 需求 3：手机号 + 短信验证码登录

**用户故事：** 作为用户，我希望使用手机号和短信验证码登录系统，以便在忘记密码时仍能登录。

#### 验收标准

1. WHEN 用户选择"验证码登录"子方式，THE Phone_Login_Module SHALL 显示手机号输入框、验证码输入框和"获取验证码"按钮
2. WHEN 用户输入有效手机号并点击"获取验证码"按钮，THE SMS_Service SHALL 向该手机号发送一条包含6位数字验证码的短信
3. WHEN 验证码发送成功，THE Phone_Login_Module SHALL 显示60秒倒计时，倒计时期间"获取验证码"按钮不可点击
4. WHEN 倒计时结束，THE Phone_Login_Module SHALL 恢复"获取验证码"按钮为可点击状态
5. WHEN 用户提交有效的手机号和正确的验证码，THE Auth_Service SHALL 验证凭证并返回认证令牌和用户信息
6. IF 提交的验证码与系统发送的验证码不匹配，THEN THE Auth_Service SHALL 返回"验证码错误或已过期"的错误提示
7. IF 验证码已超过有效期（5分钟），THEN THE Auth_Service SHALL 返回"验证码错误或已过期"的错误提示
8. IF 提交的手机号在系统中不存在，THEN THE Auth_Service SHALL 返回"该手机号未注册"的错误提示

### 需求 4：手机号格式校验

**用户故事：** 作为系统，我需要校验用户输入的手机号格式，以便防止无效请求。

#### 验收标准

1. THE Phone_Login_Module SHALL 校验手机号为11位中国大陆手机号格式（以1开头的11位数字）
2. IF 用户输入的手机号格式不合法，THEN THE Phone_Login_Module SHALL 在前端显示"请输入正确的手机号"提示且不发送请求
3. IF 获取验证码接口收到格式不合法的手机号，THEN THE SMS_Service SHALL 返回"手机号格式不正确"的错误提示

### 需求 5：验证码发送频率限制

**用户故事：** 作为系统，我需要限制验证码发送频率，以便防止短信接口被滥用。

#### 验收标准

1. WHILE 同一手机号在60秒内已发送过验证码，THE SMS_Service SHALL 拒绝再次发送并返回"发送过于频繁，请稍后再试"的错误提示
2. WHILE 同一IP地址在1小时内已发送超过10次验证码请求，THE SMS_Service SHALL 拒绝发送并返回"请求过于频繁，请稍后再试"的错误提示

### 需求 6：手机号登录用户匹配

**用户故事：** 作为系统，我需要通过手机号匹配用户，以便正确识别登录用户所属的工厂。

#### 验收标准

1. WHEN 手机号在系统中唯一匹配一个用户，THE Auth_Service SHALL 直接使用该用户完成登录
2. IF 同一手机号关联了多个工厂的用户账号，THEN THE Auth_Service SHALL 返回匹配的工厂列表，要求用户选择目标工厂后再完成登录
3. WHEN 用户从工厂列表中选择一个工厂，THE Auth_Service SHALL 使用该工厂下对应的用户账号完成登录

### 需求 7：登录成功后行为一致性

**用户故事：** 作为用户，我希望手机号登录成功后的体验与工厂账号登录一致。

#### 验收标准

1. WHEN 手机号登录成功，THE Auth_Service SHALL 返回与工厂账号登录相同格式的认证令牌和用户信息
2. WHEN 手机号登录成功，THE Login_Page SHALL 将令牌存储到本地并跳转到系统主页面
