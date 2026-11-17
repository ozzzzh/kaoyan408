/**
 * 用户自带的 AI 调用参数（BYOK，L1：只存本机）。
 *
 * 刻意只放浏览器 localStorage：
 * - 不上传、不进数据库，换设备或清缓存就没了；
 * - 服务端只在单次请求里用，不落库、不写日志。
 *
 * 代价是 XSS 能读到它，所以界面上必须写清「仅保存在本机」并支持一键清除。
 */

const KEY = 'kaoyan:ai-override'

export interface AiOverride {
  /** 接口地址，必须 https 且在服务端白名单内；留空表示用默认。 */
  base: string
  /** 密钥；空串表示没配置。 */
  key: string
  /** 模型名；留空表示用默认。 */
  model: string
}

const EMPTY: AiOverride = { base: '', key: '', model: '' }

export function loadAiOverride(): AiOverride {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return { ...EMPTY }
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return { ...EMPTY }
    const value = parsed as Partial<AiOverride>
    return {
      base: typeof value.base === 'string' ? value.base : '',
      key: typeof value.key === 'string' ? value.key : '',
      model: typeof value.model === 'string' ? value.model : '',
    }
  } catch {
    return { ...EMPTY }
  }
}

export function saveAiOverride(value: AiOverride): void {
  try {
    const trimmed: AiOverride = {
      base: value.base.trim(),
      key: value.key.trim(),
      model: value.model.trim(),
    }
    if (!trimmed.key && !trimmed.base && !trimmed.model) {
      localStorage.removeItem(KEY)
      return
    }
    localStorage.setItem(KEY, JSON.stringify(trimmed))
  } catch {
    /* 隐私模式/配额满：存不下就当没配置，不影响默认路径 */
  }
}

export function clearAiOverride(): void {
  try {
    localStorage.removeItem(KEY)
  } catch {
    /* 同上 */
  }
}

/** 是否已配置自带密钥（决定请求要不要带 X-AI-* 头）。 */
export function hasAiOverride(value: AiOverride): boolean {
  return Boolean(value.key || value.base || value.model)
}
