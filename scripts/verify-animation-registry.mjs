/**
 * 校验动画注册表是否覆盖数据库中全部 animationId。
 * 走 esbuild 打包（因为动画模块是 TS + @ 别名），在 Node 侧执行。
 */
import { readFileSync, writeFileSync, unlinkSync } from 'node:fs'
import { build } from 'esbuild'
import { createConnection } from 'mysql2/promise'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')

// 1) 从数据库取出实际用到的 animationId
const connection = await createConnection({
  host: '127.0.0.1',
  port: 3306,
  user: 'root',
  password: '123456',
  database: 'kaoyan_db',
  charset: 'utf8mb4',
})
const [rows] = await connection.query(
  "SELECT block_id, content_json FROM knowledge_block WHERE type = 'animation'",
)
await connection.end()

const usedIds = new Set()
for (const row of rows) {
  try {
    const parsed = JSON.parse(row.content_json)
    if (parsed.animationId) usedIds.add(parsed.animationId)
  } catch {
    /* 忽略坏数据，导入时已报告 */
  }
}

// 2) 打包注册表并在 Node 中读取其覆盖范围
const entry = join(ROOT, 'scripts/_registry-probe.ts')
writeFileSync(
  entry,
  `import { allAnimationIds } from '../client/src/components/knowledge/animationRegistry'\n` +
    `export const ids = allAnimationIds()\n`,
  'utf8',
)

const outfile = join(ROOT, 'scripts/_registry-probe.mjs')
try {
  await build({
    entryPoints: [entry],
    outfile,
    bundle: true,
    format: 'esm',
    platform: 'node',
    target: 'node18',
    logLevel: 'silent',
    alias: { '@': join(ROOT, 'client/src') },
    loader: { '.svg': 'text', '.webp': 'dataurl', '.png': 'dataurl' },
  })

  const mod = await import(`file://${outfile.replace(/\\/g, '/')}?t=${Date.now()}`)
  const registered = new Set(mod.ids)

  const missing = [...usedIds].filter((id) => !registered.has(id)).sort()
  const unused = [...registered].filter((id) => !usedIds.has(id)).sort()

  console.log(`数据库使用的 animationId : ${usedIds.size}`)
  console.log(`注册表登记的动画        : ${registered.size}`)
  console.log('')
  if (missing.length) {
    console.log(`❌ 注册表缺失 ${missing.length} 个（这些动画块将无法渲染）:`)
    for (const id of missing) console.log(`   - ${id}`)
  } else {
    console.log('✅ 注册表覆盖全部 animationId')
  }
  if (unused.length) {
    console.log(`\nℹ️  注册表多出 ${unused.length} 个当前未被引用的动画（可用于后续内容）:`)
    for (const id of unused.slice(0, 20)) console.log(`   - ${id}`)
  }
} finally {
  for (const path of [entry, outfile]) {
    try {
      unlinkSync(path)
    } catch {
      /* 忽略清理失败 */
    }
  }
}