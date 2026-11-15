import type {
  AiConversationDetail,
  AiAnimationResult,
  AiDiagramResult,
  AiConversationSummary,
  AiStatus,
  AnswerResult,
  ApiEnvelope,
  Dashboard,
  ExamDetail,
  ExamSummary,
  KnowledgeBlockDetail,
  KnowledgePointResponse,
  KnowledgePointView,
  LoginResult,
  MemoryDue,
  MemorySummary,
  MockHistoryItem,
  MockState,
  PointMastery,
  PracticeEntrances,
  PracticeQueue,
  QuestionView,
  SearchResult,
  SearchSuggestion,
  AuthUser,
  WrongBook,
} from './types'

const TOKEN_KEY = 'kaoyan408.token'

/** 后端异常：携带业务 code，便于页面区分 401 与其它错误。 */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* 忽略隐私模式下的存储异常 */
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(-1, '网络异常，请检查后端服务是否已启动。')
  }

  let envelope: ApiEnvelope<T> | null = null
  try {
    envelope = (await response.json()) as ApiEnvelope<T>
  } catch {
    envelope = null
  }

  if (!envelope || typeof envelope.code !== 'number') {
    throw new ApiError(response.status, `响应格式异常（HTTP ${response.status}）`)
  }
  if (envelope.code !== 0) {
    throw new ApiError(envelope.code, envelope.msg ?? '请求失败')
  }
  return envelope.data
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

/* ---------------- 内容缓存与请求去重 ---------------- */

/**
 * 为什么需要这一层：
 *
 * 1. 去重。实测发现同一个请求会被并发发两次——
 *    App.vue 和落地页都会拉 /stats/dash，知识点页路由变化时
 *    /knowledge 也会被重复拉。这两个请求各自穿越隧道，各花 400ms+。
 *    只要两次调用还在飞行中就共享同一个 Promise，就能省掉一整趟。
 *
 * 2. 缓存。知识树（147 条）和知识点正文只随部署变化、不随用户变化，
 *    在站内来回切换却每次都要重新下载。缓存后切换知识点只下载新正文。
 *
 * 只缓存明确的内容型接口，用户相关数据（进度、错题、AI 会话）一律不缓存，
 * 否则换账号或做题后会看到旧数据。
 */
const inflight = new Map<string, Promise<unknown>>()
const contentCache = new Map<string, { at: number; data: unknown }>()

/** 内容型接口的缓存时长；与 nginx 的 Cache-Control 保持一致。 */
const CONTENT_TTL_MS = 5 * 60 * 1000

/** 正文缓存最多留多少条，避免 147 个知识点全攒在内存里。 */
const MAX_CACHED_ARTICLES = 24

/**
 * 并发去重：同一个 key 的请求还在路上时，直接复用那个 Promise。
 *
 * 失败时要清掉记录，否则一次网络抖动会让后续请求永远拿到同一个失败结果。
 */
function dedupe<T>(key: string, loader: () => Promise<T>): Promise<T> {
  const pending = inflight.get(key)
  if (pending) return pending as Promise<T>
  const p = loader()
    .then((data) => {
      inflight.delete(key)
      return data
    })
    .catch((e) => {
      inflight.delete(key)
      throw e
    })
  inflight.set(key, p)
  return p
}

/**
 * 带缓存 + 去重的内容请求。
 *
 * @param key    缓存键，必须能唯一标识这次请求（含全部查询参数）
 * @param loader 真正的请求
 * @param cap    缓存条目上限（按插入顺序淘汰最旧的）
 */
function cachedContent<T>(key: string, loader: () => Promise<T>, cap = Infinity): Promise<T> {
  const hit = contentCache.get(key)
  if (hit && Date.now() - hit.at < CONTENT_TTL_MS) {
    return Promise.resolve(hit.data as T)
  }
  return dedupe(key, () =>
    loader().then((data) => {
      if (contentCache.size >= cap) {
        // Map 保持插入顺序，删第一个就是删最旧的
        const oldest = contentCache.keys().next().value
        if (oldest !== undefined) contentCache.delete(oldest)
      }
      contentCache.set(key, { at: Date.now(), data })
      return data
    }),
  )
}

/** 手动清空内容缓存（比如用户点了「刷新」或重新部署后）。 */
export function clearContentCache(): void {
  contentCache.clear()
  inflight.clear()
}

/**
 * AI 对话（SSE 流式）。
 *
 * 不能复用 request()：那里按 JSON 解析响应，而这里要边收边显示。
 * 事件约定见后端 AiController：thinking / delta / done / error。
 *
 * 返回一个取消函数，页面卸载时调用可主动断开，避免后端空跑。
 */
export function streamAiChat(
  question: string,
  conversationId: number | null,
  handlers: {
    onThinking?: () => void
    onDelta?: (text: string) => void
    onDone?: (evt: { conversationId: number | null; promptTokens?: number; completionTokens?: number; guest?: boolean }) => void
    onError?: (message: string) => void
  },
): () => void {
  const controller = new AbortController()
  const headers = new Headers({ 'Content-Type': 'application/json', Accept: 'text/event-stream' })
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  ;(async () => {
    try {
      const res = await fetch('/api/v1/ai/chat', {
        method: 'POST',
        headers,
        body: JSON.stringify({ question, conversationId }),
        signal: controller.signal,
      })

      // 业务错误（如游客次数用完）走统一 envelope，不是 SSE
      const ct = res.headers.get('Content-Type') ?? ''
      if (!ct.includes('text/event-stream')) {
        const body = (await res.json()) as ApiEnvelope<unknown>
        handlers.onError?.(body?.msg ?? `请求失败（HTTP ${res.status}）`)
        return
      }

      const reader = res.body?.getReader()
      if (!reader) {
        handlers.onError?.('浏览器不支持流式读取')
        return
      }

      const decoder = new TextDecoder()
      let buffer = ''

      // 事件之间以空行分隔，一个事件可能跨多个 chunk
      const consume = (block: string) => {
        let event = ''
        const dataLines: string[] = []
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
        }
        if (!event || dataLines.length === 0) return
        let payload: any
        try {
          payload = JSON.parse(dataLines.join(''))
        } catch {
          return
        }
        if (event === 'thinking') handlers.onThinking?.()
        else if (event === 'delta') handlers.onDelta?.(payload.text ?? '')
        else if (event === 'done') handlers.onDone?.(payload)
        else if (event === 'error') handlers.onError?.(payload.message ?? 'AI 服务异常')
      }

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let idx: number
        while ((idx = buffer.indexOf('\n\n')) >= 0) {
          consume(buffer.slice(0, idx))
          buffer = buffer.slice(idx + 2)
        }
      }
      if (buffer.trim()) consume(buffer)
    } catch (e) {
      // 主动取消不当作错误
      if ((e as Error).name !== 'AbortError') {
        handlers.onError?.((e as Error).message || '网络异常')
      }
    }
  })()

  return () => controller.abort()
}
export const api = {
    /* ---- AI 学习助手 ---- */
    aiStatus: () => request<AiStatus>('/api/v1/ai/status'),
    aiConversations: () => request<AiConversationSummary[]>('/api/v1/ai/conversations'),
    aiConversation: (id: number) => request<AiConversationDetail>(`/api/v1/ai/conversations/${id}`),
    aiAnimation: (question: string, answerExcerpt?: string, knowledge?: string) =>
      post<AiAnimationResult>('/api/v1/ai/animation', { question, answerExcerpt, knowledge }),
    aiDiagram: (question: string, answerExcerpt?: string, knowledge?: string) =>
      post<AiDiagramResult>('/api/v1/ai/diagram', { question, answerExcerpt, knowledge }),
    aiDeleteConversation: (id: number) =>
      request<{ deleted: boolean }>(`/api/v1/ai/conversations/${id}`, { method: 'DELETE' }),
  health: () => request<{ name: string; version: string; status: string }>('/api/v1/health'),

  login: (username: string, password: string) =>
    post<LoginResult>('/api/v1/auth/login', { username, password }),

  register: (username: string, email: string, password: string, code: string) =>
    post<LoginResult>('/api/v1/auth/register', { username, email, password, code }),

  /** 发送邮箱注册验证码。mock 模式下不真发信，验证码在服务端日志里。 */
  sendMailCode: (email: string) =>
    post<{ cooldownSeconds: number; mock: boolean; message: string }>(
      '/api/v1/auth/send-mail-code',
      { email },
    ),

  currentUser: () => request<AuthUser>('/api/v1/user/current'),

  /**
   * 看板数据。含用户进度，**不缓存**，只做并发去重。
   *
   * 实测它会被拉两次：App.vue 挂载时 loadDashboard() 一次，
   * 落地页自己也拉一次，两个请求几乎同时发出，各走一趟隧道。
   */
  dashboard: () => dedupe('dashboard', () => request<Dashboard>('/api/v1/stats/dash')),

  exams: () => cachedContent('exams', () => request<ExamSummary[]>('/api/v1/exams')),

  exam: (year: number) =>
    cachedContent(`exam:${year}`, () => request<ExamDetail>(`/api/v1/exams/${year}`)),

  /**
   * 单题详情。
   * memorize=true 时后端直接返回答案与解析（背题模式用），否则只有作答后才返回。
   */
  question: (examRef: string, reveal = 0, memorize = false) =>
    request<QuestionView>(
      `/api/v1/questions/${encodeURIComponent(examRef)}?reveal=${reveal}&memorize=${memorize}`,
    ),

  submitAnswer: (examRef: string, answer: string, durationMs?: number) =>
    post<AnswerResult>(`/api/v1/questions/${encodeURIComponent(examRef)}/answer`, {
      answer,
      durationMs,
    }),

  // 知识树内容只随部署变化，缓存后站内切换知识点不必重复下载。
  knowledgePoints: (subject?: string) =>
    cachedContent(`knowledge:${subject ?? ''}`, () =>
      request<KnowledgePointView[]>(
        `/api/v1/knowledge${subject ? `?subject=${encodeURIComponent(subject)}` : ''}`,
      ),
    ),

  knowledgeBlock: (blockId: string) =>
    cachedContent(`block:${blockId}`, () =>
      request<KnowledgeBlockDetail>(`/api/v1/knowledge/block/${encodeURIComponent(blockId)}`),
    ),

  /**
   * 某知识点的全部正文块（按 subpoint 分节，用于知识阅读页）。
   *
   * 正文单条 10–34KB，缓存条数设上限，避免 147 个知识点全留在内存里。
   */
  knowledgePoint: (pointId: string) =>
    cachedContent(
      `point:${pointId}`,
      () => request<KnowledgePointResponse>(`/api/v1/knowledge/point/${encodeURIComponent(pointId)}`),
      MAX_CACHED_ARTICLES,
    ),

  /** 刷题队列（按年 / 科目 / 知识点 / 错题 / 未做筛选）。 */
  practiceQueue: (params: {
    year?: number
    subject?: string
    blockId?: string
    pointId?: string
    scope?: 'all' | 'wrong' | 'untouched' | 'wrong-pending' | 'wrong-mastered'
  }) => {
    const query = new URLSearchParams()
    if (params.year != null) query.set('year', String(params.year))
    if (params.subject) query.set('subject', params.subject)
    if (params.blockId) query.set('blockId', params.blockId)
    if (params.pointId) query.set('pointId', params.pointId)
    if (params.scope) query.set('scope', params.scope)
    const suffix = query.toString()
    return request<PracticeQueue>(`/api/v1/practice/queue${suffix ? `?${suffix}` : ''}`)
  },

  /** 错题本概览。 */
  wrongBook: () => request<WrongBook>('/api/v1/practice/wrong-book'),

  /** 各刷题入口的题量汇总。 */
  practiceEntrances: () => request<PracticeEntrances>('/api/v1/practice/entrances'),

  /** 全文搜索：知识点 / 题目 / 正文片段。 */
  search: (q: string) => request<SearchResult>(`/api/v1/search?q=${encodeURIComponent(q)}`),

  /** 搜索联想（输入框下拉）。 */
  searchSuggest: (q: string) =>
    request<SearchSuggestion[]>(`/api/v1/search/suggest?q=${encodeURIComponent(q)}`),

  /** 背诵：到期复习队列。 */
  memoryDue: (subject?: string) =>
    request<MemoryDue>(`/api/v1/memory/due${subject ? `?subject=${encodeURIComponent(subject)}` : ''}`),

  /** 背诵：概览统计。 */
  memorySummary: () => request<MemorySummary>('/api/v1/memory/summary'),

  /** 背诵：复习一张卡片（记住了 / 没记住）。 */
  memoryReview: (cardId: number, remembered: boolean) =>
    post<{ cardId: number; box: number; nextReviewDate: string; intervalDays: number }>(
      '/api/v1/memory/review',
      { cardId, remembered },
    ),

  /** 背诵：把知识点下的要点/公式加入卡片。 */
  memoryAdd: (params: { pointId?: string; subject?: string; limit?: number } = {}) => {
    const query = new URLSearchParams()
    if (params.pointId) query.set('pointId', params.pointId)
    if (params.subject) query.set('subject', params.subject)
    if (params.limit != null) query.set('limit', String(params.limit))
    const suffix = query.toString()
    return post<{ added: number; candidates: number }>(
      `/api/v1/memory/add${suffix ? `?${suffix}` : ''}`,
      {},
    )
  },

  /** 背诵：移除一张卡片。 */
  memoryRemove: (cardId: number) =>
    post<{ deleted: number }>(`/api/v1/memory/remove?cardId=${cardId}`, {}),

  /** 学习看板：知识点级掌握与薄弱点。 */
  statsPoints: (subject?: string) =>
    request<PointMastery>(
      `/api/v1/stats/points${subject ? `?subject=${encodeURIComponent(subject)}` : ''}`,
    ),

  /* ---------- 个人数据管理 ---------- */

  /** 各类个人数据的条数，用于显示「将删除 N 条」。 */
  userDataCounts: () =>
    request<{
      attempts: number
      blockProgress: number
      dailyStats: number
      memoryCards: number
    }>('/api/v1/user/data/counts'),

  /** 导出全部个人数据。 */
  userDataExport: () => request<Record<string, unknown>>('/api/v1/user/data/export'),

  /** 清空个人数据。scope：attempts（作答 + 掌握度 + 每日统计）/ memory / all */
  userDataClear: (scope: 'attempts' | 'memory' | 'all') =>
    request<Record<string, number>>(`/api/v1/user/data/clear?scope=${scope}`, {
      method: 'DELETE',
    }),

  /* ---------- 模拟考试 ---------- */

  /** 开始考试；该年份已有进行中的场次会直接返回它。minutes=0 表示不限时。 */
  mockStart: (year: number, minutes = 180) =>
    post<MockState>(`/api/v1/mock/start?year=${year}&minutes=${minutes}`, {}),

  /** 恢复考试现场。 */
  mockState: (sessionId: number) => request<MockState>(`/api/v1/mock/${sessionId}`),

  /** 保存单题答案。 */
  mockAnswer: (sessionId: number, examRef: string, answer: string) =>
    post<{ saved: boolean; remainingSeconds: number }>(
      `/api/v1/mock/${sessionId}/answer?examRef=${encodeURIComponent(examRef)}&answer=${encodeURIComponent(answer)}`,
      {},
    ),

  /** 交卷。 */
  mockSubmit: (sessionId: number, timedOut = false) =>
    post<MockState>(`/api/v1/mock/${sessionId}/submit?timedOut=${timedOut}`, {}),

  /** 综合题自评。 */
  mockGrade: (sessionId: number, examRef: string, score: number) =>
    post<{ selfScore: number; sessionSelfScore: number }>(
      `/api/v1/mock/${sessionId}/grade?examRef=${encodeURIComponent(examRef)}&score=${score}`,
      {},
    ),

  /** 历史成绩。 */
  mockHistory: (limit = 20) =>
    request<{ total: number; items: MockHistoryItem[] }>(`/api/v1/mock/history?limit=${limit}`),

  /**
   * 视频指引：按题目关联的知识点生成 B 站搜索入口。
   * 返回的是搜索链接，不是课程播放地址——本站不托管第三方课程内容。
   */
  videoGuide: (examRef: string) =>
    request<{
      examRef: string
      subjectName: string
      chapterName: string | null
      points: { title: string; subpointTitle: string | null }[]
      sources: { teacher: string; keyword: string; url: string }[]
    }>(`/api/v1/questions/${encodeURIComponent(examRef)}/video-guide`),
}