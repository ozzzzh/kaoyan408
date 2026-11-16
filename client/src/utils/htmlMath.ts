import katex from 'katex'
import { escapeHtml } from './htmlEscape'

/**
 * 公式渲染（KaTeX）。**只依赖 katex**，不碰 marked / DOMPurify。
 *
 * 为什么要拆出来：原来这些函数和 marked 放在同一个模块里，于是刷题页（只渲染题干 +
 * 公式）也被迫把整条 Markdown 链路一起下载。拆分后刷题页可以只按需加载本模块，
 * 不含公式的题目连这一步都省了。
 *
 * 不变式（与 html.ts 一致）：输入必须是**已经转义或已经消毒**的 HTML；
 * KaTeX 的输出在消毒之后注入，本身不过消毒器（trust:false 已禁掉可执行入口）。
 */

/** 公式渲染失败时的占位；带上已转义的原式，便于排查是哪个公式写错了。 */
const mathError = (formula: string) => `<code class="k-math-error">公式错误: ${escapeHtml(formula)}</code>`


/**
 * 把 $...$ / $$...$$ / \(...\) / \[...\] 替换成 KaTeX 输出。
 * 输入必须已经转义或消毒，本函数只负责公式。
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

/** 纯文本 + 公式（callout 这类不解析 Markdown 的字段）：先转义再渲染公式。 */
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
