import DOMPurify from 'dompurify'
import katex from 'katex'
import { marked } from 'marked'

/**
 * 全站 HTML 渲染的唯一出口。
 *
 * 为什么要有这个文件：正文/题干/背诵卡片原本各自维护一份「marked → DOMPurify → KaTeX」，
 * 三份配置互不相同，导致同一类内容在不同页面被不同程度地放行（题干的 ADD_ATTR 里
 * 放行了 style、callout 的文本根本没转义、HTML 块干脆没过消毒器）。渲染安全依赖
 * 「每个调用点都记得消毒」，这种不变式迟早会漏。
 *
 * 现在的不变式只有两条：
 *   1. 来自 Markdown / 题库文本的内容，一律先 escape 或 sanitize，再拼任何 HTML；
 *   2. KaTeX 的输出不过消毒器 —— 因为它本身就是安全边界：`trust: false` 禁掉了
 *      \href / \url / \htmlClass 等可执行入口，公式文本由 KaTeX 自己转义，
 *      输出只含它生成的 class 与 style。反过来再消毒一次会把 KaTeX 的样式剥掉，
 *      公式会错版（这正是题干组件当年放行 style 的原因）。
 */

/** 正文消毒配置：不放行 class / id / style，避免内容影响页面结构与排版。 */
type SanitizeConfig = Parameters<typeof DOMPurify.sanitize>[1]

const CONTENT_CONFIG: SanitizeConfig = {
  FORBID_TAGS: ['button', 'embed', 'form', 'iframe', 'input', 'object', 'script', 'style'],
  FORBID_ATTR: ['class', 'id', 'style'],
  USE_PROFILES: { html: true },
}

/**
 * HTML 型知识块（含内嵌 SVG 图示）的消毒配置。
 *
 * 比正文宽一档，因为这类块确实带 <svg>：html-only 配置会把整张图剥空。
 * SVG 里能执行脚本的入口（<script>、on* 属性、javascript: 链接）由 DOMPurify 处理，
 * 不需要我们自己维护黑名单。
 */
const HTML_BLOCK_CONFIG: SanitizeConfig = {
  FORBID_TAGS: ['button', 'embed', 'form', 'iframe', 'input', 'object', 'script', 'style'],
  FORBID_ATTR: ['style', 'onerror', 'onload', 'onclick'],
  USE_PROFILES: { html: true, svg: true, svgFilters: true },
}

/** 公式渲染失败时的占位；带上已转义的原式，便于排查是哪个公式写错了。 */
const mathError = (formula: string) =>
  `<code class="k-math-error">公式错误: ${escapeHtml(formula)}</code>`

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

/** 消毒「已由我们自己生成的 HTML」；用于 Markdown 输出与知识块 HTML。 */
export function sanitizeContentHtml(html: string): string {
  // sanitize 的返回类型是 string | TrustedHTML，本项目的 v-html 需要 string。
  return String(DOMPurify.sanitize(html, CONTENT_CONFIG))
}

export function sanitizeHtmlBlock(html: string): string {
  return String(DOMPurify.sanitize(html, HTML_BLOCK_CONFIG))
}

/**
 * 把 $...$ / $$...$$ / \(...\) / \[...\] 替换成 KaTeX 输出。
 *
 * 输入必须是**已经转义或已经消毒**的 HTML —— 本函数只负责公式，不负责内容本身；
 * 若把未消毒的文本直接喂进来，等于绕过了消毒器（callout 以前就是这样）。
 */
export function renderMathInHtml(html: string): string {
  const mathPattern = /\$\$([\s\S]*?)\$\$|\\\[([\s\S]*?)\\\]|\$([^$\n]+?)\$|\\\(([\s\S]*?)\\\)/g

  return html.replace(
    mathPattern,
    (
      match: string,
      displayDollar?: string,
      displayBracket?: string,
      inlineDollar?: string,
      inlineParen?: string,
    ) => {
      const raw = displayDollar ?? displayBracket ?? inlineDollar ?? inlineParen ?? ''
      const displayMode = displayDollar !== undefined || displayBracket !== undefined
      const formula = extractFormulaText(raw)
      try {
        return katex.renderToString(formula, { displayMode, throwOnError: false, trust: false })
      } catch {
        return mathError(formula)
      }
    },
  )
}

/**
 * 公式里可能夹着 Markdown 生成的标签（例如换行被转成 <br>），
 * 直接交给 KaTeX 会因为尖括号报错，所以先取纯文本。
 */
function extractFormulaText(formulaHtml: string): string {
  const normalized = formulaHtml.replace(/<br\s*\/?>/gi, '\n')
  const container = document.createElement('div')
  container.innerHTML = normalized
  return (container.textContent ?? '').trim()
}

/** Markdown → 消毒 → 公式。知识正文与题干共用同一条链路。 */
export function renderMarkdown(source: string): string {
  if (!source) return ''
  const rendered = marked.parse(source, { async: false, breaks: true, gfm: true }) as string
  return renderMathInHtml(sanitizeContentHtml(rendered))
}

/**
 * 纯文本 + 公式（用于 callout 这类不解析 Markdown 的字段）。
 * 先转义再渲染公式：文本里的 `<` 属于内容，不能被当成标签。
 */
export function renderTextWithMath(text: string): string {
  if (!text) return ''
  return renderMathInHtml(escapeHtml(text))
}

/** 独立的公式块（formula 类型知识块）：公式本身就是完整内容，无需再解析 Markdown。 */
export function renderFormulaBlock(latex: string): string {
  try {
    return katex.renderToString(latex, { displayMode: true, throwOnError: false, trust: false })
  } catch {
    return mathError(latex)
  }
}
