import { marked } from 'marked'
import { escapeHtml } from './htmlEscape'
import { sanitizeContentHtml } from './htmlSanitize'
import { renderFormulaBlock, renderMathInHtml, renderTextWithMath } from './htmlMath'

/**
 * 完整 Markdown 渲染（marked + DOMPurify + KaTeX）。
 *
 * 只给知识正文这类确实需要完整 Markdown 的页面用。刷题页走 utils/stem.ts 的轻量渲染，
 * 不再把 marked 与 KaTeX 一起拉进首屏——实测那一个 chunk 是 328KB。
 *
 * 不变式仍然只有两条（与拆分前一致）：
 *   1. 来自内容的 HTML 一律先转义或消毒，再拼任何标签；
 *   2. KaTeX 输出不过消毒器——`trust: false` 已禁掉可执行入口，公式文本由 KaTeX 自己转义。
 */

/** Markdown → 消毒 → 公式。 */
export function renderMarkdown(source: string): string {
  if (!source) return ''
  const rendered = marked.parse(source, { async: false, breaks: true, gfm: true }) as string
  return renderMathInHtml(sanitizeContentHtml(rendered))
}

// 兼容旧调用点：这些工具原本都在这一个模块里，现在按职责拆成了三个文件，
// 依赖关系变成 htmlEscape ← htmlSanitize / htmlMath ← html（Markdown 编排）。
export { escapeHtml, renderFormulaBlock, renderMathInHtml, renderTextWithMath }
export { sanitizeContentHtml, sanitizeHtmlBlock } from './htmlSanitize'
