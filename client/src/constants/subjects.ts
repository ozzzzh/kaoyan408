/**
 * 四科（数据结构 / 操作系统 / 计算机组成原理 / 计算机网络）的统一配色。
 *
 * 为什么集中在一处：原先四套色值散落在 DashboardView、StatsView、MeView、
 * ExamsView、ExamDetailView、SubjectMasteryPanel 六个文件里，各写一遍。
 * 品牌色调整时必然漏改，导致同一科目在不同页面颜色不一致。
 *
 * 取色原则：
 * - 四科色必须两两可辨，且都不能与品牌青绿 #00B5A8 混淆，
 *   否则「计算机网络」和品牌强调色会撞在一起，看不出重点是哪个；
 * - 因此把「计算机网络」从原来的青绿改为蓝紫，青绿让位给品牌本身。
 */
export interface SubjectMeta {
  name: string
  /** 进度条 / 图例圆点的底色类 */
  bar: string
  /** 极浅底色，用于卡片背景 */
  soft: string
  /** 文字色类 */
  text: string
  /**
   * 原始十六进制色值。
   *
   * 为什么除了类名还要存一份：Tailwind 的类名只能整体套用，
   * 遇到「宽度和内联色值都需要动态计算」的场景（如落地页的分布条），
   * 就得用 style 直接给值。没有这个字段时各处会各写一份十六进制，
   * 又是同一个漏改问题。
   */
  hex: string
}

export const SUBJECT_META: Record<string, SubjectMeta> = {
  ds: { name: '数据结构', bar: 'bg-[#3D6BE5]', soft: 'bg-[#3D6BE5]/12', text: 'text-[#3D6BE5]', hex: '#3D6BE5' },
  os: { name: '操作系统', bar: 'bg-[#7C5CF0]', soft: 'bg-[#7C5CF0]/12', text: 'text-[#7C5CF0]', hex: '#7C5CF0' },
  co: { name: '计算机组成原理', bar: 'bg-[#F0A020]', soft: 'bg-[#F0A020]/12', text: 'text-[#B8730A]', hex: '#F0A020' },
  cn: { name: '计算机网络', bar: 'bg-[#E0574F]', soft: 'bg-[#E0574F]/12', text: 'text-[#C0392B]', hex: '#E0574F' },
}

/** 取科目元信息；未知科目回落到品牌主色，避免出现无样式空条。 */
export function subjectMeta(code: string): SubjectMeta {
  return (
    SUBJECT_META[code] ?? {
      name: code,
      bar: 'bg-[var(--k-accent)]',
      soft: 'bg-[var(--k-accent-soft)]',
      text: 'text-[var(--k-accent-text)]',
      hex: 'var(--k-accent)',
    }
  )
}