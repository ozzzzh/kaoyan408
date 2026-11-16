#!/usr/bin/env node
/**
 * 用 Cloudflare API 给入口 HTML 建一条 Cache Rule，并验证是否生效。
 *
 * 为什么需要它：nginx 已经下发 `s-maxage=60`，但 Cloudflare **默认不缓存 HTML**，
 * 不显式建规则的话每次访问都要跨境回源（实测 TTFB 中位 1.5s）。
 * 面板点一次也行；这个脚本适合「不想点、或者想把它固化下来」的情况。
 *
 * 需要的 Token 权限（最小集）：
 *   - Zone → Cache Rules → Edit
 *   - Zone → Zone         → Read      （用来按域名查 zone id）
 *   - Zone → Cache Purge  → Purge     （可选，建完规则清一次缓存）
 *   Zone Resources 限定到具体域名（如 de5.net），不要给 All zones。
 *
 * 用法：
 *   CF_API_TOKEN=xxx node scripts/cf-cache-rule.mjs                 # 建规则 + 清理 + 验证
 *   CF_API_TOKEN=xxx node scripts/cf-cache-rule.mjs --dry-run       # 只打印将要提交的内容
 *   CF_API_TOKEN=xxx CF_ZONE_NAME=de5.net node scripts/cf-cache-rule.mjs
 *
 * 规则内容：/ 与 index.html 可缓存，Edge TTL 60s，Browser TTL 跟随源站（max-age=0）。
 */
const TOKEN = process.env.CF_API_TOKEN || process.env.CLOUDFLARE_API_TOKEN
const ZONE_NAME = process.env.CF_ZONE_NAME || 'de5.net'
const SITE = process.env.CF_SITE || 'https://tizhilian408.de5.net'
const RULE_DESCRIPTION = 'cache-html-60s (kaoyan408 entry)'
const DRY_RUN = process.argv.includes('--dry-run')
const API = 'https://api.cloudflare.com/client/v4'

if (!TOKEN) {
  console.error('缺少 CF_API_TOKEN。请创建一个只带 Cache Rules:Edit + Zone:Read 的 Token，然后：')
  console.error('  setx CF_API_TOKEN "<token>"      (Windows，之后新开的终端生效)')
  console.error('  CF_API_TOKEN=xxx node scripts/cf-cache-rule.mjs   (临时)')
  process.exit(2)
}

const headers = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' }

async function call(path, init = {}) {
  const response = await fetch(API + path, { ...init, headers })
  const body = await response.json().catch(() => ({}))
  if (!response.ok || body.success === false) {
    const detail = JSON.stringify(body.errors ?? body).slice(0, 500)
    throw new Error(`${init.method || 'GET'} ${path} 失败: HTTP ${response.status} ${detail}`)
  }
  return body.result
}

const RULE = {
  description: RULE_DESCRIPTION,
  expression: '(http.request.uri.path eq "/") or (http.request.uri.path.ends_with "/index.html")',
  action: 'set_cache_settings',
  action_parameters: {
    cache: true,
    edge_ttl: { mode: 'override_origin', default: 60 },
    browser_ttl: { mode: 'respect_origin' },
  },
  enabled: true,
}

async function main() {
  console.log(`Token 已提供（长度 ${TOKEN.length}），zone=${ZONE_NAME}`)
  const zones = await call(`/zones?name=${encodeURIComponent(ZONE_NAME)}`)
  if (!zones?.length) throw new Error(`找不到 zone: ${ZONE_NAME}（Token 是否有 Zone:Read？域名是否拼对？）`)
  const zoneId = zones[0].id
  console.log(`zone id = ${zoneId}`)

  const phasePath = `/zones/${zoneId}/rulesets/phases/http_request_cache_settings/entrypoint`
  let ruleset = null
  try {
    ruleset = await call(phasePath)
  } catch {
    console.log('该 zone 还没有缓存规则集，将新建')
  }

  const existing = ruleset?.rules ?? []
  const kept = existing.filter((rule) => rule.description !== RULE_DESCRIPTION)
  console.log(`现有规则 ${existing.length} 条，保留 ${kept.length} 条，新增/替换 1 条（${RULE_DESCRIPTION}）`)

  const payload = { name: 'kaoyan408 cache settings', kind: 'zone', phase: 'http_request_cache_settings', rules: [...kept, RULE] }

  if (DRY_RUN) {
    console.log('--- dry-run，将要提交 ---')
    console.log(JSON.stringify(payload, null, 2))
    return
  }

  await call(phasePath, { method: 'PUT', body: JSON.stringify(payload) })
  console.log('规则已提交')

  try {
    await call(`/zones/${zoneId}/purge_cache`, { method: 'POST', body: JSON.stringify({ files: [`${SITE}/`] }) })
    console.log('已清理入口 HTML 的缓存')
  } catch (error) {
    console.log(`清理缓存失败（不影响规则生效，可稍后手动 Purge）：${error.message}`)
  }

  console.log('\n验证（连续两次取首页）：')
  for (let i = 1; i <= 2; i++) {
    const started = Date.now()
    const response = await fetch(`${SITE}/`, { headers: { 'Cache-Control': 'no-cache' } })
    const ms = Date.now() - started
    console.log(
      `  第${i}次: HTTP ${response.status} ${ms}ms cf-cache-status=${response.headers.get('cf-cache-status') ?? '-'} ` +
        `age=${response.headers.get('age') ?? '-'} cache-control=${response.headers.get('cache-control') ?? '-'}`,
    )
    await new Promise((resolve) => setTimeout(resolve, 1200))
  }
  console.log('\n如果第二次仍是 MISS/DYNAMIC：确认规则表达式匹配的是 `/`（而不是只匹配 index.html），')
  console.log('并且 Cloudflare 里没有其它 Cache Rule 用更高优先级覆盖它。')
}

main().catch((error) => {
  console.error('失败:', error.message)
  process.exit(1)
})
