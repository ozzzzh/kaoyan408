/**
 * 游客匿名标识。
 *
 * 为什么不用 IP：站点跑在隧道后面，nginx 看到的 `$remote_addr` 是隧道对端，
 * 不是访客——用它做限流等于把「游客 3 次」变成全网共享的一个桶。
 * 结果就是谁先用完，所有新访客都显示「剩余 0 次」。改用浏览器里的一枚随机 id 计数，
 * 语义也更贴近界面文案（每个浏览器试用 3 次）。
 *
 * 代价：清缓存/换浏览器会重置。这是访客额度的合理代价，且比现在「整站共用一个桶」好得多。
 * 真正需要防刷的场景（登录、注册）仍然走服务端 IP 限流，不受这里影响。
 */

const KEY = 'kaoyan:guest-id'
const PATTERN = /^[A-Za-z0-9_-]{8,64}$/

function randomId(): string {
  try {
    const uuid = crypto.randomUUID?.()
    if (uuid) return uuid
  } catch {
    /* 老浏览器或非安全上下文 */
  }
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

export function getGuestId(): string {
  try {
    const existing = localStorage.getItem(KEY)
    if (existing && PATTERN.test(existing)) return existing
    const created = randomId()
    localStorage.setItem(KEY, created)
    return created
  } catch {
    // 隐私模式：退回本次页面生命周期内稳定的 id
    return fallback
  }
}

const fallback = randomId()
