# @gongdan/uni-zip-adapter

This is a self-maintained, synchronous adapter for the **pinned uni compiler's
build-cache ZIP calls**, not a fork, renamed release, or complete implementation
of `adm-zip`. The npm override deliberately replaces `adm-zip` with this named
local package because its latest upstream release still has the reproduced
GHSA-vwc7-r8mq-g2x9 extraction-through-existing-links vulnerability.

The supported API is `new Zip()`, `new Zip(Buffer | Uint8Array | filename)`,
`addFile(name, bytes)`, `addLocalFile(filename, zipPath?)`, `toBuffer()`,
`writeZip(filename)`, `extractAllTo(directory, overwrite?)`, and
`readAsText(name, encoding?)`. Other adm-zip APIs, options, callbacks, metadata
preservation, and encrypted archives are not provided. The installed uni call
chain and this compatibility subset are covered by
`test/archive-security.test.mjs`.

## Format and resource boundary

- `fflate@0.8.3` writes ZIP containers. Node's maintained native zlib decodes
  DEFLATE with `maxOutputLength`; no upstream adm-zip implementation is copied.
- The reader first validates all ZIP32 metadata, central/local header agreement,
  contiguous non-overlapping local records, names, declared resource budgets,
  and entry types. It then fully decodes and checks size, consumed compressed
  bytes, and CRC **before any destination is changed**.
- Supported inputs are single-disk ZIP32, STORE or DEFLATE, UTF-8 (or unflagged
  ASCII) filenames, regular files/directories, and conventional signed/unsigned
  ZIP32 data descriptors. Archive/file comments are ignored. Creator OS values
  DOS, Unix and NTFS are accepted; entry types are checked regardless of creator.
  Structurally validated extended timestamps (`0x5455`), version-1 UID/GID IDs
  (`0x7875`, 1–8-byte IDs), and NTFS timestamp-only fields (`0x000a`, tag 1) are
  ignored and never applied to files.
- ZIP64, other extra fields (including Unix link records and alternative Unicode
  paths), encryption, other compression methods, self-extracting prefixes,
  trailing garbage, unlisted records and unsupported flags fail closed. This is
  not universal compatibility with every cloud-produced ZIP. A cache using an
  unsupported feature must fail rather than be extracted without validation;
  support additions require new bounded validation and regression fixtures.
- Fixed limits: 64 MiB ZIP input/output; 16 MiB uncompressed and 17 MiB compressed
  per entry; 64 MiB total uncompressed; 4,096 entries; 8,192 total paths including
  implicit directories; 1,024 UTF-8 bytes per name;
  255 bytes per component; 32 path components. These are hard limits on both
  declared and actual inflation, not merely claimed metadata checks. No
  attacker-declared unbounded allocation or unlimited read is used. There is no
  compression-ratio restriction in addition to these absolute limits.

## Filesystem and trust boundary

The extraction/output directory and its ancestors must be a local, exclusively
owned, trusted build cache. Existing symlinks, junctions, non-regular files,
hardlinked destination/source files, and linked ancestors are rejected. All
archive targets are preflighted before directory/file creation. Files are staged
with exclusive creation and restrictive permissions, then replaced by rename;
existing cache file inodes are **never opened for writing**. Archive modes and
timestamps are never restored. A corrupt archive or destination conflict leaves
existing files untouched. Ordinary OS errors during the final series of renames
can leave a partial extraction; the multi-file operation is not transactional.

Names reject traversal, absolute/UNC paths, backslashes, ADS/drive colons,
Windows device names, trailing dots/spaces, control characters, non-NFC Unicode,
duplicates, file/directory conflicts, and case-folded conflicts (including with
existing siblings). Tildes are rejected to avoid Windows short-name aliases.
The `.uni-zip-adapter-` component prefix is reserved for private staging. An
existing ordinary cache file may be overwritten only when requested.

**This does not claim to defeat an active local attacker with the same directory
write permissions or mount-control privileges.** Node's portable synchronous
filesystem APIs cannot atomically resolve every ancestor without a TOCTOU window.
Do not extract into shared/world-writable caches, do not run concurrent cache
writers, and do not use this as a sandbox for hostile local users. Treat the ZIP
bytes as untrusted within this exclusive-cache boundary. Temporary staging
cleanup removes only known flat staging files and never recursively removes a
caller-supplied path.

Maintenance: keep the dependency pinned, retain the adversarial regression suite,
and reconsider using the upstream ZIP package only after a real fix passes the
existing-directory-link reproducer. A clean package audit alone is insufficient.
