#!/usr/bin/env node
/**
 * 用 esbuild 的 JS API 打包知识内容。
 * 需要把 `@/...` 别名映射到 client/src，CLI 旗帜对别名的支持随版本变化，
 * 因此固定走 API。
 */
import { build } from 'esbuild'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')

await build({
  entryPoints: [join(ROOT, 'scripts/content-bundle-entry.ts')],
  outfile: join(ROOT, 'client/content-bundle/all.mjs'),
  bundle: true,
  format: 'esm',
  platform: 'node',
  target: 'node18',
  logLevel: 'info',
  alias: {
    '@': join(ROOT, 'client/src'),
  },
  // 图片资源在文章里以 ESM import 形式引路径；导入脚本只需要字符串路径，
  // 因此按 dataurl 之外的 text 方式内联（webp 无法转文本，改用 file 输出后取相对路径）。
  loader: {
    '.svg': 'text',
    '.webp': 'dataurl',
    '.png': 'dataurl',
  },
})

console.log('内容打包完成：client/content-bundle/all.mjs')