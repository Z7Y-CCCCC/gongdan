'use strict'

const modern = require('intlify-core-modern')
const { handleFlatJsonWith } = require('./legacy.cjs')

// Translation, escaping, sanitization, registries and caches are the actual
// upstream v11 implementation. Never retain or rename vulnerable v9 bundles.
module.exports = {
  ...modern,
  compileToFunction: modern.compile,
  getLocaleChain: modern.fallbackWithLocaleChain,
  handleFlatJson: value => handleFlatJsonWith(value, modern)
}
