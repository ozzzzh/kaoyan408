/**
 * 知识正文的类型与适配层。
 *
 * 后端把块内容以 JSON 字符串返回（数据库 content_json 列），
 * 这里负责解析成渲染组件需要的结构，使前端渲染逻辑与数据来源解耦。
 */

/** 段落：Markdown 源文本，内部可含 $...$ 行内公式。 */
export interface ParagraphBlock {
  id: string
  type: 'paragraph'
  text: string
}

export interface HtmlBlock {
  id: string
  type: 'html'
  html: string
}

export interface FormulaBlock {
  id: string
  type: 'formula'
  formula: string
  caption?: string
}

export interface CalloutBlock {
  id: string
  type: 'callout'
  title: string
  text: string
  tone?: 'blue' | 'orange'
}

export interface ImageBlock {
  id: string
  type: 'image'
  src: string
  alt: string
  caption?: string
}

/**
 * 动画块：渲染函数无法序列化，因此后端只存元信息，
 * 实际渲染函数由客户端从动画注册表按 animationId 取回。
 */
export interface AnimationMetaBlock {
  id: string
  type: 'animation'
  animationId: string | null
  ariaLabel?: string | null
}

export type KnowledgeBlock =
  | ParagraphBlock
  | HtmlBlock
  | FormulaBlock
  | CalloutBlock
  | ImageBlock
  | AnimationMetaBlock

export interface KnowledgeSubpoint {
  id: string
  title: string
  blocks: KnowledgeBlock[]
}

export interface KnowledgeArticleData {
  pointId: string
  subpoints: KnowledgeSubpoint[]
}

/** 后端 /knowledge/point/{pointId} 的响应形状。 */
export interface KnowledgePointResponse {
  pointId: string
  title: string | null
  subject: string | null
  blocks: Array<{
    blockId: string
    subpointId: string | null
    subpointTitle: string | null
    order: number
    type: string
    content: string
  }>
}

/**
 * 把后端按顺序返回的扁平块列表，还原成「subpoint → blocks」的层级。
 * 后端只保证全局顺序；同一 subpointId 的块归入同一小节。
 */
export function toArticle(response: KnowledgePointResponse): KnowledgeArticleData {
  const subpoints: KnowledgeSubpoint[] = []
  const indexById = new Map<string, KnowledgeSubpoint>()

  for (const raw of response.blocks) {
    const subpointId = raw.subpointId ?? `${response.pointId}__default`
    const subpointTitle = raw.subpointTitle ?? response.title ?? '正文'

    let subpoint = indexById.get(subpointId)
    if (!subpoint) {
      subpoint = { id: subpointId, title: subpointTitle, blocks: [] }
      indexById.set(subpointId, subpoint)
      subpoints.push(subpoint)
    }

    const block = parseBlock(raw.content, raw.blockId, raw.type)
    if (block) subpoint.blocks.push(block)
  }

  return { pointId: response.pointId, subpoints }
}

/** 解析单块内容；解析失败返回 null（不让一条坏数据阻断整页）。 */
function parseBlock(content: string, blockId: string, type: string): KnowledgeBlock | null {
  let parsed: Record<string, unknown>
  try {
    parsed = JSON.parse(content) as Record<string, unknown>
  } catch {
    // 兜底：把无法解析的内容当纯文本段落显示
    return { id: blockId, type: 'paragraph', text: content }
  }

  const resolvedType = (parsed.type as string) ?? type

  switch (resolvedType) {
    case 'paragraph':
      return { id: blockId, type: 'paragraph', text: String(parsed.text ?? '') }
    case 'html':
      return { id: blockId, type: 'html', html: String(parsed.html ?? '') }
    case 'formula':
      return {
        id: blockId,
        type: 'formula',
        formula: String(parsed.formula ?? ''),
        caption: parsed.caption ? String(parsed.caption) : undefined,
      }
    case 'callout':
      return {
        id: blockId,
        type: 'callout',
        title: String(parsed.title ?? ''),
        text: String(parsed.text ?? ''),
        tone: parsed.tone === 'blue' || parsed.tone === 'orange' ? parsed.tone : undefined,
      }
    case 'image':
      return {
        id: blockId,
        type: 'image',
        src: String(parsed.src ?? ''),
        alt: String(parsed.alt ?? ''),
        caption: parsed.caption ? String(parsed.caption) : undefined,
      }
    case 'animation':
      return {
        id: blockId,
        type: 'animation',
        animationId: parsed.animationId ? String(parsed.animationId) : null,
        ariaLabel: parsed.ariaLabel ? String(parsed.ariaLabel) : null,
      }
    default:
      return null
  }
}