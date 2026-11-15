import { chromium } from 'playwright-core'
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const BASE = process.env.BASE
const browser = await chromium.launch({ executablePath: EDGE, headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
await page.goto(BASE + '/#/practice', { waitUntil: 'networkidle', timeout: 60000 })
const entries = await page.evaluate(() => performance.getEntriesByType('resource')
  .map((r) => ({ name: r.name.split('/').slice(-1)[0].split('?')[0], start: Math.round(r.startTime), dur: Math.round(r.duration), size: r.transferSize, proto: r.nextHopProtocol }))
  .sort((a, b) => a.start - b.start))
const nav = await page.evaluate(() => { const n = performance.getEntriesByType('navigation')[0]; return { ttfb: Math.round(n.responseStart), domContent: Math.round(n.domContentLoadedEventEnd), load: Math.round(n.loadEventEnd), proto: n.nextHopProtocol } })
console.log(JSON.stringify({ navigation: nav, resources: entries.slice(0, 14) }, null, 2))
await browser.close()