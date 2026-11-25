# Vue 3.4 SSR compatibility security backport

This is **@gongdan/uni-server-renderer@3.4.34-gongdan.1**, a private local
compatibility backport installed under the `@vue/server-renderer` file alias; it is not
the official Vue 3.5.42 security release. The complete published renderer code and
declarations originate from the official `@vue/server-renderer@3.4.34` npm archive.
The original README, copyright headers and MIT `LICENSE` are retained.

## Actual security change

- CJS development/production and ESM-bundler distributions are byte-for-byte
  unchanged. Their original `@vue/shared` import resolves to the local shared
  backport through the pinned root alias and peer contract.
- Both standalone ESM-browser distributions (development and minified production)
  embed their own shared helper. Their blacklists receive the **same `\u000d`
  addition**; changing a dependency alone
  would not fix the embedded copy.
- Other renderer logic, exports, declarations and valid-attribute output stay at
  the 3.4.34 contract. Vue and compiler-ssr remain 3.4.34 peers, and shared is the
  exact 3.4.34-gongdan.1 peer. The root owns all three dependencies so npm does not
  install a separate unpatched compiler/shared tree inside the file package.

`PROVENANCE.json` contains verified upstream archive SHA-512 integrity, every
upstream file SHA-256, narrowly changed distributions' before/after hashes and fix
commit `a2b40db9a83b36ed9da3a16403cf8f040262d73f`.

## Integration, not a false upstream version bump

```json
{
  "devDependencies": {
    "@vue/server-renderer": "file:vendor/uni-server-renderer",
    "@vue/shared": "file:vendor/uni-vue-shared",
    "@vue/compiler-ssr": "3.4.34"
  },
  "overrides": {
    "@vue/server-renderer": "$@vue/server-renderer",
    "@vue/shared@~3.4.0": "$@vue/shared"
  }
}
```

The local version intentionally says `3.4.34-gongdan.1`. Do not present it as an
upstream 3.5.42 upgrade or silence an unresolved advisory with a pretend version.
Do not add an unscoped shared override: modern compiler tools' separate 3.5
shared APIs must remain intact. No local package is published to the npm registry.

`node --test test/security-ssr.test.mjs` uses the real renderer, `renderToString`
and parse5. It reconstructs and hashes exact original published modules for an
offline vulnerable baseline (including the ESM shared dependency), checks both
browser distributions and every CJS/ESM entry, and verifies ordinary data,
escaping, SVG names and exports. The CR attack-shaped input is parsed, not executed.

Existing uni-h5-vue's 3.4.21 runtime is **not** claimed upgraded. Its SSR entry
forwards to `@vue/server-renderer`; its four client distributions do not contain
this SSR blacklist. A client's `createSSRApp` factory name does not enable server
rendering. This protects the installed SSR dependency, not proof of an exploitable
SSR HTTP endpoint in the current static mobile deployment.

---

Original upstream API documentation follows (also preserved in UPSTREAM_README.md).

**Note: as of 3.2.13+, this package is included as a dependency of the main `vue` package and can be accessed as `vue/server-renderer`. This means you no longer need to explicitly install this package and ensure its version match that of `vue`'s. Just use the `vue/server-renderer` deep import instead.**

## Basic API

### `renderToString`

**Signature**

```ts
function renderToString(
  input: App | VNode,
  context?: SSRContext
): Promise<string>
```

**Usage**

```js
const { createSSRApp } = require('vue')
const { renderToString } = require('@vue/server-renderer')

const app = createSSRApp({
  data: () => ({ msg: 'hello' }),
  template: `<div>{{ msg }}</div>`
})

;(async () => {
  const html = await renderToString(app)
  console.log(html)
})()
```

### Handling Teleports

If the rendered app contains teleports, the teleported content will not be part of the rendered string. Instead, they are exposed under the `teleports` property of the ssr context object:

```js
const ctx = {}
const html = await renderToString(app, ctx)

console.log(ctx.teleports) // { '#teleported': 'teleported content' }
```

## Streaming API

### `renderToNodeStream`

Renders input as a [Node.js Readable stream](https://nodejs.org/api/stream.html#stream_class_stream_readable).

**Signature**

```ts
function renderToNodeStream(input: App | VNode, context?: SSRContext): Readable
```

**Usage**

```js
// inside a Node.js http handler
renderToNodeStream(app).pipe(res)
```

**Note:** This method is not supported in the ESM build of `@vue/server-renderer`, which is decoupled from Node.js environments. Use `pipeToNodeWritable` instead.

### `pipeToNodeWritable`

Render and pipe to an existing [Node.js Writable stream](https://nodejs.org/api/stream.html#stream_writable_streams) instance.

**Signature**

```ts
function pipeToNodeWritable(
  input: App | VNode,
  context: SSRContext = {},
  writable: Writable
): void
```

**Usage**

```js
// inside a Node.js http handler
pipeToNodeWritable(app, {}, res)
```

### `renderToWebStream`

Renders input as a [Web ReadableStream](https://developer.mozilla.org/en-US/docs/Web/API/Streams_API).

**Signature**

```ts
function renderToWebStream(
  input: App | VNode,
  context?: SSRContext
): ReadableStream
```

**Usage**

```js
// inside an environment with ReadableStream support
return new Response(renderToWebStream(app))
```

**Note:** in environments that do not expose `ReadableStream` constructor in the global scope, `pipeToWebWritable` should be used instead.

### `pipeToWebWritable`

Render and pipe to an existing [Web WritableStream](https://developer.mozilla.org/en-US/docs/Web/API/WritableStream) instance.

**Signature**

```ts
function pipeToWebWritable(
  input: App | VNode,
  context: SSRContext = {},
  writable: WritableStream
): void
```

**Usage**

This is typically used in combination with [`TransformStream`](https://developer.mozilla.org/en-US/docs/Web/API/TransformStream):

```js
// TransformStream is available in environments such as CloudFlare workers.
// in Node.js, TransformStream needs to be explicitly imported from 'stream/web'
const { readable, writable } = new TransformStream()
pipeToWebWritable(app, {}, writable)

return new Response(readable)
```

### `renderToSimpleStream`

Renders input in streaming mode using a simple readable interface.

**Signature**

```ts
function renderToSimpleStream(
  input: App | VNode,
  context: SSRContext,
  options: SimpleReadable
): SimpleReadable

interface SimpleReadable {
  push(content: string | null): void
  destroy(err: any): void
}
```

**Usage**

```js
let res = ''

renderToSimpleStream(
  app,
  {},
  {
    push(chunk) {
      if (chunk === null) {
        // done
        console(`render complete: ${res}`)
      } else {
        res += chunk
      }
    },
    destroy(err) {
      // error encountered
    }
  }
)
```
