// Minimal ASAR reader: list / cat / extract
// usage: node asar.mjs list <pathPrefix> [depth]
//        node asar.mjs extract <pathPrefix> <outDir>
//        node asar.mjs cat <filePath>
import fs from 'node:fs'
import path from 'node:path'

const ASAR = 'C:/Users/louxu/AppData/Local/Programs/DeepSeek Harness/resources/app.asar'

function openAsar (p) {
  const fd = fs.openSync(p, 'r')
  const head = Buffer.alloc(8)
  fs.readSync(fd, head, 0, 8, 0)
  const headerSize = head.readUInt32LE(4)
  const hb = Buffer.alloc(headerSize)
  fs.readSync(fd, hb, 0, headerSize, 8)
  const jsonLen = hb.readUInt32LE(4)
  const header = JSON.parse(hb.subarray(8, 8 + jsonLen).toString('utf8'))
  const base = 8 + headerSize
  return { fd, header, base }
}

const asar = openAsar(ASAR)

function walk (node, prefix, out, depth, maxDepth) {
  if (depth > maxDepth) return
  for (const [name, child] of Object.entries(node.files || {})) {
    const p = prefix ? prefix + '/' + name : name
    if (child.files) {
      out.push({ type: 'dir', path: p })
      walk(child, p, out, depth + 1, maxDepth)
    } else {
      out.push({ type: 'file', path: p, size: child.size, unpacked: !!child.unpacked })
    }
  }
}

function findNode (p) {
  const parts = p.split('/').filter(Boolean)
  let node = asar.header
  for (const part of parts) {
    if (!node.files || !node.files[part]) return null
    node = node.files[part]
  }
  return node
}

function readFile (p) {
  const node = findNode(p)
  if (!node || node.files) return null
  if (node.unpacked) {
    return fs.readFileSync(ASAR.replace(/app\.asar$/, 'app.asar.unpacked') + '/' + p)
  }
  const buf = Buffer.alloc(node.size)
  fs.readSync(asar.fd, buf, 0, node.size, asar.base + Number(node.offset))
  return buf
}

const [cmd, arg1, arg2] = process.argv.slice(2)
if (cmd === 'list') {
  const out = []
  const prefix = arg1 || ''
  const maxDepth = arg2 ? Number(arg2) : 3
  const node = prefix ? findNode(prefix) : asar.header
  if (!node) { console.error('not found'); process.exit(1) }
  if (node.files) walk(node, prefix, out, 0, maxDepth)
  else out.push({ type: 'file', path: prefix, size: node.size })
  for (const e of out) console.log(e.type === 'dir' ? e.path + '/' : `${e.path}\t${e.size}${e.unpacked ? '\t[unpacked]' : ''}`)
} else if (cmd === 'cat') {
  const b = readFile(arg1)
  if (!b) { console.error('not found'); process.exit(1) }
  process.stdout.write(b)
} else if (cmd === 'extract') {
  const prefix = arg1
  const outDir = arg2
  const node = findNode(prefix)
  if (!node) { console.error('not found'); process.exit(1) }
  const out = []
  if (node.files) {
    walk(node, prefix, out, 0, 99)
  } else {
    out.push({ type: 'file', path: prefix, size: node.size })
  }
  let n = 0
  for (const e of out) {
    if (e.type === 'file') {
      const b = readFile(e.path)
      const dest = path.join(outDir, e.path)
      fs.mkdirSync(path.dirname(dest), { recursive: true })
      fs.writeFileSync(dest, b)
      n++
    }
  }
  console.error(`extracted ${n} files to ${outDir}`)
}
