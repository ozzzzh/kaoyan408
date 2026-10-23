/**
 * 与后端约定的统一响应体：{ code, msg, data }，HTTP 状态恒为 200。
 */
export interface ApiEnvelope<T> {
  code: number
  msg: string | null
  data: T
}

export interface SubjectStat {
  subject: 'ds' | 'os' | 'co' | 'cn'
  total: number
  done: number
  correct: number
  accuracy: number
  progress: number
  /** 掌握率 = 掌握题数 / 总题数 */
  masteryRate: number
  mastered: number
  shaky: number
  failed: number
  untouched: number
}

export interface DailyPoint {
  date: string
  questions: number
  correct: number
}

/** 热力图单元格：level 0–4，0 表示当日无做题记录。 */
export interface HeatmapDay {
  date: string
  questions: number
  correct: number
  level: number
}

export interface Heatmap {
  start: string
  end: string
  max: number
  days: HeatmapDay[]
}

export interface Dashboard {
  totalQuestions: number
  doneQuestions: number
  correctQuestions: number
  accuracy: number
  progress: number
  masteryRate: number
  subjects: SubjectStat[]
  streakDays: number
  recent: DailyPoint[]
  heatmap: Heatmap | null
  activeDays: number
}

export interface QuestionOption {
  key: string
  text: string
}

export interface SubQuestion {
  number: number
  score: number | null
  description: string | null
}

export interface QuestionView {
  id: string
  number: number
  subject: string
  chapterName: string | null
  questionType: 'choice' | 'comprehensive' | string
  score: number
  stem: string
  difficulty: number | null
  tags: string[]
  imagePaths: string[]
  knowledgeBlockIds: string[]
  answered: boolean
  options: QuestionOption[]
  subQuestions: SubQuestion[]
  answer?: string | null
  explanation?: string | null
}

export interface ExamSummary {
  year: number
  title: string
  questionCount: number
  subjects: Record<string, number>
}

export interface ExamDetail {
  year: number
  title: string
  questionCount: number
  questions: QuestionView[]
}

export interface KnowledgePointView {
  pointId: string
  subject: string
  title: string
  blockCount: number
}

/** 知识点正文（按顺序的扁平块列表，前端再按 subpointId 分节）。 */
export interface KnowledgePointResponse {
  pointId: string
  title: string | null
  subject: string | null
  /** 该知识点下所有块关联真题的去重总数。 */
  questionCount?: number
  blocks: Array<{
    blockId: string
    subpointId: string | null
    subpointTitle: string | null
    order: number
    type: string
    content: string
    /** 该块关联的真题数。 */
    questionCount?: number
  }>
}

/** 刷题队列中的一道题（轻量，不含题干与解析）。 */
export interface PracticeQueueItem {
  examRef: string
  year: number
  number: number
  subject: string
  chapterName: string | null
  questionType: string
  score: number
  difficulty: number | null
  answered: boolean
  correct: boolean
  /** 累计答错次数（答对不清零） */
  wrongCount: number
  /** 答错之后重做答对的次数 */
  retryCorrectCount: number
  /** 错题是否已订正（移出错题本） */
  mastered: boolean
}

export interface PracticeQueue {
  scope: string
  year: number | null
  subject: string | null
  blockId: string | null
  pointId: string | null
  total: number
  answered: number
  correct: number
  items: PracticeQueueItem[]
}

export interface WrongBook {
  /** 待订正的错题数 */
  pending: number
  /** 已订正的错题数 */
  mastered: number
  total: number
  subjects: Array<{ subject: string; name: string; pending: number; mastered: number }>
}

export interface PracticeEntrances {
  subjects: Array<{ subject: string; name: string; total: number; done: number }>
  years: Array<{ year: number; total: number }>
  untouched: number
  wrong: number
  wrongPending: number
  wrongMastered: number
}

/** 搜索命中的知识点。 */
export interface SearchPointHit {
  kind: 'point'
  pointId: string
  title: string
  subject: string
  blockCount: number
  score: number
  bodyHits: number
}

/** 搜索命中的题目。 */
export interface SearchQuestionHit {
  kind: 'question'
  examRef: string
  number: number
  subject: string
  chapterName: string | null
  questionType: string
  score: number
  year: number
  title: string
  hint: string
  snippet: string | null
}

/** 搜索命中的正文片段。 */
export interface SearchBlockHit {
  kind: 'block'
  blockId: string
  pointId: string
  subpointTitle: string | null
  type: string
  pointTitle: string
  subject: string
  score: number
  snippet: string
}

export interface SearchResult {
  query: string
  total: number
  points: SearchPointHit[]
  questions: SearchQuestionHit[]
  blocks: SearchBlockHit[]
}

export interface SearchSuggestion {
  type: 'point' | 'question'
  label: string
  hint: string
  to: string
}

/** 一张背诵卡片（到期队列中的一项）。 */
export interface MemoryCardItem {
  cardId: number
  blockId: string
  type: string
  /** 块的 content_json 原文，前端解析后展示要点/公式 */
  content: string
  subpointTitle: string | null
  pointId: string
  pointTitle: string
  subject: string
  /** 记忆盒序号 0-7 */
  box: number
  reviewCount: number
  correctCount: number
}

export interface MemoryDue {
  total: number
  items: MemoryCardItem[]
}

export interface MemorySummary {
  total: number
  due: number
  solid: number
  reviews: number
  corrects: number
  accuracy: number
  subjects: Array<{ subject: string; name: string; total: number }>
}

export interface KnowledgeBlockView {
  blockId: string
  pointId: string
  pointTitle: string | null
  subpointId: string | null
  subpointTitle: string | null
  type: string
  content: string
}

export interface KnowledgeBlockDetail {
  block: KnowledgeBlockView
  relatedQuestions: Array<{
    id: string
    number: number
    year: string | null
    subject: string
    questionType: string
    hasImage: boolean
  }>
}

export interface AnswerResult {
  questionId: string
  correct: boolean
  standardAnswer: string | null
  explanation: string | null
  firstAttempt: boolean
  blocks: Array<{ blockId: string; total: number; done: number; correct: number }>
  stats: Dashboard
}

export interface AuthUser {
  id: number
  username: string
  email: string | null
  createdAt: string | null
}

export interface LoginResult {
  token: string
  expiresAt: number
  expiresIn: number
  user: AuthUser
}