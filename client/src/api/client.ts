import type {
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

export const api = {
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

  dashboard: () => request<Dashboard>('/api/v1/stats/dash'),

  exams: () => request<ExamSummary[]>('/api/v1/exams'),

  exam: (year: number) => request<ExamDetail>(`/api/v1/exams/${year}`),

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

  knowledgePoints: (subject?: string) =>
    request<KnowledgePointView[]>(
      `/api/v1/knowledge${subject ? `?subject=${encodeURIComponent(subject)}` : ''}`,
    ),

  knowledgeBlock: (blockId: string) =>
    request<KnowledgeBlockDetail>(`/api/v1/knowledge/block/${encodeURIComponent(blockId)}`),

  /** 某知识点的全部正文块（按 subpoint 分节，用于知识阅读页）。 */
  knowledgePoint: (pointId: string) =>
    request<KnowledgePointResponse>(`/api/v1/knowledge/point/${encodeURIComponent(pointId)}`),

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