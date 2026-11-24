# UReport native HTML locale integration

These three HTML templates are derived from `com.bstek.ureport:ureport2-console:2.2.9`,
copyright Bstek / Gaojie, licensed under the Apache License, Version 2.0.
Upstream: https://github.com/youseries/ureport
License: https://www.apache.org/licenses/LICENSE-2.0

Modifications: each template loads the trusted same-origin
`report-locale-bootstrap.js` after jQuery and before its native bundle. The preview
toolbar also has an ID for narrowly scoped UI translation. Native report markup,
business values, datasource definitions, query parameters, inline initialization,
asset URLs and Velocity directives remain otherwise unchanged.

Original template SHA-256 values (UTF-8 bytes from the 2.2.9 Maven artifact):

- `designer.html`: `2f5099909faea3f80da5c771016e6b9a2d3bd1a70536a726b0a056a4d4eba50f`
- `html-preview.html`: `130c8d7e7f4f041fdefa98286d407afec88a595a56cf9c412cc67e7764a79909`
- `searchform.html`: `df15b44aa45a42c1d7ea08425f7653399772589cda0325069eecd43ba73b0cee`

Keep the frontend public and backend static bootstrap files byte-for-byte equal.
The backend copy supports direct backend access; the frontend copy supports the
normal same-origin reverse proxy. Language changes do not reload an open editor.
Ethan can apply the new language explicitly; dirty native reports require discard
confirmation. The query-form canvas, input values and datasource options are not
translated, because they can contain user-defined business data.
