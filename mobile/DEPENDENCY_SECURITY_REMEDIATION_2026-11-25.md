# 移动端依赖安全修复（2026-11-25）

## 结果与范围

本轮处理上一轮剩余的 **22 high / 0 moderate** 受影响包节点；这些节点由三个公告沿 uni 依赖图传播，并不是 22 条已证实的应用攻击链。

- 官方 registry 的最终 `npm audit --json`：**0 high / 0 moderate / 0 critical，total 0**，包含开发及构建依赖。
- 普通干净 `npm ci` 成功；移动端完整回归 **217/217**、类型检查、H5 与微信小程序生产构建均成功。
- 最新生产 H5 配合隔离内存 API：**333/333、42 屏、16 组交互**，0 pageerror、0 外部 origin 请求尝试。另实看登录失败、报工深色及现场事件深色截图。
- 公开的扫描结果、实际版本、原始验收文件 SHA256 与范围声明见[脱敏证据](../docs/evidence/mobile-dependency-remediation-2026-11-25.json)。完整日志、截图及运行配置只保留在本地，不上传账号、cookie、数据库或构建产物。

**扫描为零不是整个项目或本地 fork 的安全认证。** npm 只能按公告目录识别已知注册包；私有安全 backport 必须另以真实源码补丁、上游字节追溯与漏洞输入回归证明。本轮没有靠虚构官方版本、删除锁文件、跳过开发依赖或 `npm audit fix --force` 消除告警。

## 1. Vue SSR：真实兼容 backport

[GHSA-g2v6-rqmx-r4w6](https://github.com/advisories/GHSA-g2v6-rqmx-r4w6) 的官方首次修复为 3.5.42 / 3.6.0-rc.6；[官方提交](https://github.com/vuejs/core/commit/a2b40db9a83b36ed9da3a16403cf8f040262d73f) 为 SSR 属性名黑名单增加 CR（U+000D）。

调查时官方 uni `vue3` tag `3.0.0-alpha-5030820261124002` 仍依赖旧 Vue SSR/shared，不能用升级 uni 或错误的 `latest` tag 当作此公告的修复。当前 uni 还使用 Vue 3.4 内部 API，直接混入 3.5 SSR/runtime 并非已验证的兼容方案。

因此保留 uni `3.0.0-4020920240930001`、Vue `3.4.34`，交付两个明确私有且保留 MIT 的源码 backport：

| 消费名 | 实际私有包 | 版本 / 改动 |
| --- | --- | --- |
| `@vue/shared` | `@gongdan/uni-vue-shared` | `3.4.34-gongdan.1`；三种 shared JS 分发仅为同一属性名黑名单增加 CR，其他导出与类型不变 |
| `@vue/server-renderer` | `@gongdan/uni-server-renderer` | `3.4.34-gongdan.1`；两种自带 shared 的 browser 分发实际补 CR（包括压缩生产入口），其余 renderer JS 与官方 3.4.34 字节相同 |

SSR 使用 root 管理的 Vue、compiler-ssr 与 patched shared **peer**，避免 file-link 内部另装未经修补的 3.4 shared；根 override 仅替换旧 3.4 shared 分支。Volar 等工具自身的 3.5 编译器/shared 不降级。包名、版本、许可证、官方 tarball SHA512、原始/修后 SHA256 与维护责任均见[SSR 来源](vendor/uni-server-renderer/PROVENANCE.json)、[shared 来源](vendor/uni-vue-shared/PROVENANCE.json)及各 README。

[SSR 回归](test/security-ssr.test.mjs) **12/12**：从上游真实字节复原旧模块并用 HTML5 parser 证明 CR 注入产生独立 `onfocus`，再验 patched helper、真实 Vue/uni `renderToString`、CJS 开发/生产、ESM bundler、两种 browser 分发、正常/SVG/转义属性及全导出集合。原始 ESM 基线确实加载原始 shared，不用已修 predicate 冒充漏洞基线。

uni-h5-vue 内嵌客户端运行时仍为 **3.4.21**；四种内嵌客户端分发没有本公告的 SSR predicate，SSR 入口转发到上述 patched 包。这里是特定公告的安全 backport，**不是整个移动 Vue/uni 栈已统一升级到 3.5**。

## 2. Intlify：官方 fixed 核心与最小旧 API facade

- [GHSA-83rm-6c8r-g728](https://github.com/advisories/GHSA-83rm-6c8r-g728)：[官方修复](https://github.com/intlify/vue-i18n/commit/43391d20e7c85403cb59bdb21b5d542819f54042)中和 `srcdoc` **属性名本身**，不只转义其值。
- [GHSA-8rc2-428q-7ww3](https://github.com/advisories/GHSA-8rc2-428q-7ww3)：[官方修复](https://github.com/intlify/vue-i18n/commit/d657ce8f4b3b6b198a8f6e3d09e2b57a5d019fb8)同时转义 named 与 list，且不修改调用者的 named 对象。

二者首次修复均为 **11.4.14**，官方明确不再为 v9/v10 backport。本轮实际使用官方 **core-base/shared/message-compiler 11.4.23**，没有保留或改名未修的 v9 translator/sanitizer。

`@intlify/core-base` 的 file 入口是透明私有 `@gongdan/uni-intlify-core-compat 1.0.0`；翻译、转义、HTML 处理、编译和缓存均委托给真实 npm alias `intlify-core-modern = npm:@intlify/core-base@11.4.23`。facade 只桥接旧 uni 需要的 `getLocaleChain`、`compileToFunction` 名称与安全 dotted-key 字典适配；旧 `vue-devtools 9.14.1` 仅有三个常量，其声明的 core/shared 消费也解析到修后核心。

[17 项真实回归](test/security-intlify.test.mjs)覆盖 CJS 开发/生产、ESM、官方 SRI/分发 SHA、named+list、srcdoc/引号的 HTML5 解析、frozen caller 对象复用、正常/复数翻译、flatJson/AST/原型路径、实际 uni importer、composer/legacy 和 uni-i18n 消费。

旧 uni VueI18n 对原始字符串刻意使用 literal compiler，legacy `t` 也不接受 modern joint-options 签名。测试保留这些真实契约，以预编译 catalog 与真实 composer 路径验兼容；**没有为了过测试开启新的运行时模板编译**。来源、许可证及桥接边界见[facade README](vendor/uni-intlify-core/README.md)与[来源记录](vendor/uni-intlify-core/PROVENANCE.json)。

## 3. 可复验门禁与维护

从仓库 `mobile/` 目录执行：

```sh
npm ci --registry=https://registry.npmjs.org
npm test
npm run build
npm run build:mp-weixin
npm audit --audit-level=low --registry=https://registry.npmjs.org
```

[CI](../.github/workflows/ci.yml)为管理端与移动端分别增加全图 npm audit 门禁；完整移动测试包含上述源码/漏洞回归。[包装回归](test/security-dependency-packaging.test.mjs)确认 Git 与 Docker context 只为这两份审核过的 vendor `dist` 开精确例外，生成的应用 dist、node_modules 和 output 仍排除，Docker 在 `npm ci` 前复制 vendor。[Git 字节规则](../.gitattributes)将这三份 vendor 源固定为 `-text`，防止 Windows auto-CRLF 改坏上游/许可证哈希。不可只修本机 node_modules。

浏览器使用[生产 H5 场景](test/security-ui-browser.mjs)及[内存夹具](test/browser-fixture.mjs)，仅监听 `127.0.0.2`，不连接业务数据库。本轮截图前缀为 `dependency-remediation`，不覆盖前轮 final 截图；专用浏览器与本轮夹具在验收后关闭。

以后升级 uni/Vue 时应重新评估并删除不再需要的 backport，而不是永久冻结上游。私有 fork 的公告追踪、差异复核与回归是仓库维护责任；官方 npm audit 不能单独覆盖它们。仍未验生产 TLS/CSP/代理链、微信真机与企业外部服务；Java SCA 未逐项完成，不用 npm 零告警或 JVM 回归替代。GitHub 推送及新 SHA 的 CI 状态另以实际发布结果为准，不引用旧 `6a707ad` 的成功 CI 冒充本轮。
