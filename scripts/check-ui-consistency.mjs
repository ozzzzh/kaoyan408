/**
 * UI 一致性检查：找出硬编码色值、缺失的语义变量、以及样式不一致处。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const SRC = 'client/src'

function walk(dir) {
  const out = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) out.push(...walk(full))
    else if (/\.(vue|ts|css)$/.test(name)) out.push(full)
  }
  return out
}

const files = walk(SRC)

console.log('=== 1) 仍在用硬编码色值的文件（应只剩有意保留的科目色/状态色）===')
const KNOWN_OK = [
  '#2d68ef', '#7c5cf0', '#f0a020', '#12a594', // 四科配色
  '#0f7a4d', '#b23c3c', '#8a6d1f',            // 对/错/一般 状态色
  '#eef2fa', '#c6e4c9', '#8ecf96', '#4fae5f', '#1f7a33', // 热力图级别
  '#fff', '#ffffff',
]
for (const file of files) {
  const text = readFileSync(file, 'utf8')
  const hexes = [...text.matchAll(/#[0-9a-fA-F]{6}\b/g)].map((m) => m[0].toLowerCase())
  const unexpected = hexes.filter((h) => !KNOWN_OK.includes(h))
  if (unexpected.length) {
    const uniq = [...new Set(unexpected)]
    console.log(`  ${file.replace(SRC + '\\', '')}`)
    console.log(`     ${uniq.join(', ')}`)
  }
}

console.log('\n=== 2) 各视图是否使用统一容器宽度/间距 ===')
for (const file of files.filter((f) => f.includes('views'))) {
  const text = readFileSync(file, 'utf8')
  const root = text.match(/<template>\s*\n\s*<div class="([^"]+)"/)
  console.log(`  ${file.split('\\').pop()?.padEnd(24)} ${root ? root[1] : '(未匹配)'}`)
}

console.log('\n=== 3) 空状态/加载态是否齐全 ===')
for (const file of files.filter((f) => f.includes('views'))) {
  const text = readFileSync(file, 'utf8')
  const name = file.split('\\').pop()
  const loading = /加载中|loading/i.test(text)
  const empty = /暂无|还没有|没有找到|为空/.test(text)
  console.log(`  ${name.padEnd(24)} 加载态=${loading ? 'Y' : '-'} 空态=${empty ? 'Y' : '-'}`)
}