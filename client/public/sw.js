/**
 * Service Worker：把应用外壳与构建产物缓存到本地。
 *
 * 为什么需要它：线上入口经 Cloudflare 隧道，单次往返 0.9–2.7 秒。回访用户重新下载
 * 同样的 entry/runtime/CSS/chunk 完全是浪费——这些文件名带内容哈希，天然不可变，
 * 缓存后就再也不需要出网。
 *
 * 策略（刻意保守，宁可慢一点也不要白屏）：
 *   - /assets/**           cache-first（immutable）
 *   - 页面导航             network-first（3 秒超时回退缓存），保证发版后能立刻拿到新入口
 *   - 其它同源静态资源      stale-while-revalidate
 *   - /api/** 与跨域请求    完全不介入（避免缓存到个人数据或鉴权结果）
 *
 * 版本变更：改名 CACHE 即可让旧缓存整体失效；activate 时清理所有非当前版本缓存。
 */
const CACHE = 'kaoyan408-shell-v1'
const NAV_TIMEOUT_MS = 3000

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      // 只预缓存最关键的入口文件；失败不应阻塞安装
      .then((cache) => cache.addAll(['/', '/theme-init.js', '/favicon.svg']).catch(() => undefined))
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

/** 网络优先，超时回退缓存（导航请求用）。 */
async function networkFirst(request) {
  const cache = await caches.open(CACHE)
  const cached = await cache.match(request, { ignoreSearch: true })
  try {
    const response = await Promise.race([
      fetch(request),
      new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), NAV_TIMEOUT_MS)),
    ])
    if (response && response.ok) {
      cache.put(request, response.clone())
    }
    return response
  } catch (error) {
    // 回退：同一个 URL 的缓存 → 缓存的首页 → 原样报错
    return cached || (await cache.match('/')) || fetch(request)
  }
}

/** 缓存优先（带哈希的构建产物）。 */
async function cacheFirst(request) {
  const cache = await caches.open(CACHE)
  const cached = await cache.match(request)
  if (cached) return cached
  const response = await fetch(request)
  if (response && response.ok) {
    cache.put(request, response.clone())
  }
  return response
}

/** 先给缓存，同时后台更新。 */
async function staleWhileRevalidate(request) {
  const cache = await caches.open(CACHE)
  const cached = await cache.match(request)
  const network = fetch(request)
    .then((response) => {
      if (response && response.ok) cache.put(request, response.clone())
      return response
    })
    .catch(() => undefined)
  return cached || (await network) || fetch(request)
}

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') return

  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return
  // 接口一律不缓存：其中有个人数据与鉴权结果
  if (url.pathname.startsWith('/api/')) return

  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(cacheFirst(request))
    return
  }
  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request))
    return
  }
  event.respondWith(staleWhileRevalidate(request))
})
