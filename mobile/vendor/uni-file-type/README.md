# Jimp file-type compatibility bridge

`@jimp/core@0.22.12` uses only `require('file-type').fromBuffer(buffer)` and
awaits its `{ ext, mime } | undefined` result. Its original `file-type@16`
dependency is affected by GHSA-5v7r-6r5c-r473: a zero-sized ASF sub-header can
stall the event loop indefinitely.

This local package provides that one asynchronous CommonJS method. All format
parsing is performed by the unmodified, officially patched `file-type@21.3.4`,
loaded through a cached dynamic import. The `file-type-modern` npm alias only
keeps that modern dependency separate from the legacy dependency-name override;
it is not a copy or a relabeling of the vulnerable parser.

The exact upstream pin also includes the fix for GHSA-j47w-4g3g-c36v, which
limits inflation while probing ZIP-based file types from known-size buffers.

Scope and compatibility:

- Requires Node 20+ for the upstream parser; the mobile toolchain has a stricter
  supported runtime requirement and tests Jimp HTTP reads with native Fetch.
- Accepts the upstream Buffer/Uint8Array/ArrayBuffer inputs and preserves its
  Promise result/rejection. No synchronous parser or `fromFile`, `fromStream`,
  tokenizer, or stream API is emulated because Jimp does not use those APIs.
- PNG/JPEG decoding and `Jimp.read(...).bitmap` remain supplied by official Jimp.
- Revisit and remove this bridge when the pinned uni toolchain can consume a
  Jimp version that directly supports a patched modern file-type package.

The regression in `../../test/http-dependency.test.mjs` exercises the published
malformed ASF fixture in a disposable worker, so even a dependency regression
cannot hang the main test process. It also checks real Jimp decoding and the
compiler-facing QR bitmap contract.
