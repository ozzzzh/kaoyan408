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