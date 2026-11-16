#!/usr/bin/env node
/**
 * 边缘缓存自检：确认 Cloudflare 是否真的在缓存入口 HTML。
 *
 * 背景：nginx 已经下发 `s-maxage=60`，但 Cloudflare **默认不缓存 HTML**。
 * 不加 Cache Rule 时每次访问都要跨境回源（实测 TTFB 中位 1.5s），
 * 而这类「配置没生效」在浏览器里完全看不出来——页面照常打开，只是慢。
 *
 * 用法：
 *   node scripts/check-edge-cache.mjs                      # 默认域名
 *   node scripts/check-edge-cache.mjs https://your.domain
 *
 * 判定：
 *   cf-cache-status: HIT     → 边缘已缓存，回访不再回源
 *   MISS/DYNAMIC/EXPIRED     → 还没有规则（或规则没覆盖这条路径）
 */
const base = (process.argv[2] || 'https://tizhilian408.de5.net').replace(/\/$/, '')

const samples = []
for (let i = 0; i < 3; i++) {
  const started = performance.now()
  const response = await fetch(base + '/', {
    headers: { 'Cache-Control': 'no-cache' },
    redirect: 'follow',
  })
  const text = await response.text()
  const ms = Math.round(performance.now() - started)
  samples.push({
    round: i + 1,
    status: response.status,
    ms,
    bytes: Buffer.byteLength(text),
    cacheStatus: response.headers.get('cf-cache-status') ?? '(无该响应头)',
    cacheControl: response.headers.get('cache-control') ?? '(无)',
    encoding: response.headers.get('content-encoding') ?? '(未压缩)',
    age: response.headers.get('age') ?? '-',
  })
}

console.log(`目标: ${base}/`)
console.table(samples)

const last = samples[samples.length - 1]
const apiStart = performance.now()
await fetch(base + '/api/v1/health')
const apiMs = Math.round(performance.now() - apiStart)

console.log('\n结论:')
if (last.cacheStatus === 'HIT') {
  console.log('  ✅ 入口 HTML 已由边缘缓存（cf-cache-status: HIT）')
} else {
  console.log(`  ❌ 入口 HTML 未被边缘缓存（cf-cache-status: ${last.cacheStatus}）`)
  console.log('     去 Cloudflare 面板加一条 Cache Rule：URI Path 等于 / 或结尾 index.html，')
  console.log('     Cache eligibility = Eligible，Edge TTL = 60 秒，Browser TTL = Respect origin。')
  console.log('     详见 README「让回访更快：Cloudflare 缓存规则」。')
}
console.log(`  对比：一次 /api/v1/health 往返 ${apiMs}ms（未缓存接口的真实成本）`)
if (!last.encoding.includes('br') && !last.encoding.includes('gzip')) {
  console.log('  ⚠️ 入口 HTML 未压缩：检查 nginx gzip 与 Cloudflare Brotli 设置')
}
