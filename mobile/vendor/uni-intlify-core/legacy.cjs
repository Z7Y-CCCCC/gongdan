'use strict'

const owns = (value, key) => Object.prototype.hasOwnProperty.call(value, key)
const object = value => value !== null && typeof value === 'object'
const unsafe = new Set(['__proto__', 'constructor', 'prototype'])

// uni's vendored vue-i18n 9.1 runtime still imports this removed core helper.
// Keep its in-place dotted-key conversion, scalar-collision and AST contracts;
// never descend into inherited objects or prototype-control path segments.
exports.handleFlatJsonWith = function handleFlatJsonWith(value, core) {
  if (!object(value) || core.isMessageAST(value)) return value
  for (const key of Object.keys(value)) {
    const parts = key.split('.')
    if (parts.some(part => unsafe.has(part))) throw new Error('Unsafe flat locale key')
    if (parts.length === 1) {
      if (object(value[key])) exports.handleFlatJsonWith(value[key], core)
      continue
    }
    let target = value, collision = false
    for (let index = 0; index < parts.length - 1; index++) {
      const part = parts[index], next = parts[index + 1]
      if (!owns(target, part)) target[part] = Object.create(null)
      if (!object(target[part]) || core.isMessageAST(target[part])
        && (core.AST_NODE_PROPS_KEYS.includes(next) || next === 'cacheKey')) {
        collision = true
        break
      }
      target = target[part]
    }
    if (collision) continue
    const last = parts[parts.length - 1]
    target[last] = value[key]
    delete value[key]
    if (object(target[last])) exports.handleFlatJsonWith(target[last], core)
  }
  return value
}
