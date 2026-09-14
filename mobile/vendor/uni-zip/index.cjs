'use strict'

// This is a deliberately small ZIP32 adapter, not a general-purpose adm-zip fork.
const fs = require('node:fs')
const path = require('node:path')
const { inflateRawSync } = require('node:zlib')
const { TextDecoder } = require('node:util')

const MiB = 1024 * 1024
const LIMITS = Object.freeze({
  archiveBytes: 64 * MiB,
  entryBytes: 16 * MiB,
  compressedEntryBytes: 17 * MiB,
  totalBytes: 64 * MiB,
  entries: 4096,
  paths: 8192,
  nameBytes: 1024,
  depth: 32
})
const STAGE_PREFIX = '.uni-zip-adapter-'
const utf8 = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true })
const crcTable = Uint32Array.from({ length: 256 }, (_, n) => {
  for (let bit = 0; bit < 8; bit++) n = n & 1 ? 0xedb88320 ^ (n >>> 1) : n >>> 1
  return n >>> 0
})
function reject(reason) {
  const error = new Error(`uni-zip: ${reason}`)
  error.code = 'ERR_UNI_ZIP'
  throw error
}
function check(condition, reason) { if (!condition) reject(reason) }
function crc32(data) {
  let crc = 0xffffffff
  for (const byte of data) crc = crcTable[(crc ^ byte) & 255] ^ (crc >>> 8)
  return (crc ^ 0xffffffff) >>> 0
}
function fold(name) { return name.normalize('NFC').toUpperCase() }
function safeName(name) {
  check(typeof name === 'string' && name.length > 0 && Buffer.byteLength(name) <= LIMITS.nameBytes, 'invalid entry name length')
  check(Buffer.from(name).toString('utf8') === name && !/[\x00-\x1f\x7f-\x9f<>:"\\|?*~]/u.test(name), 'unsafe entry name')
  const directory = name.endsWith('/')
  const parts = (directory ? name.slice(0, -1) : name).split('/')
  check(parts.length <= LIMITS.depth, 'entry path is too deep')
  for (const part of parts) {
    check(part && part !== '.' && part !== '..' && !/[ .]$/.test(part) && Buffer.byteLength(part) <= 255, 'unsafe path component')
    check(part === part.normalize('NFC') && !part.toLowerCase().startsWith(STAGE_PREFIX), 'ambiguous or reserved path component')
    check(!/^(?:con|prn|aux|nul|conin\$|conout\$|com[1-9¹²³]|lpt[1-9¹²³])(?: *\.| *$)/i.test(part), 'Windows reserved name')
  }
  return { name, directory, parts }
}
function entryTree(entries) {
  const nodes = new Map()
  for (const entry of entries) {
    const { parts, directory } = safeName(entry.name)
    for (let i = 0; i < parts.length; i++) {
      const relative = parts.slice(0, i + 1).join('/')
      const key = fold(relative)
      const isDirectory = i < parts.length - 1 || directory
      const explicit = i === parts.length - 1
      const previous = nodes.get(key)
      if (previous) {
        check(previous.relative === relative && previous.directory === isDirectory, 'case or file/directory path conflict')
        check(!(explicit && previous.explicit), 'duplicate archive entry')
        previous.explicit ||= explicit
      } else {
        check(nodes.size < LIMITS.paths, 'expanded path count limit exceeded')
        nodes.set(key, { relative, directory: isDirectory, explicit, depth: i })
      }
    }
  }
  return [...nodes.values()].sort((a, b) => a.depth - b.depth)
}
function maybeStat(filename) {
  try { return fs.lstatSync(filename) } catch (error) { if (error.code === 'ENOENT') return undefined; throw error }
}
function directoryChain(directory) {
  const chain = []
  for (let current = directory; ; current = path.dirname(current)) {
    chain.push(current)
    if (current === path.dirname(current)) break
  }
  for (const filename of chain.reverse()) {
    const stat = maybeStat(filename)
    check(!stat || (!stat.isSymbolicLink() && stat.isDirectory()), 'linked or non-directory ancestor')
  }
}
function regularFile(stat) {
  check(stat && !stat.isSymbolicLink() && stat.isFile() && stat.nlink === 1, 'linked or non-regular file')
}
function readBounded(filename, limit) {
  check(typeof filename === 'string' && filename.length > 0, 'expected a file path')
  const absolute = path.resolve(filename)
  directoryChain(path.dirname(absolute))
  regularFile(fs.lstatSync(absolute))
  const fd = fs.openSync(absolute, fs.constants.O_RDONLY | (fs.constants.O_NOFOLLOW || 0))
  try {
    const stat = fs.fstatSync(fd)
    regularFile(stat)
    check(stat.size <= limit, 'input file exceeds byte limit')
    const data = Buffer.alloc(stat.size)
    let offset = 0
    while (offset < data.length) {
      const read = fs.readSync(fd, data, offset, data.length - offset, offset)
      check(read > 0, 'input file changed while reading')
      offset += read
    }
    check(fs.fstatSync(fd).size === stat.size, 'input file changed while reading')
    return data
  } finally { fs.closeSync(fd) }
}
function extraFields(buffer, start, length, central = false) {
  // Only known, structurally bounded timestamps/owner IDs are ignored. These
  // cannot supply another path, file type, link target, or resource size.
  const seen = new Set()
  const end = start + length
  while (start < end) {
    check(start + 4 <= end, 'truncated extra field')
    const id = buffer.readUInt16LE(start), size = buffer.readUInt16LE(start + 2)
    start += 4
    check(start + size <= end && !seen.has(id), 'truncated or duplicate extra field')
    seen.add(id)
    const data = buffer.subarray(start, start + size)
    if (id === 0x5455) {
      const flags = data[0]
      const fullSize = 1 + 4 * [1, 2, 4].filter(flag => flags & flag).length
      check(size >= 1 && flags <= 7 && (size === fullSize || (central && size === 1 + 4 * (flags & 1))), 'invalid timestamp extra field')
    } else if (id === 0x7875) {
      const uidSize = data[1], gidSize = data[2 + uidSize]
      check(data[0] === 1 && uidSize >= 1 && uidSize <= 8 && gidSize >= 1 && gidSize <= 8 && size === 3 + uidSize + gidSize, 'invalid UID/GID extra field')
    } else if (id === 0x000a) {
      check(size === 32 && data.readUInt32LE(0) === 0 && data.readUInt16LE(4) === 1 && data.readUInt16LE(6) === 24, 'unsupported NTFS extra field')
    } else reject('unsupported ZIP extra field')
    start += size
  }
}
function decode(buffer) {
  check(buffer.length >= 22 && buffer.length <= LIMITS.archiveBytes, 'invalid archive byte length')
  let end = buffer.length - 22
  const earliest = Math.max(0, end - 0xffff)
  while (end >= earliest && (buffer.readUInt32LE(end) !== 0x06054b50 || end + 22 + buffer.readUInt16LE(end + 20) !== buffer.length)) end--
  check(end >= earliest, 'missing ZIP end record')
  const count = buffer.readUInt16LE(end + 10)
  const centralSize = buffer.readUInt32LE(end + 12)
  const centralOffset = buffer.readUInt32LE(end + 16)
  check(buffer.readUInt16LE(end + 4) === 0 && buffer.readUInt16LE(end + 6) === 0 && buffer.readUInt16LE(end + 8) === count, 'multi-disk ZIP is unsupported')
  check(count <= LIMITS.entries && centralOffset + centralSize === end, 'invalid central directory or entry limit')
  const entries = []
  let cursor = centralOffset, total = 0
  for (let i = 0; i < count; i++) {
    check(cursor + 46 <= end && buffer.readUInt32LE(cursor) === 0x02014b50, 'invalid central entry')
    const version = buffer.readUInt16LE(cursor + 6)
    const flags = buffer.readUInt16LE(cursor + 8)
    const method = buffer.readUInt16LE(cursor + 10)
    const crc = buffer.readUInt32LE(cursor + 16)
    const compressedSize = buffer.readUInt32LE(cursor + 20)
    const size = buffer.readUInt32LE(cursor + 24)
    const nameLength = buffer.readUInt16LE(cursor + 28)
    const extraLength = buffer.readUInt16LE(cursor + 30)
    const commentLength = buffer.readUInt16LE(cursor + 32)
    const attributes = buffer.readUInt32LE(cursor + 38)
    const localOffset = buffer.readUInt32LE(cursor + 42)
    const next = cursor + 46 + nameLength + extraLength + commentLength
    check(next <= end && nameLength <= LIMITS.nameBytes, 'truncated central entry or long name')
    check([0, 3, 10].includes(buffer[cursor + 5]) && version >= 10 && version <= 20, 'unsupported ZIP version or creator')
    check((flags & ~0x080e) === 0 && (method === 0 || method === 8) && (method !== 0 || (flags & 6) === 0), 'unsupported ZIP flags or compression')
    check(buffer.readUInt16LE(cursor + 34) === 0, 'multi-disk entry is unsupported')
    check(size <= LIMITS.entryBytes && compressedSize <= LIMITS.compressedEntryBytes && (total += size) <= LIMITS.totalBytes, 'archive expansion limit exceeded')
    const rawName = buffer.subarray(cursor + 46, cursor + 46 + nameLength)
    check((flags & 0x800) !== 0 || rawName.every(byte => byte < 128), 'non-UTF8 filenames are unsupported')
    let name
    try { name = utf8.decode(rawName) } catch { reject('invalid UTF8 filename') }
    const entry = safeName(name)
    const unixType = (attributes >>> 16) & 0xf000
    check(unixType === 0 || unixType === (entry.directory ? 0x4000 : 0x8000), 'link or special ZIP entry is unsupported')
    check((attributes & 0x0448) === 0 && (!(attributes & 0x10) || entry.directory), 'special or inconsistent DOS file attributes')
    check(!entry.directory || size === 0, 'directory entry contains data')
    extraFields(buffer, cursor + 46 + nameLength, extraLength, true)
    check(localOffset + 30 <= centralOffset && buffer.readUInt32LE(localOffset) === 0x04034b50, 'invalid local header offset')
    check(buffer.readUInt16LE(localOffset + 4) === version && buffer.readUInt16LE(localOffset + 6) === flags && buffer.readUInt16LE(localOffset + 8) === method, 'local and central headers disagree')
    check(buffer.readUInt32LE(localOffset + 10) === buffer.readUInt32LE(cursor + 12), 'local and central timestamps disagree')
    const localNameLength = buffer.readUInt16LE(localOffset + 26)
    const localExtraLength = buffer.readUInt16LE(localOffset + 28)
    const dataStart = localOffset + 30 + localNameLength + localExtraLength
    const dataEnd = dataStart + compressedSize
    check(dataEnd <= centralOffset && localNameLength === nameLength && buffer.subarray(localOffset + 30, localOffset + 30 + localNameLength).equals(rawName), 'local filename or compressed range disagrees')
    extraFields(buffer, localOffset + 30 + localNameLength, localExtraLength)
    const expected = [crc, compressedSize, size]
    const local = [14, 18, 22].map(offset => buffer.readUInt32LE(localOffset + offset))
    let localEnd = dataEnd
    if (flags & 8) {
      check(local.every(value => value === 0) || local.every((value, index) => value === expected[index]), 'invalid streaming local sizes')
      check(dataEnd + 12 <= centralOffset, 'missing data descriptor')
      // An unsigned descriptor's CRC can itself equal the optional signature.
      // Match the complete CRC/size tuple, not just its first four bytes.
      const matches = offset => offset + 12 <= centralOffset && expected.every((value, index) => buffer.readUInt32LE(offset + index * 4) === value)
      const unsigned = matches(dataEnd)
      const signed = buffer.readUInt32LE(dataEnd) === 0x08074b50 && matches(dataEnd + 4)
      check(unsigned !== signed, 'invalid or ambiguous data descriptor')
      localEnd = dataEnd + (signed ? 16 : 12)
    } else check(local.every((value, index) => value === expected[index]), 'local CRC or sizes disagree')
    check(method !== 0 || compressedSize === size, 'stored entry size mismatch')
    entries.push({ ...entry, crc, method, size, dataStart, dataEnd, localOffset, localEnd })
    cursor = next
  }
  check(cursor === end, 'central directory size or count mismatch')
  let contiguous = 0
  for (const entry of [...entries].sort((a, b) => a.localOffset - b.localOffset)) {
    check(entry.localOffset === contiguous, 'overlapping, prefixed, or unlisted local records')
    contiguous = entry.localEnd
  }
  check(contiguous === centralOffset, 'unlisted data before central directory')
  entryTree(entries)
  // All structure and declared budgets are checked before any decompression.
  return entries.map(entry => {
    const compressed = buffer.subarray(entry.dataStart, entry.dataEnd)
    let data
    if (entry.method === 0) data = Buffer.from(compressed)
    else {
      let inflated
      try { inflated = inflateRawSync(compressed, { maxOutputLength: Math.max(1, entry.size), info: true }) }
      catch { reject('invalid or over-expanding DEFLATE stream') }
      check(inflated.engine.bytesWritten === compressed.length, 'trailing compressed data')
      data = inflated.buffer
    }
    check(data.length === entry.size && crc32(data) === entry.crc, 'uncompressed size or CRC mismatch')
    return { name: entry.name, directory: entry.directory, data }
  })
}
function preflight(root, nodes, overwrite) {
  directoryChain(root)
  const directoryNames = new Map()
  for (const node of nodes) {
    const target = path.join(root, ...node.relative.split('/'))
    const parent = path.dirname(target)
    if (!directoryNames.has(parent)) directoryNames.set(parent, maybeStat(parent) ? fs.readdirSync(parent) : [])
    const spelling = path.basename(target)
    check(directoryNames.get(parent).filter(name => fold(name) === fold(spelling)).every(name => name === spelling), 'existing case or Unicode path conflict')
    const stat = maybeStat(target)
    if (!stat) continue
    if (node.directory) check(!stat.isSymbolicLink() && stat.isDirectory(), 'linked or non-directory extraction target')
    else { regularFile(stat); check(overwrite, 'destination exists and overwrite is disabled') }
  }
}
function writeEntries(directory, entries, overwrite) {
  check(typeof directory === 'string' && directory.length > 0, 'expected an output directory')
  const root = path.resolve(directory)
  check(root !== path.parse(root).root && !root.startsWith('\\\\'), 'filesystem-root or UNC output is unsupported')
  const nodes = entryTree(entries)
  preflight(root, nodes, overwrite)
  // This cache must be exclusively owned by the build. These checks are not a
  // substitute for OS sandboxing against concurrent, same-user directory swaps.
  fs.mkdirSync(root, { recursive: true, mode: 0o700 })
  for (const node of nodes) if (node.directory) fs.mkdirSync(path.join(root, ...node.relative.split('/')), { recursive: true, mode: 0o700 })
  const stage = fs.mkdtempSync(path.join(root, STAGE_PREFIX))
  const staged = []
  try {
    for (const entry of entries) {
      if (entry.directory) continue
      const source = path.join(stage, String(staged.length))
      staged.push(source)
      fs.writeFileSync(source, entry.data, { flag: 'wx', mode: 0o600 })
    }
    preflight(root, nodes, overwrite)
    let index = 0
    for (const entry of entries) if (!entry.directory) fs.renameSync(staged[index++], path.join(root, ...entry.name.split('/')))
  } finally {
    // Only our known flat staging files are removed; never recursively delete a
    // caller-provided directory, even after an extraction failure.
    for (const filename of staged) {
      try { fs.unlinkSync(filename) } catch (error) { if (error.code !== 'ENOENT') throw error }
    }
    fs.rmdirSync(stage)
  }
}

class UniZipAdapter {
  #entries = []
  #total = 0
  constructor(input) {
    check(arguments.length <= 1, 'constructor options are unsupported')
    if (input === undefined) return
    if (typeof input === 'string') input = readBounded(input, LIMITS.archiveBytes)
    check(input instanceof Uint8Array && input.byteLength <= LIMITS.archiveBytes, 'expected a bounded ZIP buffer or file')
    this.#entries = decode(Buffer.from(input))
    this.#total = this.#entries.reduce((sum, entry) => sum + entry.data.length, 0)
  }
  addFile(name, data) {
    check(arguments.length === 2 && data instanceof Uint8Array, 'addFile accepts only a name and byte buffer')
    const entry = safeName(name)
    check(this.#entries.length < LIMITS.entries && data.byteLength <= LIMITS.entryBytes && this.#total + data.byteLength <= LIMITS.totalBytes, 'archive input limit exceeded')
    check(!entry.directory || data.byteLength === 0, 'directory entry contains data')
    entryTree([...this.#entries, entry])
    this.#entries.push({ name, directory: entry.directory, data: Buffer.from(data) })
    this.#total += data.byteLength
  }
  addLocalFile(filename, zipPath = '') {
    check(arguments.length <= 2 && typeof filename === 'string' && filename.length > 0 && typeof zipPath === 'string', 'addLocalFile options are unsupported')
    // uni passes path.dirname(path.relative(...)), hence Windows separators and
    // the root-level "." are normalized only for this trusted local-file API.
    const prefix = zipPath === '.' ? '' : zipPath.replaceAll('\\', '/').replace(/\/$/, '')
    const name = (prefix ? `${prefix}/` : '') + path.basename(filename)
    safeName(name)
    this.addFile(name, readBounded(filename, LIMITS.entryBytes))
  }
  toBuffer() {
    check(arguments.length === 0, 'toBuffer options are unsupported')
    const { zipSync } = require('fflate')
    const files = Object.create(null)
    for (const entry of this.#entries) files[entry.name] = entry.data
    const buffer = Buffer.from(zipSync(files, { level: 6, mtime: new Date(1980, 0, 1) }))
    check(buffer.length <= LIMITS.archiveBytes, 'encoded archive exceeds byte limit')
    return buffer
  }
  writeZip(filename) {
    check(arguments.length === 1 && typeof filename === 'string' && filename.length > 0, 'writeZip requires one output path')
    const absolute = path.resolve(filename)
    writeEntries(path.dirname(absolute), [{ name: path.basename(absolute), data: this.toBuffer(), directory: false }], true)
  }
  extractAllTo(directory, overwrite = false) {
    check(arguments.length <= 2 && typeof overwrite === 'boolean', 'extractAllTo supports only directory and overwrite')
    writeEntries(directory, this.#entries, overwrite)
  }
  readAsText(name, encoding = 'utf8') {
    check(arguments.length <= 2, 'readAsText options are unsupported')
    const entry = this.#entries.find(item => item.name === name)
    check(entry, 'entry not found')
    return entry.data.toString(encoding)
  }
}
Object.defineProperty(UniZipAdapter, 'LIMITS', { value: LIMITS })
module.exports = UniZipAdapter
