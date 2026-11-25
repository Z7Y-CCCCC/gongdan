# Intlify security-fixed core / legacy uni facade

This is **@gongdan/uni-intlify-core-compat 1.0.0**, not an upstream Intlify release.
The implementation of translation, interpolation escaping, HTML sanitization,
compiler, locale fallback, registry and caching is the actual MIT-licensed
**@intlify/core-base 11.4.23** dependency, published under the transparent npm
alias `intlify-core-modern`. No vulnerable v9 core/shared bundle is renamed,
vendored into this package, or used by the facade.

## Why the facade exists

Pinned uni `3.0.0-4020920240930001` carries a Vue I18n 9.1 runtime which imports
twenty core symbols. Official core v11 still supplies eighteen; its old
`getLocaleChain(context, fallback, locale)` is bridged to the same-signature
`fallbackWithLocaleChain`. Its old `handleFlatJson` gets a small in-place dictionary
adapter preserving dotted keys, scalar collisions and compiled-message ASTs,
but rejecting prototype-control paths instead of mutating inherited objects.
For older external consumers, the v9 `compileToFunction` name delegates to
the real v11 JIT `compile` implementation.

The sixteen shared symbols imported by uni's runtime all exist in official
**@intlify/shared 11.4.23**. Keep that real release and the matching
**@intlify/message-compiler 11.4.23** in the final dependency graph.
`@intlify/vue-devtools 9.14.1` only contains three constant exports; its declared
core/shared edges must also resolve to the fixed core/fixed shared release.
Do not use the unsupported 9.14.5 translator or sanitizer for compatibility.

## Real upstream security fixes

- [GHSA-83rm-6c8r-g728](https://github.com/advisories/GHSA-83rm-6c8r-g728),
  upstream [43391d20](https://github.com/intlify/vue-i18n/commit/43391d20e7c85403cb59bdb21b5d542819f54042):
  neutralize the **srcdoc attribute name itself**. Escaped values are decoded
  before a browser parses the nested document and are not an adequate fix.
- [GHSA-8rc2-428q-7ww3](https://github.com/advisories/GHSA-8rc2-428q-7ww3),
  upstream [d657ce8f](https://github.com/intlify/vue-i18n/commit/d657ce8f4b3b6b198a8f6e3d09e2b57a5d019fb8):
  escape both list and named parameters and create a fresh named object rather
  than modifying caller-owned data or escaping it twice on reuse.

Both fixes first shipped in 11.4.14. Official advisory text explicitly states
that v9/v10 are unsupported and will not receive those patches. This adapter
uses the later fixed 11.4.23 implementation in CJS and ESM; it does not mask the
advisories with a fictional package version.

`PROVENANCE.json` records the verified official registry tarball SHA-512,
dist file hashes, fix commit URLs and downloaded patch hashes.
`UPSTREAM_LICENSE` retains upstream license text; `LICENSE` covers the facade.

## Integration owned by the root task

Use the root manifest dependency `@intlify/core-base: file:vendor/uni-intlify-core`
and its matching `$@intlify/core-base` npm override. Pin official shared and
message-compiler to 11.4.23; leave the constant-only devtools version at 9.14.1.
The aliased modern core keeps its real upstream name/version/provenance.

Run the dedicated Node behavior regressions, the existing mobile suite,
typecheck, H5 and WeChat builds, the browser scenarios, then an official registry
audit. Version strings and a successful build alone do not prove either fix.
Pure DOM/library tests are not a production-device or universal HTML-sanitizer
certification. Runtime compilation remains for trusted message catalogs only.

The [behavior regressions](../../test/security-intlify.test.mjs) verify the
installed official source hashes, CommonJS development/production and ESM
escaping, HTML5-parsed attribute boundaries, immutable/reused named values,
and actual uni consumers. Pinned uni's vendored Vue-I18n intentionally returns
raw string messages literally; its interpolation test uses genuinely
precompiled trusted catalogs rather than enabling another runtime compiler.
Legacy public `t` retains its list-or-named signature, while its actual composer
and the fixed core are also exercised with both parameter sets together.
