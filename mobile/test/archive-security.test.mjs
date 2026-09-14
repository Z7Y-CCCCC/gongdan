import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { createRequire } from 'node:module'
import { deflateRawSync } from 'node:zlib'
import { test } from 'node:test'

const require = createRequire(import.meta.url)
const Zip = require('../vendor/uni-zip')
const fixturePrefix = 'gongdan-zip-test-'
const before = 'original innocent content'

function fixture(t) {
  const parent = fs.realpathSync(os.tmpdir())
  const created = fs.mkdtempSync(path.join(parent, fixturePrefix))
  const root = fs.realpathSync(created)
  assert.equal(path.dirname(root), parent)
  assert.ok(path.basename(root).startsWith(fixturePrefix))
  t.after(() => {
    // Delete only the exact mkdtemp result after revalidating it. In particular,
    // never delete a cache/output directory derived from archive input.
    assert.equal(fs.realpathSync(created), root)
    assert.equal(path.dirname(root), parent)
    assert.ok(path.basename(root).startsWith(fixturePrefix))
    fs.rmSync(root, { recursive: true, force: true })
  })
  return root
}
function write(filename, content) {
  fs.mkdirSync(path.dirname(filename), { recursive: true })
  fs.writeFileSync(filename, content, { flag: 'wx' })
}
function crc32(data) {
  let value = 0xffffffff
  for (const byte of data) {
    value ^= byte
    for (let bit = 0; bit < 8; bit++) value = value & 1 ? (value >>> 1) ^ 0xedb88320 : value >>> 1
  }
  return (value ^ 0xffffffff) >>> 0
}
function extra(id, data) {
  const header = Buffer.alloc(4)
  header.writeUInt16LE(id, 0)
  header.writeUInt16LE(data.length, 2)
  return Buffer.concat([header, data])
}

// An independent ZIP32 fixture writer, deliberately permitting malformed
// records. Never use the adapter's name validation to construct attack inputs.
function zip32(records, comment = '') {
  const locals = [], centrals = []
  let offset = 0
  for (const record of records) {
    const name = Buffer.isBuffer(record.name) ? record.name : Buffer.from(record.name)
    const content = Buffer.from(record.data ?? '')
    const method = record.method ?? 0
    const compressed = record.compressed ?? (method === 8 ? deflateRawSync(content) : content)
    const flags = record.flags ?? (0x800 | (record.descriptor ? 8 : 0))
    const checksum = record.crc ?? crc32(content)
    const size = record.size ?? content.length
    const compressedSize = record.compressedSize ?? compressed.length
    const localExtra = record.localExtra ?? record.extra ?? Buffer.alloc(0)
    const centralExtra = record.centralExtra ?? record.extra ?? Buffer.alloc(0)
    const version = record.version ?? 20
    const local = Buffer.alloc(30)
    local.writeUInt32LE(0x04034b50, 0)
    local.writeUInt16LE(version, 4)
    local.writeUInt16LE(flags, 6)
    local.writeUInt16LE(method, 8)
    if (!(flags & 8)) {
      local.writeUInt32LE(checksum, 14)
      local.writeUInt32LE(compressedSize, 18)
      local.writeUInt32LE(size, 22)
    }
    local.writeUInt16LE(name.length, 26)
    local.writeUInt16LE(localExtra.length, 28)
    let descriptor = Buffer.alloc(0)
    if (record.descriptor) {
      const signed = record.descriptor === 'signed'
      descriptor = Buffer.alloc(signed ? 16 : 12)
      if (signed) descriptor.writeUInt32LE(0x08074b50, 0)
      for (const [index, value] of [checksum, compressedSize, size].entries()) descriptor.writeUInt32LE(value, (signed ? 4 : 0) + index * 4)
    }
    const central = Buffer.alloc(46)
    central.writeUInt32LE(0x02014b50, 0)
    central.writeUInt16LE(((record.os ?? 3) << 8) | 20, 4)
    central.writeUInt16LE(version, 6)
    central.writeUInt16LE(flags, 8)
    central.writeUInt16LE(method, 10)
    central.writeUInt32LE(checksum, 16)
    central.writeUInt32LE(compressedSize, 20)
    central.writeUInt32LE(size, 24)
    central.writeUInt16LE(name.length, 28)
    central.writeUInt16LE(centralExtra.length, 30)
    central.writeUInt32LE((record.attributes ?? (name.at(-1) === 47 ? 0x41ed0010 : 0x81a40000)) >>> 0, 38)
    central.writeUInt32LE(offset, 42)
    locals.push(local, name, localExtra, compressed, descriptor)
    centrals.push(central, name, centralExtra)
    offset += local.length + name.length + localExtra.length + compressed.length + descriptor.length
  }
  const localData = Buffer.concat(locals), centralData = Buffer.concat(centrals)
  const commentData = Buffer.from(comment)
  const end = Buffer.alloc(22)
  end.writeUInt32LE(0x06054b50, 0)
  end.writeUInt16LE(records.length, 8)
  end.writeUInt16LE(records.length, 10)
  end.writeUInt32LE(centralData.length, 12)
  end.writeUInt32LE(localData.length, 16)
  end.writeUInt16LE(commentData.length, 20)
  return Buffer.concat([localData, centralData, end, commentData])
}
function centralPositions(buffer) {
  const end = buffer.length - 22
  let position = buffer.readUInt32LE(end + 16)
  const positions = []
  while (position < end) {
    positions.push(position)
    position += 46 + buffer.readUInt16LE(position + 28) + buffer.readUInt16LE(position + 30) + buffer.readUInt16LE(position + 32)
  }
  return positions
}
function untouched(t, buffer) {
  const root = fixture(t)
  const cache = path.join(root, 'cache')
  const innocent = path.join(cache, 'innocent.txt')
  const outside = path.join(root, 'outside.txt')
  write(innocent, before)
  write(outside, before)
  assert.throws(() => new Zip(buffer).extractAllTo(cache, true), { code: 'ERR_UNI_ZIP' })
  assert.equal(fs.readFileSync(innocent, 'utf8'), before)
  assert.equal(fs.readFileSync(outside, 'utf8'), before)
  assert.deepEqual(fs.readdirSync(cache), ['innocent.txt'])
}
function attack(record) {
  return zip32([{ name: 'innocent.txt', data: 'must not be written' }, record])
}

test('uni resolves the explicitly named replacement and executes its real packUploadEncryptUniModules call chain', t => {
  const uniRequire = createRequire(require.resolve('@dcloudio/vite-plugin-uni/package.json'))
  const sharedRequire = createRequire(uniRequire.resolve('@dcloudio/uni-cli-shared/package.json'))
  assert.equal(sharedRequire('adm-zip/package.json').name, '@gongdan/uni-zip-adapter')
  assert.equal(sharedRequire('adm-zip').name, 'UniZipAdapter')
  const { packUploadEncryptUniModules } = sharedRequire('./dist/uni_modules.js')
  const root = fixture(t), input = path.join(root, 'input'), cache = path.join(root, 'cache')
  write(path.join(input, 'uni_modules/demo/package.json'), '{"name":"demo"}')
  write(path.join(input, 'uni_modules/demo/中文目录/说明.txt'), '合法中文内容')
  write(path.join(input, 'uni_modules/demo/utssdk/app-android/index.uts'), 'export const value = 1')
  write(path.join(input, 'uni_modules/demo/README.md'), 'excluded by uni')
  const result = packUploadEncryptUniModules({ demo: undefined }, 'app-android', input, cache)
  assert.deepEqual(result.modules, ['demo'])
  assert.equal(result.zipFile, path.join(cache, 'cloud-compile-plugins.zip'))
  const downloadZip = new (sharedRequire('adm-zip'))(result.zipFile)
  assert.equal(downloadZip.readAsText('uni_modules/demo/中文目录/说明.txt'), '合法中文内容')
  assert.throws(() => downloadZip.readAsText('uni_modules/demo/README.md'), { code: 'ERR_UNI_ZIP' })
  const output = path.join(root, 'download-cache')
  downloadZip.extractAllTo(output, true)
  assert.equal(fs.readFileSync(path.join(output, 'uni_modules/demo/utssdk/app-android/index.uts'), 'utf8'), 'export const value = 1')
  downloadZip.extractAllTo(output, true)
  assert.equal(fs.readFileSync(path.join(output, 'uni_modules/demo/中文目录/说明.txt'), 'utf8'), '合法中文内容')
})

test('fflate-written buffers round-trip empty files, directories, binary bytes and UTF8 paths', t => {
  const root = fixture(t), local = path.join(root, '本地.txt')
  write(local, 'local bytes')
  const zip = new Zip()
  zip.addFile('目录/', Buffer.alloc(0))
  zip.addFile('目录/中文.txt', Buffer.from('完整中文'))
  zip.addFile('binary.dat', Uint8Array.from([0, 255, 1, 254]))
  zip.addFile('empty.txt', Buffer.alloc(0))
  zip.addLocalFile(local, 'nested\\from-windows')
  zip.addLocalFile(local, '.')
  const encoded = zip.toBuffer()
  const parsed = new Zip(encoded)
  assert.equal(parsed.readAsText('目录/中文.txt'), '完整中文')
  assert.equal(parsed.readAsText('nested/from-windows/本地.txt'), 'local bytes')
  assert.equal(parsed.readAsText('本地.txt'), 'local bytes')
  assert.equal(parsed.readAsText('empty.txt'), '')
  const destination = path.join(root, 'out')
  parsed.extractAllTo(destination)
  assert.deepEqual(fs.readFileSync(path.join(destination, 'binary.dat')), Buffer.from([0, 255, 1, 254]))
  assert.deepEqual(fs.readdirSync(destination).filter(name => name.startsWith('.uni-zip-adapter-')), [])
  assert.equal(new Zip(new Zip().toBuffer()).toBuffer().length, 22)
})

test('a captured adm-zip 0.6.0 Chinese cache fixture is readable without loading that library', () => {
  // Generated once with adm-zip 0.6.0 before replacement; this is ZIP data, not
  // copied implementation code or a retained runtime dependency.
  const legacy = Buffer.from('UEsDBBQAAAgIAIQzLF2NiEeFFgAAABQAAAAbAAAAdW5pX21vZHVsZXMvZGVtby/kuK3mlocudHh0y0lNT0yuVEhOTM5IVUjLrCgpLUoFAFBLAQIUChQAAAgIAIQzLF2NiEeFFgAAABQAAAAbAAAAAAAAAAAAAACkgQAAAAB1bmlfbW9kdWxlcy9kZW1vL+S4reaWhy50eHRQSwUGAAAAAAEAAQBJAAAATwAAAAAA', 'base64')
  assert.equal(new Zip(legacy).readAsText('uni_modules/demo/中文.txt'), 'legacy cache fixture')
})

test('independently produced STORE/DEFLATE/descriptors and bounded timestamp/owner metadata are supported', () => {
  const localTimestamp = extra(0x5455, Buffer.from([7, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0]))
  const centralTimestamp = extra(0x5455, Buffer.from([7, 1, 0, 0, 0]))
  const owner = extra(0x7875, Buffer.from([1, 4, 1, 0, 0, 0, 4, 2, 0, 0, 0]))
  const ntfs = Buffer.alloc(32)
  ntfs.writeUInt16LE(1, 4)
  ntfs.writeUInt16LE(24, 6)
  const signatureCrc = Buffer.from('ac0a7ad5', 'hex')
  assert.equal(crc32(signatureCrc), 0x08074b50)
  const encoded = zip32([
    { name: 'store.txt', data: 'store', flags: 0, os: 0, localExtra: localTimestamp, centralExtra: centralTimestamp },
    { name: '目录/deflate.txt', data: 'deflate 中文', method: 8, extra: owner },
    { name: 'signed.txt', data: 'signed', method: 8, descriptor: 'signed', extra: extra(0x000a, ntfs) },
    { name: 'unsigned.txt', data: 'unsigned', method: 8, descriptor: 'unsigned' },
    { name: 'crc-signature-signed.bin', data: signatureCrc, method: 8, descriptor: 'signed' },
    { name: 'crc-signature-unsigned.bin', data: signatureCrc, method: 8, descriptor: 'unsigned' },
    { name: 'zero.txt', data: '', method: 8 }
  ], 'bounded ZIP comment')
  const parsed = new Zip(encoded)
  for (const [name, expected] of [['store.txt', 'store'], ['目录/deflate.txt', 'deflate 中文'], ['signed.txt', 'signed'], ['unsigned.txt', 'unsigned'], ['zero.txt', '']]) assert.equal(parsed.readAsText(name), expected)
  for (const name of ['crc-signature-signed.bin', 'crc-signature-unsigned.bin']) assert.equal(parsed.readAsText(name, 'hex'), signatureCrc.toString('hex'))
})

test('overwrite replaces an ordinary destination with a new inode rather than writing through the old file', t => {
  const root = fixture(t), cache = path.join(root, 'cache'), target = path.join(cache, 'value.txt')
  write(target, before)
  const oldInode = fs.statSync(target, { bigint: true }).ino
  // Windows may deny rename over an open handle. That remains a safe OS error;
  // use the file identity there, and additionally inspect the old POSIX handle.
  const fd = process.platform === 'win32' ? undefined : fs.openSync(target, 'r')
  try {
    new Zip(zip32([{ name: 'value.txt', data: 'replacement' }])).extractAllTo(cache, true)
    assert.equal(fs.readFileSync(target, 'utf8'), 'replacement')
    assert.notEqual(fs.statSync(target, { bigint: true }).ino, oldInode)
    if (fd !== undefined) assert.equal(fs.readFileSync(fd, 'utf8'), before)
  } finally { if (fd !== undefined) fs.closeSync(fd) }
  assert.throws(() => new Zip(zip32([{ name: 'value.txt', data: 'no overwrite' }])).extractAllTo(cache), { code: 'ERR_UNI_ZIP' })
  assert.equal(fs.readFileSync(target, 'utf8'), 'replacement')
})

test('existing directory junction/symlink escape is rejected before any earlier file is written', t => {
  const root = fixture(t), cache = path.join(root, 'cache'), outside = path.join(root, 'outside')
  write(path.join(cache, 'innocent.txt'), before)
  write(path.join(outside, 'sentinel.txt'), before)
  fs.symlinkSync(outside, path.join(cache, 'link'), process.platform === 'win32' ? 'junction' : 'dir')
  assert.equal(fs.lstatSync(path.join(cache, 'link')).isSymbolicLink(), true)
  assert.throws(() => new Zip(attack({ name: 'link/sentinel.txt', data: 'escape attempt' })).extractAllTo(cache, true), { code: 'ERR_UNI_ZIP' })
  assert.equal(fs.readFileSync(path.join(outside, 'sentinel.txt'), 'utf8'), before)
  assert.equal(fs.readFileSync(path.join(cache, 'innocent.txt'), 'utf8'), before)
  assert.deepEqual(fs.readdirSync(cache).sort(), ['innocent.txt', 'link'])
})

test('linked extraction roots and linked destination ancestors are rejected', t => {
  const root = fixture(t), outside = path.join(root, 'outside'), linked = path.join(root, 'linked')
  write(path.join(outside, 'sentinel.txt'), before)
  fs.symlinkSync(outside, linked, process.platform === 'win32' ? 'junction' : 'dir')
  const zip = new Zip(zip32([{ name: 'sentinel.txt', data: 'escape' }]))
  assert.throws(() => zip.extractAllTo(linked, true), { code: 'ERR_UNI_ZIP' })
  assert.throws(() => zip.extractAllTo(path.join(linked, 'new-cache'), true), { code: 'ERR_UNI_ZIP' })
  assert.throws(() => zip.writeZip(path.join(linked, 'sentinel.txt')), { code: 'ERR_UNI_ZIP' })
  assert.equal(fs.readFileSync(path.join(outside, 'sentinel.txt'), 'utf8'), before)
  assert.deepEqual(fs.readdirSync(outside), ['sentinel.txt'])
})

test('hardlinked targets are rejected and neither outside sentinel nor earlier files change', t => {
  const root = fixture(t), cache = path.join(root, 'cache'), outside = path.join(root, 'outside.txt')
  write(path.join(cache, 'innocent.txt'), before)
  write(outside, before)
  fs.linkSync(outside, path.join(cache, 'linked.txt'))
  assert.ok(fs.statSync(outside).nlink > 1)
  const zip = new Zip(attack({ name: 'linked.txt', data: 'escape' }))
  assert.throws(() => zip.extractAllTo(cache, true), { code: 'ERR_UNI_ZIP' })
  assert.throws(() => zip.writeZip(path.join(cache, 'linked.txt')), { code: 'ERR_UNI_ZIP' })
  assert.equal(fs.readFileSync(outside, 'utf8'), before)
  assert.equal(fs.readFileSync(path.join(cache, 'innocent.txt'), 'utf8'), before)
})

test('file symlinks, including dangling symlinks, cannot be replaced through extraction', t => {
  const root = fixture(t), cache = path.join(root, 'cache'), outside = path.join(root, 'outside.txt')
  write(path.join(cache, 'innocent.txt'), before)
  write(outside, before)
  try { fs.symlinkSync(outside, path.join(cache, 'linked.txt'), 'file') }
  catch (error) {
    if (process.platform === 'win32' && error.code === 'EPERM') { t.skip('Windows file-symlink creation requires privilege; junction and hardlink cases still run'); return }
    throw error
  }
  fs.symlinkSync(path.join(root, 'not-present.txt'), path.join(cache, 'dangling.txt'), 'file')
  for (const name of ['linked.txt', 'dangling.txt']) assert.throws(() => new Zip(attack({ name, data: 'escape' })).extractAllTo(cache, true), { code: 'ERR_UNI_ZIP' })
  assert.throws(() => new Zip().addLocalFile(path.join(cache, 'linked.txt')), { code: 'ERR_UNI_ZIP' })
  assert.equal(fs.readFileSync(outside, 'utf8'), before)
  assert.equal(fs.readFileSync(path.join(cache, 'innocent.txt'), 'utf8'), before)
  assert.equal(fs.existsSync(path.join(root, 'not-present.txt')), false)
})

test('unsafe ZIP paths fail before changing any output', t => {
  for (const name of [
    '../outside.txt', 'nested/../../outside.txt', '/outside.txt', '//server/share/file', 'C:/outside.txt',
    'a\\..\\outside.txt', '\\\\server\\share\\file', 'a:stream', 'NUL', 'con.txt', 'NUL .txt', 'COM1/a', 'lpt².txt', 'CONOUT$',
    'dot./file', 'space /file', 'a//file', './file', 'a/./file', 'a/../file', 'nul\0byte', 'bad\nline',
    'e\u0301.txt', 'LONGFI~1.TXT', '.uni-zip-adapter-pretend/file', 'x/'.repeat(Zip.LIMITS.depth) + 'file', 'x'.repeat(256)
  ]) untouched(t, attack({ name, data: 'attack' }))
  untouched(t, attack({ name: Buffer.from([0xff, 0xfe]), data: 'attack' }))
  untouched(t, attack({ name: '中文.txt', data: 'attack', flags: 0 }))
})

test('duplicate, case-folded, and file/directory conflicts fail before output', t => {
  for (const names of [['x', 'x'], ['X', 'x'], ['dir/file', 'DIR/other'], ['a', 'a/file'], ['dir/', 'dir/'], ['ß.txt', 'SS.txt']]) {
    untouched(t, zip32([{ name: 'innocent.txt', data: 'must not change' }, ...names.map(name => ({ name, data: name.endsWith('/') ? '' : 'data' }))]))
  }
})

test('existing sibling case conflicts and file/directory conflicts are preflighted for the whole archive', t => {
  for (const [existing, entryName] of [['Mixed.txt', 'mixed.txt'], ['folder', 'folder/item.txt'], ['folder/item.txt', 'folder']]) {
    const root = fixture(t), cache = path.join(root, 'cache')
    write(path.join(cache, existing), before)
    write(path.join(cache, 'innocent.txt'), before)
    assert.throws(() => new Zip(attack({ name: entryName, data: 'replacement' })).extractAllTo(cache, true), { code: 'ERR_UNI_ZIP' })
    assert.equal(fs.readFileSync(path.join(cache, existing), 'utf8'), before)
    assert.equal(fs.readFileSync(path.join(cache, 'innocent.txt'), 'utf8'), before)
  }
})

test('archive symlinks, device nodes, reparse attributes and inconsistent directory types are rejected', t => {
  for (const record of [
    { name: 'link', data: '../outside.txt', attributes: 0xa1ff0000 },
    { name: 'socket', attributes: 0xc1ff0000 },
    { name: 'device', attributes: 0x21ff0000 },
    { name: 'reparse', attributes: 0x400, os: 0 },
    { name: 'volume', attributes: 0x8, os: 0 },
    { name: 'wrong-file', attributes: 0x41ed0010 },
    { name: 'wrong-directory/', attributes: 0x81a40000 },
    { name: 'directory-with-data/', data: 'hidden contents' }
  ]) untouched(t, attack(record))
})

test('unsupported or malformed extra fields cannot smuggle paths, links or ZIP64 sizes', t => {
  const invalid = [
    extra(1, Buffer.alloc(16)), extra(0x000d, Buffer.from('../outside.txt')), extra(0x7075, Buffer.from('../outside.txt')),
    extra(0xbeef, Buffer.alloc(0)), Buffer.from([0x55]), Buffer.from([0x55, 0x54, 5, 0, 1]),
    extra(0x5455, Buffer.from([8])), extra(0x5455, Buffer.from([7, 0, 0, 0, 0])),
    extra(0x7875, Buffer.from([2, 1, 0, 1, 0])), extra(0x7875, Buffer.from([1, 255, 0, 1, 0])),
    extra(0x000a, Buffer.alloc(31)), extra(0x000a, Buffer.alloc(32)),
    Buffer.concat([extra(0x5455, Buffer.from([0])), extra(0x5455, Buffer.from([0]))])
  ]
  for (const field of invalid) untouched(t, attack({ name: 'extra.txt', data: 'attack', extra: field }))
})

test('CRC errors and local/central disagreement do not partially overwrite earlier entries', t => {
  untouched(t, attack({ name: 'crc.txt', data: 'corrupt', crc: 123 }))
  for (const change of [
    (buffer, central, local) => buffer.writeUInt16LE(0, local + 6),
    (buffer, central, local) => buffer.writeUInt16LE(0, local + 8),
    (buffer, central, local) => buffer.writeUInt16LE(10, local + 4),
    (buffer, central, local) => buffer.writeUInt32LE(1, local + 10),
    (buffer, central, local) => buffer.writeUInt32LE(0, local + 14),
    (buffer, central, local) => buffer.writeUInt32LE(0, local + 18),
    (buffer, central, local) => buffer.writeUInt32LE(0, local + 22),
    (buffer, central, local) => buffer.writeUInt16LE(1, local + 26),
    (buffer, central, local) => { buffer[local + 30] = 0x58 },
    (buffer, central) => buffer.writeUInt32LE(0xffffffff, central + 42),
    (buffer, central) => buffer.writeUInt16LE(1, central + 34),
    buffer => buffer.writeUInt16LE(1, buffer.length - 22 + 4),
    buffer => buffer.writeUInt16LE(3, buffer.length - 22 + 8),
    buffer => buffer.writeUInt16LE(3, buffer.length - 22 + 10)
  ]) {
    const encoded = attack({ name: 'data.txt', data: 'payload', method: 8 })
    const central = centralPositions(encoded)[1]
    change(encoded, central, encoded.readUInt32LE(central + 42))
    untouched(t, encoded)
  }
})

test('bad descriptors, truncated ZIPs, unsupported flags, methods and versions fail closed', t => {
  for (const record of [
    { name: 'encrypted', flags: 0x801 }, { name: 'strong-encryption', flags: 0x840 },
    { name: 'unknown-flag', flags: 0x1000 }, { name: 'bad-method', method: 99 },
    { name: 'zip64', version: 45 }, { name: 'unknown-os', os: 99 },
    { name: 'missing-descriptor', flags: 0x808, method: 8, data: 'data' }
  ]) untouched(t, attack(record))
  for (const descriptor of ['signed', 'unsigned']) {
    const encoded = attack({ name: 'data', data: 'data', method: 8, descriptor })
    const central = centralPositions(encoded)[1]
    const local = encoded.readUInt32LE(central + 42)
    const start = local + 30 + encoded.readUInt16LE(local + 26) + encoded.readUInt32LE(central + 20)
    encoded.writeUInt32LE(123, start + (descriptor === 'signed' ? 4 : 0))
    untouched(t, encoded)
  }
  const good = attack({ name: 'data', data: 'payload', method: 8 })
  untouched(t, good.subarray(0, good.length - 1))
  untouched(t, Buffer.concat([good, Buffer.from('trailing bytes')]))
})

test('prefixed and overlapping local records are rejected even when their metadata is internally consistent', t => {
  const plain = zip32([{ name: 'innocent.txt', data: before }, { name: 'data', data: 'payload' }])
  const prefixLength = 4
  const prefixed = Buffer.concat([Buffer.alloc(prefixLength), plain])
  for (const central of centralPositions(plain)) {
    const at = central + prefixLength
    prefixed.writeUInt32LE(prefixed.readUInt32LE(at + 42) + prefixLength, at + 42)
  }
  const end = prefixed.length - 22
  prefixed.writeUInt32LE(prefixed.readUInt32LE(end + 16) + prefixLength, end + 16)
  untouched(t, prefixed)
  const overlap = Buffer.from(plain)
  const firstCentral = centralPositions(overlap)[0]
  const increased = overlap.readUInt32LE(firstCentral + 20) + 10
  overlap.writeUInt32LE(increased, firstCentral + 20)
  overlap.writeUInt32LE(increased, firstCentral + 24)
  overlap.writeUInt32LE(increased, 18)
  overlap.writeUInt32LE(increased, 22)
  untouched(t, overlap)
})

test('actual DEFLATE output, corrupt streams and trailing compressed bytes are bounded before writes', t => {
  const expanded = deflateRawSync(Buffer.alloc(2 * 1024 * 1024))
  untouched(t, attack({ name: 'bomb', data: 'x', method: 8, compressed: expanded, size: 1 }))
  untouched(t, attack({ name: 'broken', data: 'x', method: 8, compressed: Buffer.from([255, 255, 255]) }))
  untouched(t, attack({ name: 'trailing', data: 'data', method: 8, compressed: Buffer.concat([deflateRawSync(Buffer.from('data')), Buffer.from([0])]) }))
  untouched(t, attack({ name: 'size-lie', data: 'x', method: 8, size: 2 }))
  untouched(t, attack({ name: 'store-size-lie', data: 'x', size: 2 }))
})

test('tiny archives cannot allocate attacker-declared huge entries or aggregate expansion', t => {
  untouched(t, attack({ name: 'declared-4GB', data: '', size: 0xffffffff }))
  untouched(t, attack({ name: 'compressed-4GB', data: '', compressedSize: 0xffffffff }))
  untouched(t, zip32(Array.from({ length: 5 }, (_, i) => ({ name: `large-${i}`, method: 8, data: '', size: Zip.LIMITS.entryBytes }))))
  untouched(t, zip32(Array.from({ length: Zip.LIMITS.entries + 1 }, (_, i) => ({ name: `entry-${i}`, data: '' }))))
  untouched(t, zip32(Array.from({ length: Math.floor(Zip.LIMITS.paths / Zip.LIMITS.depth) + 1 }, (_, i) => ({
    name: `dir-${i}/` + 'part/'.repeat(Zip.LIMITS.depth - 2) + 'file', data: ''
  }))))
  const root = fixture(t), file = path.join(root, 'too-large.zip')
  write(file, '')
  fs.truncateSync(file, Zip.LIMITS.archiveBytes + 1)
  assert.throws(() => new Zip(file), { code: 'ERR_UNI_ZIP' })
})

test('the writer rejects unsafe/duplicate names and unsupported compatibility options explicitly', () => {
  const zip = new Zip()
  assert.throws(() => zip.addFile('../escape', Buffer.from('data')), { code: 'ERR_UNI_ZIP' })
  zip.addFile('safe', Buffer.from('data'))
  assert.throws(() => zip.addFile('SAFE', Buffer.from('data')), { code: 'ERR_UNI_ZIP' })
  assert.throws(() => zip.addFile('safe/child', Buffer.from('data')), { code: 'ERR_UNI_ZIP' })
  assert.throws(() => zip.addFile('bad/', Buffer.from('data')), { code: 'ERR_UNI_ZIP' })
  assert.throws(() => zip.addFile('comment', Buffer.from('data'), 'unsupported'), { code: 'ERR_UNI_ZIP' })
  assert.throws(() => zip.toBuffer(() => {}), { code: 'ERR_UNI_ZIP' })
  assert.throws(() => new Zip(Buffer.alloc(0)), { code: 'ERR_UNI_ZIP' })
  assert.equal(new Zip(zip.toBuffer()).readAsText('safe'), 'data')
})

test('deterministic malformed-header mutations either decode safely or fail with a bounded format error', () => {
  const original = zip32([
    { name: 'one.txt', data: 'first payload', method: 8 },
    { name: '目录/two.txt', data: 'second payload', method: 8, descriptor: 'signed' }
  ])
  let seed = 0x4d595df4
  const next = () => (seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0)
  let rejected = 0
  for (let i = 0; i < 1000; i++) {
    const mutated = Buffer.from(original)
    for (let j = 0, changes = 1 + (next() % 3); j < changes; j++) mutated[next() % mutated.length] ^= 1 << (next() % 8)
    try { new Zip(mutated) }
    catch (error) { assert.equal(error.code, 'ERR_UNI_ZIP'); rejected++ }
  }
  assert.ok(rejected > 900)
})
