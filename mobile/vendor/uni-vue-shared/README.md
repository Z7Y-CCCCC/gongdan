# Vue 3.4 shared compatibility security backport

This **private local fork** is `@gongdan/uni-vue-shared@3.4.34-gongdan.1`, installed
under the `@vue/shared` file alias, not an
official Vue 3.5.42 release. All published JavaScript and declarations were copied
from the official `@vue/shared@3.4.34` npm archive. `UPSTREAM_README.md`, original
copyright headers and the upstream MIT `LICENSE` are retained.

GHSA-g2v6-rqmx-r4w6 permits CR in an SSR attribute name to become HTML whitespace.
Official Vue commit `a2b40db9a83b36ed9da3a16403cf8f040262d73f` adds `\u000d` to
`unsafeAttrCharRE`. That exact change is applied to **all three published shared
JavaScript distributions**. Other functions, exported names and declarations are
unchanged. This is not an HTML/URL sanitizer and does not make arbitrary templates
or trusted event-handler attributes safe.

`PROVENANCE.json` records the official archive URL and verified SHA-512 integrity,
original file SHA-256 manifest, modified file hashes and official fix.

Install directly as `@vue/shared` with
`file:vendor/uni-vue-shared`. The renderer keeps its original `@vue/shared`
import and declares the exact custom-version peer; the root alias and resolution
regression ensure it and the 3.4 compiler use this patched helper. A **3.4-only**
`@vue/shared@~3.4.0` override also protects old uni/compiler consumers; do not replace modern 3.5
compiler shared packages with this fork.

Keep the custom name/version visible. `mobile/test/security-ssr.test.mjs` checks
the complete upstream export sets, declaration hashes and real SSR/HTML5-parser
regression, without executing the attack-shaped attribute value.
