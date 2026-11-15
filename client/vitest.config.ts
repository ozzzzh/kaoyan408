import { defineConfig } from 'vitest/config'
import { fileURLToPath, URL } from 'node:url'

/**
 * 单测只跑 src/utils 这类纯逻辑模块，因此用 jsdom 环境跑（DOMPurify / KaTeX 需要 DOM）。
 * 组件级测试暂不在范围内：那些路径由 scripts/verify-*.mjs 的端到端回归覆盖。
 */
export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
  },
})
