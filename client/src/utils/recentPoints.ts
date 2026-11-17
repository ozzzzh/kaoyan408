/**
 * 「最近浏览的知识点」本地记录。
 *
 * 搜索页的空状态需要一点真实内容，否则整页只有搜索框、显得很空。
 * 这份记录只存在本机 localStorage，不上传、不参与登录态，
 * 换设备或清缓存后自然丢失 —— 本来就是给人「接着上次看」的便利记忆，不是学习数据。
 */

const KEY = 'kaoyan:recent-points'
const MAX = 8

export interface RecentPoint {
  pointId: string
  title: string
  subject: string
  /** 毫秒时间戳，用于排序与「刚刚 / n 分钟前」展示。 */
  at: number
}

/** localStorage 在隐私模式/被禁用时会抛异常，这里一律降级为「没有记录」。 */
function read(): RecentPoint[] {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (item): item is RecentPoint =>
        !!item &&
        typeof item === 'object' &&
        typeof (item as RecentPoint).pointId === 'string' &&
        typeof (item as RecentPoint).title === 'string' &&
        typeof (item as RecentPoint).at === 'number',
    )
  } catch {
    return []
  }
}

function write(list: RecentPoint[]): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(list))
  } catch {
    /* 配额满或被禁用：记不住就算了，不影响页面 */
  }
}

/** 记一次浏览。同一个知识点重复打开只更新时间并提到最前，不重复占位。 */
export function rememberPoint(entry: Omit<RecentPoint, 'at'>): void {
  if (!entry.pointId || !entry.title) return
  const rest = read().filter((item) => item.pointId !== entry.pointId)
  write([{ ...entry, at: Date.now() }, ...rest].slice(0, MAX))
}

export function recentPoints(limit = 4): RecentPoint[] {
  return read().slice(0, limit)
}

export function clearRecentPoints(): void {
  try {
    localStorage.removeItem(KEY)
  } catch {
    /* 同上 */
  }
}
