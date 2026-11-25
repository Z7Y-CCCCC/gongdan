import { compile, fallbackWithLocaleChain } from 'intlify-core-modern'
import * as modern from 'intlify-core-modern'
import { handleFlatJsonWith } from './legacy.cjs'

export * from 'intlify-core-modern'
export const compileToFunction = compile
export const getLocaleChain = fallbackWithLocaleChain
export const handleFlatJson = value => handleFlatJsonWith(value, modern)
