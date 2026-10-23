/**
 * 校验动画按需加载表是否覆盖数据库中全部 animationId。
 * 走 esbuild 打包（因为模块是 TS），在 Node 侧执行。
 */
import { writeFileSync, unlinkSync } from 'node:fs'
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

// 2) 打包加载表，取回它登记的 id 列表。
// 动态 import 的动画模块整体替换为空对象：探测只需要 id 列表，
// 不执行任何动画代码（它们依赖浏览器环境且体积很大）。
const entry = join(ROOT, 'scripts/_loader-probe.ts')
writeFileSync(
  entry,
  `import { registeredAnimationIds } from '../client/src/components/knowledge/animationLoader'\n` +
    `export const ids = registeredAnimationIds()\n`,
  'utf8',
)

const outfile = join(ROOT, 'scripts/_loader-probe.mjs')
try {
  const stubPlugin = {
    name: 'stub-animation-modules',
    setup(build) {
      // 匹配 animations/ 下的具体模块（排除 types 与 manim 入口）
      build.onResolve({ filter: /animations\// }, (args) => {
        if (/animations\/(types|manim)$/.test(args.path)) return null
        if (args.path.includes('/animations/')) {
          return { path: args.path, namespace: 'anim-stub' }
        }
        return null
      })
      build.onLoad({ filter: /.*/, namespace: 'anim-stub' }, () => ({
        contents: 'export default {}',
        loader: 'js',
      }))
    },
  }

  await build({
    entryPoints: [entry],
    outfile,
    bundle: true,
    format: 'esm',
    platform: 'node',
    target: 'node18',
    logLevel: 'silent',
    plugins: [stubPlugin],
  })

  const mod = await import(`file://${outfile.replace(/\\/g, '/')}?t=${Date.now()}`)
  const registered = new Set(mod.ids)

  const missing = [...usedIds].filter((id) => !registered.has(id)).sort()
  const unused = [...registered].filter((id) => !usedIds.has(id)).sort()

  console.log(`数据库使用的 animationId : ${usedIds.size}`)
  console.log(`按需加载表登记的动画     : ${registered.size}`)
  console.log('')
  if (missing.length) {
    console.log(`❌ 加载表缺失 ${missing.length} 个（这些动画块将无法渲染）:`)
    for (const id of missing) console.log(`   - ${id}`)
    process.exitCode = 1
  } else {
    console.log('✅ 加载表覆盖全部 animationId')
  }
  if (unused.length) {
    console.log(`\nℹ️  加载表多出 ${unused.length} 个当前未被引用的动画（可用于后续内容）:`)
    for (const id of unused) console.log(`   - ${id}`)
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