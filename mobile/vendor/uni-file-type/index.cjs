'use strict'

// Jimp 0.22 only consumes fromBuffer(): Promise<{ ext, mime } | undefined>.
// Keep that interface without copying the vulnerable legacy ASF parser.
let detector

async function fromBuffer(buffer) {
  detector ||= import('file-type-modern')
  const { fileTypeFromBuffer } = await detector
  return fileTypeFromBuffer(buffer)
}

module.exports = Object.freeze({ fromBuffer })
