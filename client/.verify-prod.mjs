import { chromium } from 'playwright-core'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const BASE = process.env.BASE
const browser = await chromium.launch({ executablePath: EDGE, headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
const errors = []
page.on('pageerror', (e) => errors.push('pageerror: ' + e.message))
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
const api = []
page.on('response', async (r) => {
  if (!r.url().includes('/api/')) return
  api.push({ url: r.url().split('/api/v1')[1], status: r.status(), enc: r.headers()['content-encoding'] || '-' })
})

const t0 = Date.now()
await page.goto(BASE + '/#/practice', { waitUntil: 'domcontentloaded' })
await page.waitForSelector('[aria-label="正在加载题目"]', { timeout: 15000 }).then(() => {}, () => {})
const skeletonAt = Date.now() - t0
const skeletonCount = await page.locator('[aria-label="正在加载题目"]').count()
await page.waitForSelector('main article', { timeout: 45000 })
const contentAt = Date.now() - t0
const stats = await page.locator('header p').first().innerText()
await page.screenshot({ path: process.env.OUT })

// 切年
const yearBtn = page.locator('main aside button', { hasText: '2025' }).first()
let switchMs = null
if (await yearBtn.count()) {
  const ts = Date.now()
  await yearBtn.click()
  await page.waitForFunction((prev) => {
    const el = document.querySelector('header p')
    return el && el.textContent && el.textContent !== prev && el.textContent.includes('2025')
  }, stats, { timeout: 45000 }).catch(() => {})
  switchMs = Date.now() - ts
}
console.log(JSON.stringify({
  skeletonShownAtMs: skeletonAt, skeletonCountAtThatMoment: skeletonCount,
  contentVisibleAtMs: contentAt, stats,
  switchTo2025Ms: switchMs, url: page.url(),
  apiCalls: api, errors: errors.slice(0, 4),
}, null, 2))
await browser.close()