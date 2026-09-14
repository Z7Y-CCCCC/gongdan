# 依赖修复与验收报告（2026-09-12）

续接原任务 `01a08d65-e478-7aa3-bd4e-80656f8fe956` 中未完成的移动工具链依赖修复。保留已有业务修复，未提交、推送、部署或修改正式数据库。

## 结论

最终独立复验已通过。收尾发现的 Vue 覆盖规则问题已经修复，并按“干净安装 → 测试和全部构建 → 完整实际依赖树与安全审计”的顺序重新验收；下方移动端证据均引用修正后的 `r2` 记录。

网页前端、移动端及 MCP 的完整 npm 审计均为 **0 条已知漏洞**，包含开发依赖。移动端原有 24 条包告警（23 moderate、1 high）及网页端剩余 2 条 moderate 已处理；同一个漏洞的父包传播不重复解释为独立漏洞。

已知依赖告警清零，不等于整个软件无漏洞，也不等于只剩上线按钮。真实 MySQL、容器运行、供应商联调及灾备仍未验收；详见最后一节。

## 1. 已完成的修复

| 编号 | 问题与影响 | 修复及证据 |
| --- | --- | --- |
| D1 | Vite 5 的 Windows 文件访问高危问题、旧 esbuild 开发服务 CORS 告警 | 统一 Vite 6.4.3 / esbuild 0.25.12 / plugin-vue 5.2.4 / vue-jsx 4.2.0 / legacy 6.1.1。实际启动项目开发服务，验证模块、源码映射、JS/Vue 热更新，以及 HTTP/WS Host、token、CORS、文件访问拒绝。 |
| D2 | uni 插件默认关闭文件限制；开发 bootstrap 被错误返回 HTML | [vite.config.ts](../mobile/vite.config.ts) 显式设置 `server.fs.strict: true` 和 `publicDir: 'public'`，保留 loopback 默认监听。没有通过关闭保护来迁就升级。 |
| D3 | adm-zip 0.6.0 可跟随已有目录链接，覆盖解压根之外的文件 | 用明确命名的[受限 ZIP 适配器](../mobile/vendor/uni-zip/README.md)替换旧实现。成熟 fflate 写包、有界原生 zlib 解包，完整验证路径、类型、元数据一致性、CRC 和实际大小，再进行整包目的地预检及暂存/rename。独立旧漏洞 probe 现在 `sentinelPreserved:true`、`vulnerable:false`。 |
| D4 | Jimp 旧图片请求链使用 phin 2，跨源跳转可能携带凭证 | 升至官方 Jimp 0.22.12，真实读图使用受支持 Node 的原生 Fetch。验证端口/主机/子域变化、HTTPS 降级、大小写认证头、Location 递归、回调及超时取消；没有新增自维护网络层。字体依赖链仍有官方 phin 3.7.1，不能把图片测试外推到所有字体功能。 |
| D5 | 升级后暴露的旧 file-type ASF 死循环，以及较早补丁版 ZIP 探测膨胀问题 | [13 行 CommonJS 桥](../mobile/vendor/uni-file-type/index.cjs)只保持异步 `fromBuffer`，实际解析交给未修改的官方 file-type 21.3.4。恶意 ASF/ZIP 输入在可终止 worker 中验证，另以合法 ASF、PNG/JPEG 和二维码作正对照。 |
| D6 | 编译工具 Express 的 qs 残余告警 | 保留 Express 4.22.2 与兼容路由解析器，统一 qs 6.16.0，补括号/逗号数组限制和构造器字段回归。 |
| D7 | 网页测试工具 Vitest/mocker 的路径访问告警 | Vitest/mocker 升至 4.1.11，Vite 固定 6.4.3。统一 peer 解析避免包管理器错误；测试中保留公共根图片 URL，避免 Windows 将 `/factory.png` 误当作无盘符 file URL。原 109 项业务断言全部通过，没有删除测试。 |

独立代码复核还修正了 ZIP 无签名 descriptor 的 CRC 恰等于签名值时的合法包误拒，并收紧开发 WebSocket 负例：必须收到明确的 HTTP 400/403 拒绝，超时或连接失败不能被当作测试通过。测试异常路径也会清理自己创建的服务和临时目录。

## 2. 兼容性与维护边界

- 构建要求 Node.js 22 LTS（22.12+）或 24 LTS；实际本机验收为 Node 22.13.1 / npm 10.9.2，CI 和构建镜像使用 Node 22。未在本机额外验收 Node 24。
- uni 仍为 `3.0.0-4020920240930001`，声明的 `vue` 依赖为 3.4.34。H5 别名引用的 `@dcloudio/uni-h5-vue` 私有运行时实际内嵌 Vue 3.4.21，**本次没有改写该 fork**。Vue 类型/JSX 工具原有的 3.5.34 辅助编译包也保留。两个 Vue helper 的 override 选择 `~3.4.0`，同时包含原 3.4.21 与替换目标 3.4.34、排除 3.5；避免 npm 实际依赖树重载时丢掉覆盖规则。
- 这是本项目验证过的 Vite/uni 组合，不代表 DCloud 官方承诺兼容。H5 保持原来的手动启动方式，没有启用第二套旧 H5 编译流水线。
- ZIP 仅支持已列明的 ZIP32 子集；不支持 ZIP64、未知额外字段、加密或任意云端 ZIP 格式。上限为压缩包 64 MiB、单项解压 16 MiB、总解压 64 MiB、4,096 项、含隐式目录共 8,192 路径。异常包在写入前失败。
- ZIP 缓存必须由当前构建独占；不承诺抵抗同等目录写权限的活跃本地攻击者。多文件最终重命名不是事务，普通磁盘错误可能留下部分解压结果。详见适配器 README。
- Jimp 远程读取改用 Fetch 的 `signal: AbortSignal.timeout(...)` 和 `redirect`；旧 `timeout/core/followRedirects` 不适用，URL 内嵌凭证被拒绝。裸 `Jimp.read(url)` 没有新增总时限。不要用旧 polyfill 替换全局 Fetch。uni 实际用到的 Buffer → bitmap → QR 解码已验证。
- 必须一起保存 `mobile/vendor/`、清单和锁文件。根 file 别名供 overrides 复用，避免嵌套目录中的错误相对路径。Docker 在安装前复制这些源文件，运行镜像仍只复制静态构建产物。
- 依赖审计按公开包名/版本检测已知 advisory，不是对所有包内嵌代码、自维护适配器或未知漏洞的证明。上游 Sass、vue-router、部分 npm 包弃用提示仍可能存在；外部经典 bootstrap 脚本的“不打包”提示也保留，它必须在应用模块前执行，实际浏览器已验证。没有过滤这些输出。

## 3. 执行结果与证据

| 检查 | 结果 | 日志 |
| --- | --- | --- |
| 移动端干净安装 | 普通 `npm ci --include=optional` 通过，无 force/legacy-peer-deps；锁文件未漂移 | [npm-ci.log](../mobile/output/toolchain-verification-2026-09-12-r2/npm-ci.log) |
| 构建后的移动端实际依赖树 | 普通 JSON/文本 `npm ls --all` 均为 0；根任务独立复查也无缺失/invalid | [构建后检查](../mobile/output/toolchain-verification-2026-09-12-r2/post-build-npm-ls-json.log) |
| 移动端最终全套 | 72 tests，0 failures、0 skipped；含 Node runner 统计的嵌套子测 | [npm-test.log](../mobile/output/toolchain-verification-2026-09-12-r2/npm-test.log) |
| 移动端类型检查 | 通过 | [typecheck.log](../mobile/output/toolchain-verification-2026-09-12-r2/typecheck.log) |
| H5 默认路径构建 | 通过 | [build-h5.log](../mobile/output/toolchain-verification-2026-09-12-r2/build-h5.log) |
| H5 `/mobile/` 生产前缀构建 | 通过，最终保留此产物 | [build-h5-mobile-base.log](../mobile/output/toolchain-verification-2026-09-12-r2/build-h5-mobile-base.log) |
| 微信小程序编译 | 通过；不是微信真机验收 | [build-mp-weixin.log](../mobile/output/toolchain-verification-2026-09-12-r2/build-mp-weixin.log) |
| 网页端干净安装 | 通过 | [安装日志](../output/dependency-frontend-install-20260912.log) |
| 网页端业务回归 | 19 文件 / 109 tests 通过 | [测试日志](../output/dependency-frontend-tests-20260912.log) |
| 网页端类型检查及生产构建 | 通过 | [构建日志](../output/dependency-frontend-build-20260912.log) |
| 生产 CSP / Docker 源文件契约 | 11 个配置回归通过，不是实际 Docker 运行 | [配置日志](../output/dependency-plugin-csp-20260912.log) |
| Windows 运维保护 | 26 个纯保护检查通过，未启停正式服务 | [运维日志](../output/dependency-ops-guards-20260912.log) |
| MCP | 7 个只读工具的协议 smoke 再次通过，不是“7 个测试”计数 | [协议日志](../output/dependency-mcp-tests-20260912.log) |

完整审计 JSON：[网页端](../output/dependency-frontend-audit-20260912.json)、[移动端](../output/dependency-mobile-audit-20260912.json)、[MCP](../output/dependency-mcp-audit-20260912.json)。网页与移动端的完整依赖树均以 `npm ls --all` 验证，没有只查少数包来漏掉 peer/override 不一致。

CI 网页/移动端使用 Node 22、执行测试及包含开发依赖的 `npm audit --audit-level=low`，并在构建后追加 `npm ls --all`。安装缓存中的依赖树通过，不能代替生成构建缓存后的实际依赖树检查。现有后端/插件代码未在此续做阶段修改，之前的 **767 项后端结果属于 9 月 11 日验收**，本次没有重新运行后端全套，也没有将旧日志算成本次新结果。

CI YAML 解析、构建后检查顺序与最终 `git diff --check` 均通过。移动端锁文件 SHA-256：`C444E0B7C91E31B58FA36A50F0B15F6BB2A9766FF2FD92C41345A8AE512A6B68`。修正后的验收还确认隐藏安装锁早于 Vite 缓存变更，实际文件系统依赖图检查无错误，而不是再次沿用旧缓存的通过结果。

### 隔离浏览器

使用 Chromium、390×844 视口和本机 `mobile/test/browser-fixture.mjs`；API 仅写内存假数据，不连接正式后端。

- 工厂账号登录 → 我的工单 → 工单详情 → 报工 → 历史记录完整通过。
- 输入报工 3、良品 3、不良品 0，历史记录和统计显示一致。
- 双击提交只产生 **1 次 POST、1 条记录**；[独立计数证据](../output/playwright/dependency-20260912/duplicate-submit-result.json)。
- 页面宽度实测 390，等于视口宽度；没有横向溢出。JavaScript 错误 0，保留 1 条上游 vue-router 深路径导入弃用提示。
- 最终 `/mobile/` 前缀产物另行打开验证：bootstrap 地址为 `/mobile/uni-bootstrap.js`，历史记录仍正常显示；底部导航切回工单后仍保留 `/mobile/` 路径。
- 最后重建的 JS/CSS 仍为 `index-Du4mL1PE.js` / `index-BA_agsmf.css`，与已通过浏览器验收的资源文件名一致。
- 截图：[首页](../output/playwright/dependency-20260912/.playwright-cli/page-2026-09-11T22-47-03-709Z.png)、[报工历史](../output/playwright/dependency-20260912/.playwright-cli/page-2026-09-11T23-02-47-747Z.png)。
- 自行创建的浏览器和隔离假服务均已关闭，端口 54100 不再监听；内存假记录随服务退出释放，正式数据未受影响。

## 4. 仍未完成的工作

以下事项不能记成“除上线外都已结束”：

1. 真实 MySQL 迁移、生产数据规模验证及压力/锁时长检查。
2. 真实 Docker / Nginx 组合容器运行与端到端验收。本机 Docker Linux 引擎管道不可连接；本次没有启动或重配 Docker 环境。
3. 微信真机及实际 AppID、短信、S3 等供应商的证书、签名、权限、回调和配额联调。
4. 真实数据库/附件备份恢复、故障切换、Windows 生产服务启停和灾备演练。
5. Java 8 / Spring Boot 2.7、uni 私有运行时的长期升级，以及 Java 依赖、系统和基础镜像扫描；本次 npm 审计不覆盖这些对象。

原有业务规模上限、插件持久化和定制权限/历史库复核仍按[原验收报告](AUDIT_2026-09-11.md#11-未完成的生产环境验收与剩余风险)执行。继续这些真实环境工作需要对应测试环境和业务确认，不能直接在正式数据上试验。
